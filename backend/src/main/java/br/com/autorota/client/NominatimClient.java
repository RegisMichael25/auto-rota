package br.com.autorota.client;

import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.autorota.config.AppProperties;
import br.com.autorota.domain.CacheKeys;
import br.com.autorota.domain.CoordSource;
import br.com.autorota.domain.ExternalServiceUnavailableException;
import br.com.autorota.domain.GeocodeCache;
import br.com.autorota.repository.GeocodeCacheRepository;

/**
 * Geocodificação por endereço no Nominatim, restrita ao Brasil. Segue a política de uso: User-Agent identificado,
 * no máximo uma requisição por intervalo configurado (padrão 1 s, entre threads) e cache persistente, inclusive de
 * "não encontrado".
 * <p>
 * Nomes genéricos ("Rua C") se repetem em vários bairros e até em cidades vizinhas, então um resultado só é aceito
 * se trouxer o mesmo CEP ou se estiver na cidade e no bairro do ViaCEP (resultado sem bairro não contradiz). Tentativas,
 * em ordem: logradouro + número; só o logradouro (ponto da via); o CEP, aceito só com o CEP exato. A última cobre ruas
 * que o OpenStreetMap conhece por outro nome (loteamento renomeado: "Rua C" nos Correios, nome oficial no OSM).
 */
@Component
public class NominatimClient {

	private static final Logger log = LoggerFactory.getLogger(NominatimClient.class);

	private static final String SERVICE = "Nominatim";

	private static final String COUNTRY = "Brasil";

	private static final String COUNTRY_CODE = "br";

	/** Versão do critério de busca; mudar o critério exige mudar o prefixo para não reaproveitar resultados antigos. */
	static final String CACHE_PREFIX = "nominatim:v3|";

	private static final int MAX_RESULTS = 5;

	private static final List<String> CITY_FIELDS = List.of("city", "town", "village", "municipality");

	private static final List<String> NEIGHBORHOOD_FIELDS = List.of("suburb", "city_district", "quarter", "neighbourhood");

	private final RestClient http;

	private final GeocodeCacheRepository cache;

	private final RateLimiter rateLimiter;

	public NominatimClient(RestClient.Builder builder, AppProperties properties, GeocodeCacheRepository cache) {
		var nominatim = properties.external().nominatim();
		this.http = builder.clone()
			.baseUrl(nominatim.baseUrl())
			.defaultHeader(HttpHeaders.USER_AGENT, nominatim.userAgent())
			.build();
		this.cache = cache;
		this.rateLimiter = new RateLimiter(Duration.ofMillis(nominatim.minIntervalMs()));
	}

	/**
	 * @param neighborhood bairro do ViaCEP; em branco, o bairro não é conferido
	 * @param cep CEP de 8 dígitos (com ou sem hífen); em branco, não há busca nem conferência por CEP
	 * @throws ExternalServiceUnavailableException se o Nominatim falhar (a falha não vai para o cache)
	 */
	public Optional<Coordinates> geocode(String street, String number, String neighborhood, String city, String uf,
			String cep) {
		String query = buildQuery(street, number, city, uf);
		var expected = new Expected(city, neighborhood, digits(cep));
		String cacheText = CACHE_PREFIX + normalize(query) + "|" + fold(nullToEmpty(neighborhood)) + "|" + expected.cep();
		String key = CacheKeys.sha256(cacheText);

		var cached = cache.findById(key);
		if (cached.isPresent()) {
			return toCoordinates(cached.get());
		}

		Optional<Coordinates> coordinates = firstMatch(search(query), expected);
		if (coordinates.isEmpty() && !isBlank(street) && !isBlank(number)) {
			coordinates = firstMatch(search(buildQuery(street, null, city, uf)), expected);
		}
		if (coordinates.isEmpty() && !expected.cep().isEmpty()) {
			coordinates = firstWithCep(search(buildQuery(formatCep(expected.cep()), null, city, uf)), expected.cep());
		}
		save(new GeocodeCache(key, cacheText, CoordSource.NOMINATIM, coordinates.map(Coordinates::lat).orElse(null),
				coordinates.map(Coordinates::lng).orElse(null)));
		return coordinates;
	}

