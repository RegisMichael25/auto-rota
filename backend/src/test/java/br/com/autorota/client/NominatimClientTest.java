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
import java.util.Optional;

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

	private static final String RUA_C_PASSARE = """
			[{"lat": "-3.8008", "lon": "-38.5394", "address": {"road": "Rua C", "suburb": "Passaré", "city": "Fortaleza", "postcode": "60861-000"}}]
			""";

	/** Resultado de CEP no Nominatim: sem cidade, com bairro e CEP. */
	private static final String CEP_LAGUNA_PARK = """
			[{"lat": "-3.8223", "lon": "-38.4680", "address": {"suburb": "Lagoa Redonda", "postcode": "60831-227"}}]
			""";

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

		var result = geocode(client, "Avenida Paulista", "1000", "São Paulo", "SP");

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

		var result = geocode(client, FARIA_LIMA, "1500", "São Paulo", "SP");

		assertThat(result).contains(new Coordinates(-23.5868, -46.6819));
		WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void cityMatchIgnoresCaseAndAccents() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("""
				[{"lat": "-25.4284", "lon": "-49.2733", "address": {"town": "SAO JOSE DOS PINHAIS"}}]
				""")));

		assertThat(geocode(client, "Rua XV", "10", "São José dos Pinhais", "PR")).isPresent();
	}

	@Test
	void notFoundInCityAfterRetryIsCachedAsNotFound() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(GUARULHOS)));

		assertThat(geocode(client, FARIA_LIMA, "1500", "São Paulo", "SP")).isEmpty();
		assertThat(geocode(client, FARIA_LIMA, "1500", "São Paulo", "SP")).isEmpty();

		WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
		assertThat(cache.findAll()).singleElement()
			.satisfies(entry -> assertThat(entry.getQueryText()).startsWith(NominatimClient.CACHE_PREFIX));
	}

	@Test
	void repeatedQueryHitsCache() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(FOUND)));

		geocode(client, "Avenida Paulista", "1000", "São Paulo", "SP");
		var second = geocode(client, "avenida  paulista", "1000", "são paulo", "sp");

		assertThat(second).isPresent();
		WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void notFoundIsCached() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("[]")));

		assertThat(geocode(client, "Rua Inexistente", "1", "Lugar Nenhum", "SP")).isEmpty();
		assertThat(geocode(client, "Rua Inexistente", "1", "Lugar Nenhum", "SP")).isEmpty();

		// com número + sem número; a segunda chamada vem do cache
		WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void unavailableServiceThrowsDomainError() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(serviceUnavailable()));

		assertThatThrownBy(() -> geocode(client, "Avenida Paulista", "1000", "São Paulo", "SP"))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		assertThat(cache.count()).isZero();
	}

	@Test
	void respectsMinimumIntervalBetweenRequests() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(FOUND)));
		var limited = clientWithInterval(Duration.ofMillis(400));

		geocode(limited, "Rua A", "1", "São Paulo", "SP");
		long start = System.nanoTime();
		geocode(limited, "Rua B", "2", "São Paulo", "SP");
		long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

		assertThat(elapsedMs).isGreaterThanOrEqualTo(380);
		WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void buildsQueryWithoutStreetForCityWideCep() {
		assertThat(NominatimClient.buildQuery(null, "10", "Cidade", "MG")).isEqualTo("Cidade, MG, Brasil");
	}

	@Test
	void rejectsHomonymousStreetInAnotherNeighborhoodAndFallsBackToCep() {
		// "Rua C" existe em dezenas de bairros de Fortaleza; no OSM, a do Laguna Park se chama Rua Santa Isabel.
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("q", equalTo("Rua C 576, Fortaleza, CE, Brasil"))
			.willReturn(okJson(RUA_C_PASSARE)));
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("q", equalTo("Rua C, Fortaleza, CE, Brasil"))
			.willReturn(okJson(RUA_C_PASSARE)));
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("q", equalTo("60831-227, Fortaleza, CE, Brasil"))
			.willReturn(okJson(CEP_LAGUNA_PARK)));

		var result = client.geocode("Rua C", "576", "Lagoa Redonda", "Fortaleza", "CE", "60831-227");

		assertThat(result).contains(new Coordinates(-3.8223, -38.4680));
		WIREMOCK.verify(3, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void acceptsResultInExpectedNeighborhoodIgnoringAccentsAndJoinedNames() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("""
				[{"lat": "-3.8008", "lon": "-38.5394", "address": {"city": "Fortaleza", "suburb": "Passaré"}},
				 {"lat": "-3.7922", "lon": "-38.4596", "address": {"city": "Fortaleza", "suburb": "Sapiranga / Coité"}}]
				""")));

		var result = client.geocode("Rua C", "10", "SAPIRANGA", "Fortaleza", "CE", "60833000");

		assertThat(result).contains(new Coordinates(-3.7922, -38.4596));
		WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void acceptsResultWithSameCepEvenWithoutCityOrNeighborhood() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(CEP_LAGUNA_PARK)));

		assertThat(client.geocode("Rua C", "576", "Lagoa Redonda", "Fortaleza", "CE", "60831227"))
			.contains(new Coordinates(-3.8223, -38.4680));
	}

	@Test
	void cepSearchOnlyAcceptsExactCep() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(RUA_C_PASSARE)));
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("q", equalTo("60831-227, Fortaleza, CE, Brasil"))
			.willReturn(okJson("""
					[{"lat": "-3.8300", "lon": "-38.4600", "address": {"suburb": "Lagoa Redonda", "postcode": "60831-496"}}]
					""")));

		assertThat(client.geocode("Rua C", "576", "Lagoa Redonda", "Fortaleza", "CE", "60831-227")).isEmpty();
		WIREMOCK.verify(3, getRequestedFor(urlPathEqualTo(PATH)));
	}

	/** Sem bairro e sem CEP: só a cidade é conferida e não há busca por CEP. */
	private static Optional<Coordinates> geocode(NominatimClient client, String street, String number,
			String city, String uf) {
		return client.geocode(street, number, null, city, uf, null);
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
