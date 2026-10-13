package br.com.autorota.service;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.autorota.PostgresIntegrationTest;
import br.com.autorota.domain.AddressNotGeocodedException;
import br.com.autorota.domain.CoordSource;
import br.com.autorota.repository.CepCacheRepository;
import br.com.autorota.repository.GeocodeCacheRepository;

class AddressResolutionServiceTest extends PostgresIntegrationTest {

	private static final String VIACEP = "/viacep/ws/01310100/json/";

	private static final String BRASILAPI = "/brasilapi/api/cep/v2/01310100";

	private static final String NOMINATIM = "/nominatim/search";

	@Autowired
	private AddressResolutionService service;

	@Autowired
	private CepCacheRepository cepCache;

	@Autowired
	private GeocodeCacheRepository geocodeCache;

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
	void usesBrasilApiCoordinatesWithoutCallingNominatim() {
		WIREMOCK.stubFor(get(urlPathEqualTo(BRASILAPI)).willReturn(okJson("""
				{"location": {"coordinates": {"longitude": "-46.6544", "latitude": "-23.5632"}}}
				""")));

		var resolved = service.resolve("01310-100", " 1000 ");

		assertThat(resolved.coordSource()).isEqualTo(CoordSource.BRASILAPI);
		assertThat(resolved.address().getNumber()).isEqualTo("1000");
		assertThat(resolved.address().getStreet()).isEqualTo("Avenida Paulista");
		assertThat(resolved.address().getLat()).isEqualTo(-23.5632);
		WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo(NOMINATIM)));
	}

	@Test
	void fallsBackToNominatimWhenBrasilApiHasNoCoordinates() {
		WIREMOCK.stubFor(get(urlPathEqualTo(BRASILAPI)).willReturn(okJson("{\"location\": {\"coordinates\": {}}}")));
		WIREMOCK.stubFor(get(urlPathEqualTo(NOMINATIM)).willReturn(okJson("[{\"lat\": \"-23.5614\", \"lon\": \"-46.6559\"}]")));

		var resolved = service.resolve("01310100", "1000");

		assertThat(resolved.coordSource()).isEqualTo(CoordSource.NOMINATIM);
		assertThat(resolved.address().getLng()).isEqualTo(-46.6559);
	}

	@Test
	void failsWhenNoProviderHasCoordinates() {
		WIREMOCK.stubFor(get(urlPathEqualTo(BRASILAPI)).willReturn(okJson("{}")));
		WIREMOCK.stubFor(get(urlPathEqualTo(NOMINATIM)).willReturn(okJson("[]")));

		assertThatThrownBy(() -> service.resolve("01310100", "1000")).isInstanceOf(AddressNotGeocodedException.class);
	}

	@Test
	void repeatedResolutionMakesNoExternalRequest() {
		WIREMOCK.stubFor(get(urlPathEqualTo(BRASILAPI)).willReturn(okJson("{}")));
		WIREMOCK.stubFor(get(urlPathEqualTo(NOMINATIM)).willReturn(okJson("[{\"lat\": \"-23.5614\", \"lon\": \"-46.6559\"}]")));
		service.resolve("01310100", "1000");
		WIREMOCK.resetRequests();

		var again = service.resolve("01310-100", "1000");

		assertThat(again.coordSource()).isEqualTo(CoordSource.NOMINATIM);
		WIREMOCK.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void numberIsRequired() {
		assertThatThrownBy(() -> service.resolve("01310100", " ")).isInstanceOf(IllegalArgumentException.class);
	}

}
