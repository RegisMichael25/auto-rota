package br.com.autorota.client;

import java.time.Duration;
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
 */
@Component
public class NominatimClient {

	private static final Logger log = LoggerFactory.getLogger(NominatimClient.class);

	private static final String SERVICE = "Nominatim";

	private static final String COUNTRY = "Brasil";

	private static final String COUNTRY_CODE = "br";

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
		String cacheText = "nominatim|" + normalize(query);
		String key = CacheKeys.sha256(cacheText);

		var cached = cache.findById(key);
		if (cached.isPresent()) {
			return toCoordinates(cached.get());
		}

		rateLimiter.acquire();
		JsonNode results;
		try {
			results = http.get()
				.uri(uri -> uri.path("/search")
					.queryParam("q", query)
					.queryParam("countrycodes", COUNTRY_CODE)
					.queryParam("format", "jsonv2")
					.queryParam("limit", 1)
					.build())
				.retrieve()
				.body(JsonNode.class);
		}
		catch (RestClientException ex) {
			throw new ExternalServiceUnavailableException(SERVICE, ex);
		}

		Optional<Coordinates> coordinates = parse(results);
		save(new GeocodeCache(key, cacheText, CoordSource.NOMINATIM, coordinates.map(Coordinates::lat).orElse(null),
				coordinates.map(Coordinates::lng).orElse(null)));
		return coordinates;
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

	private static Optional<Coordinates> parse(JsonNode results) {
		if (results == null || !results.isArray() || results.isEmpty()) {
			return Optional.empty();
		}
		JsonNode first = results.get(0);
		try {
			return Optional.of(new Coordinates(Double.parseDouble(first.path("lat").asText()),
					Double.parseDouble(first.path("lon").asText())));
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
