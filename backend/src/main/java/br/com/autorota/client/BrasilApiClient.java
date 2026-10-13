package br.com.autorota.client;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.autorota.config.AppProperties;
import br.com.autorota.domain.CacheKeys;
import br.com.autorota.domain.Cep;
import br.com.autorota.domain.CoordSource;
import br.com.autorota.domain.GeocodeCache;
import br.com.autorota.repository.GeocodeCacheRepository;

/**
 * Coordenadas do CEP pela BrasilAPI (v2). Nunca lança erro: sem coordenadas, CEP desconhecido ou falha do serviço
 * resultam em vazio, e o chamador recorre ao Nominatim. Só respostas válidas vão para o cache.
 */
@Component
public class BrasilApiClient {

	private static final Logger log = LoggerFactory.getLogger(BrasilApiClient.class);

	private final RestClient http;

	private final GeocodeCacheRepository cache;

	public BrasilApiClient(RestClient.Builder builder, AppProperties properties, GeocodeCacheRepository cache) {
		this.http = builder.clone().baseUrl(properties.external().brasilapi().baseUrl()).build();
		this.cache = cache;
	}

	public Optional<Coordinates> coordinates(String rawCep) {
		String cep = Cep.normalize(rawCep);
		String query = "brasilapi|" + cep;
		String key = CacheKeys.sha256(query);

		var cached = cache.findById(key);
		if (cached.isPresent()) {
			return toCoordinates(cached.get());
		}

		JsonNode body;
		try {
			body = http.get().uri("/{cep}", cep).retrieve().body(JsonNode.class);
		}
		catch (HttpClientErrorException.NotFound ex) {
			body = null;
		}
		catch (RestClientException ex) {
			log.warn("BrasilAPI indisponível para o CEP {}: {}", cep, ex.getMessage());
			return Optional.empty();
		}

		Optional<Coordinates> coordinates = parse(body);
		save(new GeocodeCache(key, query, CoordSource.BRASILAPI, coordinates.map(Coordinates::lat).orElse(null),
				coordinates.map(Coordinates::lng).orElse(null)));
		return coordinates;
	}

	/** {@code location.coordinates} pode vir ausente, vazio ou com latitude/longitude em texto. */
	private static Optional<Coordinates> parse(JsonNode body) {
		if (body == null) {
			return Optional.empty();
		}
		JsonNode coords = body.path("location").path("coordinates");
		Double lat = parseDouble(coords.path("latitude"));
		Double lng = parseDouble(coords.path("longitude"));
		if (lat == null || lng == null || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
			return Optional.empty();
		}
		return Optional.of(new Coordinates(lat, lng));
	}

	private static Double parseDouble(JsonNode node) {
		if (node.isNumber()) {
			return node.doubleValue();
		}
		String text = node.asText("").trim();
		if (text.isEmpty()) {
			return null;
		}
		try {
			return Double.valueOf(text);
		}
		catch (NumberFormatException ex) {
			return null;
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

}
