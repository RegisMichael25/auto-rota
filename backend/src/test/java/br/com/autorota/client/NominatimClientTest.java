package br.com.autorota.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

import br.com.autorota.PostgresIntegrationTest;
import br.com.autorota.config.AppProperties;
import br.com.autorota.domain.ExternalServiceUnavailableException;
import br.com.autorota.repository.GeocodeCacheRepository;

class NominatimClientTest extends PostgresIntegrationTest {

	private static final String PATH = "/nominatim/search";

	private static final String FOUND = """
			[{"lat": "-23.5614", "lon": "-46.6559", "address": {"road": "Avenida Paulista", "city": "São Paulo"}}]
			""";

	private static final String GUARULHOS = """
			[{"lat": "-23.4420", "lon": "-46.5214", "address": {"road": "Avenida Brigadeiro Faria Lima", "city": "Guarulhos"}}]
			""";

	private static final String FARIA_LIMA_STREET = """
			[{"lat": "-23.4421", "lon": "-46.5215", "address": {"city": "Guarulhos"}},
			 {"lat": "-23.5868", "lon": "-46.6819", "address": {"road": "Avenida Brigadeiro Faria Lima", "city": "São Paulo"}}]
			""";

	private static final String FARIA_LIMA = "Avenida Brigadeiro Faria Lima";

	@Autowired
	private NominatimClient client;

	@Autowired
	private GeocodeCacheRepository cache;

	@Autowired
	private AppProperties properties;

	@Autowired
	private RestClient.Builder restClientBuilder;

	@BeforeEach
	void clearCache() {
		cache.deleteAll();
	}

	@Test
	void geocodesInBrazilWithIdentifiedUserAgent() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(FOUND)));

		var result = client.geocode("Avenida Paulista", "1000", "São Paulo", "SP");

		assertThat(result).contains(new Coordinates(-23.5614, -46.6559));
		WIREMOCK.verify(getRequestedFor(urlPathEqualTo(PATH))
			.withQueryParam("q", equalTo("Avenida Paulista 1000, São Paulo, SP, Brasil"))
			.withQueryParam("countrycodes", equalTo("br"))
			.withQueryParam("format", equalTo("jsonv2"))
			.withQueryParam("addressdetails", equalTo("1"))
			.withQueryParam("limit", equalTo("5"))
			.withHeader("User-Agent", equalTo("auto-rota-poc/test")));
	}

	@Test
	void ignoresHomonymousStreetInAnotherCityAndFallsBackToStreetLevel() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("q", equalTo(FARIA_LIMA + " 1500, São Paulo, SP, Brasil"))
			.willReturn(okJson(GUARULHOS)));
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("q", equalTo(FARIA_LIMA + ", São Paulo, SP, Brasil"))
			.willReturn(okJson(FARIA_LIMA_STREET)));

		var result = client.geocode(FARIA_LIMA, "1500", "São Paulo", "SP");

		assertThat(result).contains(new Coordinates(-23.5868, -46.6819));
		WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void cityMatchIgnoresCaseAndAccents() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("""
				[{"lat": "-25.4284", "lon": "-49.2733", "address": {"town": "SAO JOSE DOS PINHAIS"}}]
				""")));

		assertThat(client.geocode("Rua XV", "10", "São José dos Pinhais", "PR")).isPresent();
	}

	@Test
	void notFoundInCityAfterRetryIsCachedAsNotFound() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(GUARULHOS)));

		assertThat(client.geocode(FARIA_LIMA, "1500", "São Paulo", "SP")).isEmpty();
		assertThat(client.geocode(FARIA_LIMA, "1500", "São Paulo", "SP")).isEmpty();

		WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
		assertThat(cache.findAll()).singleElement()
			.satisfies(entry -> assertThat(entry.getQueryText()).startsWith(NominatimClient.CACHE_PREFIX));
	}

	@Test
	void repeatedQueryHitsCache() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(FOUND)));

		client.geocode("Avenida Paulista", "1000", "São Paulo", "SP");
		var second = client.geocode("avenida  paulista", "1000", "são paulo", "sp");

		assertThat(second).isPresent();
		WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void notFoundIsCached() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("[]")));

		assertThat(client.geocode("Rua Inexistente", "1", "Lugar Nenhum", "SP")).isEmpty();
		assertThat(client.geocode("Rua Inexistente", "1", "Lugar Nenhum", "SP")).isEmpty();

		// com número + sem número; a segunda chamada vem do cache
		WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void unavailableServiceThrowsDomainError() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(serviceUnavailable()));

		assertThatThrownBy(() -> client.geocode("Avenida Paulista", "1000", "São Paulo", "SP"))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		assertThat(cache.count()).isZero();
	}

	@Test
	void respectsMinimumIntervalBetweenRequests() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(FOUND)));
		var limited = clientWithInterval(Duration.ofMillis(400));

		limited.geocode("Rua A", "1", "São Paulo", "SP");
		long start = System.nanoTime();
		limited.geocode("Rua B", "2", "São Paulo", "SP");
		long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

		assertThat(elapsedMs).isGreaterThanOrEqualTo(380);
		WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void buildsQueryWithoutStreetForCityWideCep() {
		assertThat(NominatimClient.buildQuery(null, "10", "Cidade", "MG")).isEqualTo("Cidade, MG, Brasil");
	}

	private NominatimClient clientWithInterval(Duration interval) {
		var external = properties.external();
		var nominatim = new AppProperties.Nominatim(external.nominatim().baseUrl(), external.nominatim().userAgent(),
				interval.toMillis());
		var custom = new AppProperties(
				new AppProperties.External(external.viacep(), external.brasilapi(), nominatim, external.osrm()),
				properties.geocoding(), properties.optimizer(), properties.fuel(), properties.cors());
		return new NominatimClient(restClientBuilder, custom, cache);
	}

}
