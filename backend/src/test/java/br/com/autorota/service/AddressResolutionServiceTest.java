package br.com.autorota.service;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.autorota.PostgresIntegrationTest;
import br.com.autorota.client.BrasilApiClient;
import br.com.autorota.client.NominatimClient;
import br.com.autorota.client.ViaCepClient;
import br.com.autorota.config.AppProperties;
import br.com.autorota.config.AppProperties.GeocodingStrategy;
import br.com.autorota.domain.AddressNotGeocodedException;
import br.com.autorota.domain.CoordSource;
import br.com.autorota.domain.ExternalServiceUnavailableException;
import br.com.autorota.repository.CepCacheRepository;
import br.com.autorota.repository.GeocodeCacheRepository;

class AddressResolutionServiceTest extends PostgresIntegrationTest {

	private static final String VIACEP = "/viacep/ws/01310100/json/";

	private static final String BRASILAPI = "/brasilapi/api/cep/v2/01310100";

	private static final String NOMINATIM = "/nominatim/search";

	private static final String PAULISTA_1000 = """
			[{"lat": "-23.5649", "lon": "-46.6519", "address": {"city": "São Paulo"}}]
			""";

	private static final String BRASILAPI_COORDS = """
			{"location": {"coordinates": {"longitude": "-46.6361", "latitude": "-23.5475"}}}
			""";

	@Autowired
	private AddressResolutionService service;

	@Autowired
	private CepCacheRepository cepCache;

	@Autowired
	private GeocodeCacheRepository geocodeCache;

	@Autowired
	private ViaCepClient viaCep;

	@Autowired
	private BrasilApiClient brasilApi;

	@Autowired
	private NominatimClient nominatim;

	@Autowired
	private AppProperties properties;

	@BeforeEach
	void setUp() {
		cepCache.deleteAll();
		geocodeCache.deleteAll();
		WIREMOCK.stubFor(get(urlPathEqualTo(VIACEP)).willReturn(okJson("""
				{"cep": "01310-100", "logradouro": "Avenida Paulista", "complemento": "de 612 a 1510 - lado par",
				 "bairro": "Bela Vista", "localidade": "São Paulo", "uf": "SP"}
				""")));
	}

	@Test
	void defaultStrategyIsNominatimFirst() {
		assertThat(properties.geocoding().strategy()).isEqualTo(GeocodingStrategy.NOMINATIM_FIRST);
	}