	private JsonNode search(String query) {
		rateLimiter.acquire();
		try {
			return http.get()
				.uri(uri -> uri.path("/search")
					.queryParam("q", query)
					.queryParam("countrycodes", COUNTRY_CODE)
					.queryParam("format", "jsonv2")
					.queryParam("addressdetails", 1)
					.queryParam("limit", MAX_RESULTS)
					.build())
				.retrieve()
				.body(JsonNode.class);
		}
		catch (RestClientException ex) {
			throw new ExternalServiceUnavailableException(SERVICE, ex);
		}
	}

	/** O que o resultado precisa confirmar. CEP só com dígitos; campos vazios não são conferidos. */
	record Expected(String city, String neighborhood, String cep) {
	}

	/** "Logradouro número, cidade, UF, Brasil", omitindo partes vazias (CEP geral de cidade não tem logradouro). */
	static String buildQuery(String street, String number, String city, String uf) {
		String streetPart = isBlank(street) ? null : (isBlank(number) ? street.trim() : street.trim() + " " + number.trim());
		return Stream.of(streetPart, city, uf, COUNTRY)
			.filter(part -> !isBlank(part))
			.map(String::trim)
			.collect(Collectors.joining(", "));
	}

	private static String normalize(String query) {
		return query.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}

	/**
	 * Primeiro resultado com o CEP esperado, ou na cidade e no bairro esperados (sem diferenciar maiúsculas nem
	 * acentos). Resultado sem bairro é aceito, pois não contradiz o ViaCEP.
	 */
	static Optional<Coordinates> firstMatch(JsonNode results, Expected expected) {
		return stream(results).filter(result -> accepts(result.path("address"), expected))
			.map(NominatimClient::parseCoordinates)
			.flatMap(Optional::stream)
			.findFirst();
	}

	/** Primeiro resultado com exatamente o CEP esperado. */
	static Optional<Coordinates> firstWithCep(JsonNode results, String cep) {
		return stream(results).filter(result -> hasCep(result.path("address"), cep))
			.map(NominatimClient::parseCoordinates)
			.flatMap(Optional::stream)
			.findFirst();
	}

	private static boolean accepts(JsonNode address, Expected expected) {
		if (hasCep(address, expected.cep())) {
			return true;
		}
		return (isBlank(expected.city()) || inCity(address, expected.city()))
				&& (isBlank(expected.neighborhood()) || inNeighborhood(address, expected.neighborhood()));
	}

	private static boolean hasCep(JsonNode address, String cep) {
		return !cep.isEmpty() && digits(address.path("postcode").asText("")).equals(cep);
	}

	private static boolean inCity(JsonNode address, String city) {
		String expected = fold(city);
		return CITY_FIELDS.stream().map(field -> address.path(field).asText("")).anyMatch(v -> fold(v).equals(expected));
	}

	/**
	 * Sem bairro no resultado, aceita. Com bairro, um nome contém o outro: o OSM às vezes junta bairros
	 * ("Sapiranga / Coité") ou detalha o loteamento.
	 */
	private static boolean inNeighborhood(JsonNode address, String neighborhood) {
		String expected = fold(neighborhood);
		var found = NEIGHBORHOOD_FIELDS.stream().map(field -> fold(address.path(field).asText(""))).filter(v -> !v.isEmpty()).toList();
		return found.isEmpty() || found.stream().anyMatch(v -> v.contains(expected) || expected.contains(v));
	}

	private static Stream<JsonNode> stream(JsonNode results) {
		return results == null || !results.isArray() ? Stream.empty() : StreamSupport.stream(results.spliterator(), false);
	}

	private static String digits(String value) {
		return value == null ? "" : value.replaceAll("\\D", "");
	}

	private static String formatCep(String digits) {
		return digits.length() == 8 ? digits.substring(0, 5) + "-" + digits.substring(5) : digits;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String fold(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.trim();
	}

	private static Optional<Coordinates> parseCoordinates(JsonNode result) {
		try {
			return Optional.of(new Coordinates(Double.parseDouble(result.path("lat").asText()),
					Double.parseDouble(result.path("lon").asText())));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

	private void save(GeocodeCache entry) {
		try {
			cache.save(entry);
		}
		catch (DataIntegrityViolationException ex) {
			log.debug("geocode_cache já contém {}", entry.getQueryText());
		}
	}

	private static Optional<Coordinates> toCoordinates(GeocodeCache entry) {
		return entry.isFound() ? Optional.of(new Coordinates(entry.getLat(), entry.getLng())) : Optional.empty();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
