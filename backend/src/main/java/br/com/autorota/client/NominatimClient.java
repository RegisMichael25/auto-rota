package br.com.autorota.client;

import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * Só aceita resultados na cidade informada (ruas homônimas em cidades vizinhas são comuns). Se nenhum resultado com o
 * número estiver na cidade, repete a busca sem o número e usa o ponto da via.
 */
@Component
public class NominatimClient {

	private static final Logger log = LoggerFactory.getLogger(NominatimClient.class);

	private static final String SERVICE = "Nominatim";

	private static final String COUNTRY = "Brasil";

	private static final String COUNTRY_CODE = "br";

	/** Versão do critério de busca; mudar o critério exige mudar o prefixo para não reaproveitar resultados antigos. */
	static final String CACHE_PREFIX = "nominatim:v2|";

	private static final int MAX_RESULTS = 5;

	private static final List<String> CITY_FIELDS = List.of("city", "town", "village", "municipality");

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
	 * @throws ExternalServiceUnavailableException se o Nominatim falhar (a falha não vai para o cache)
	 */
	public Optional<Coordinates> geocode(String street, String number, String city, String uf) {
		String query = buildQuery(street, number, city, uf);
		String cacheText = CACHE_PREFIX + normalize(query);
		String key = CacheKeys.sha256(cacheText);

		var cached = cache.findById(key);
		if (cached.isPresent()) {
			return toCoordinates(cached.get());
		}

		Optional<Coordinates> coordinates = search(query, city);
		if (coordinates.isEmpty() && !isBlank(street) && !isBlank(number)) {
			coordinates = search(buildQuery(street, null, city, uf), city);
		}
		save(new GeocodeCache(key, cacheText, CoordSource.NOMINATIM, coordinates.map(Coordinates::lat).orElse(null),
				coordinates.map(Coordinates::lng).orElse(null)));
		return coordinates;
	}

	private Optional<Coordinates> search(String query, String city) {
		rateLimiter.acquire();
		JsonNode results;
		try {
			results = http.get()
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
		return firstInCity(results, city);
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

	/** Primeiro resultado cuja cidade coincide com a informada (sem diferenciar maiúsculas nem acentos). */
	static Optional<Coordinates> firstInCity(JsonNode results, String city) {
		if (results == null || !results.isArray()) {
			return Optional.empty();
		}
		for (JsonNode result : results) {
			if (isBlank(city) || inCity(result.path("address"), city)) {
				var coordinates = parseCoordinates(result);
				if (coordinates.isPresent()) {
					return coordinates;
				}
			}
		}
		return Optional.empty();
	}

	private static boolean inCity(JsonNode address, String city) {
		String expected = fold(city);
		return CITY_FIELDS.stream().map(field -> address.path(field).asText("")).anyMatch(v -> fold(v).equals(expected));
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