	@Test
	void usesBrasilApiCoordinatesWithoutCallingNominatim() {
		WIREMOCK.stubFor(get(urlPathEqualTo(BRASILAPI)).willReturn(okJson(BRASILAPI_COORDS)));

		var resolved = brasilApiFirst().resolve("01310-100", " 1000 ");

		assertThat(resolved.coordSource()).isEqualTo(CoordSource.BRASILAPI);
		assertThat(resolved.address().getNumber()).isEqualTo("1000");
		assertThat(resolved.address().getStreet()).isEqualTo("Avenida Paulista");
		assertThat(resolved.address().getLat()).isEqualTo(-23.5475);
		WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo(NOMINATIM)));
	}

	@Test
	void fallsBackToNominatimWhenBrasilApiHasNoCoordinates() {
		WIREMOCK.stubFor(get(urlPathEqualTo(BRASILAPI)).willReturn(okJson("{\"location\": {\"coordinates\": {}}}")));
		WIREMOCK.stubFor(get(urlPathEqualTo(NOMINATIM)).willReturn(okJson(PAULISTA_1000)));

		var resolved = brasilApiFirst().resolve("01310100", "1000");

		assertThat(resolved.coordSource()).isEqualTo(CoordSource.NOMINATIM);
		assertThat(resolved.address().getLng()).isEqualTo(-46.6519);
	}

	@Test
	void failsWhenNoProviderHasCoordinates() {
		WIREMOCK.stubFor(get(urlPathEqualTo(BRASILAPI)).willReturn(okJson("{}")));
		WIREMOCK.stubFor(get(urlPathEqualTo(NOMINATIM)).willReturn(okJson("[]")));

		assertThatThrownBy(() -> brasilApiFirst().resolve("01310100", "1000")).isInstanceOf(AddressNotGeocodedException.class);
	}

	@Test
	void repeatedResolutionMakesNoExternalRequest() {
		WIREMOCK.stubFor(get(urlPathEqualTo(BRASILAPI)).willReturn(okJson("{}")));
		WIREMOCK.stubFor(get(urlPathEqualTo(NOMINATIM)).willReturn(okJson(PAULISTA_1000)));
		brasilApiFirst().resolve("01310100", "1000");
		WIREMOCK.resetRequests();

		var again = brasilApiFirst().resolve("01310-100", "1000");

		assertThat(again.coordSource()).isEqualTo(CoordSource.NOMINATIM);
		WIREMOCK.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void numberIsRequired() {
		assertThatThrownBy(() -> service.resolve("01310100", " ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nominatimFirstUsesNominatimWithoutCallingBrasilApi() {
		WIREMOCK.stubFor(get(urlPathEqualTo(NOMINATIM)).willReturn(okJson(PAULISTA_1000)));

		var resolved = service.resolve("01310100", "1000");

		assertThat(resolved.coordSource()).isEqualTo(CoordSource.NOMINATIM);
		assertThat(resolved.address().getLat()).isEqualTo(-23.5649);
		WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo(BRASILAPI)));
	}

	@Test
	void nominatimFirstFallsBackToBrasilApiWhenNotFound() {
		WIREMOCK.stubFor(get(urlPathEqualTo(NOMINATIM)).willReturn(okJson("[]")));
		WIREMOCK.stubFor(get(urlPathEqualTo(BRASILAPI)).willReturn(okJson(BRASILAPI_COORDS)));

		var resolved = service.resolve("01310100", "1000");

		assertThat(resolved.coordSource()).isEqualTo(CoordSource.BRASILAPI);
	}

	@Test
	void nominatimFirstFallsBackToBrasilApiWhenNominatimIsDown() {
		WIREMOCK.stubFor(get(urlPathEqualTo(NOMINATIM)).willReturn(serviceUnavailable()));
		WIREMOCK.stubFor(get(urlPathEqualTo(BRASILAPI)).willReturn(okJson(BRASILAPI_COORDS)));

		var resolved = service.resolve("01310100", "1000");

		assertThat(resolved.coordSource()).isEqualTo(CoordSource.BRASILAPI);
	}

	@Test
	void nominatimFirstReportsOutageWhenBrasilApiHasNoCoordinates() {
		WIREMOCK.stubFor(get(urlPathEqualTo(NOMINATIM)).willReturn(serviceUnavailable()));
		WIREMOCK.stubFor(get(urlPathEqualTo(BRASILAPI)).willReturn(okJson("{}")));

		assertThatThrownBy(() -> service.resolve("01310100", "1000"))
			.isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	void nominatimFirstUsesBrasilApiFirstForCityWideCep() {
		WIREMOCK.stubFor(get(urlPathEqualTo("/viacep/ws/35460000/json/")).willReturn(okJson("""
				{"cep": "35460-000", "logradouro": "", "bairro": "", "localidade": "Brumadinho", "uf": "MG"}
				""")));
		WIREMOCK.stubFor(get(urlPathEqualTo("/brasilapi/api/cep/v2/35460000")).willReturn(okJson(BRASILAPI_COORDS)));

		var resolved = service.resolve("35460000", "10");

		assertThat(resolved.coordSource()).isEqualTo(CoordSource.BRASILAPI);
		WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo(NOMINATIM)));
	}

	private AddressResolutionService brasilApiFirst() {
		var custom = new AppProperties(properties.external(),
				new AppProperties.Geocoding(GeocodingStrategy.BRASILAPI_FIRST), properties.optimizer(), properties.fuel(),
				properties.cors());
		return new AddressResolutionService(viaCep, brasilApi, nominatim, custom);
	}

}
