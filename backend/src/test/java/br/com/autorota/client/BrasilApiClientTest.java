package br.com.autorota.client;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.autorota.PostgresIntegrationTest;
import br.com.autorota.repository.GeocodeCacheRepository;

class BrasilApiClientTest extends PostgresIntegrationTest {

	private static final String PATH = "/brasilapi/api/cep/v2/01001000";

	@Autowired
	private BrasilApiClient client;

	@Autowired
	private GeocodeCacheRepository cache;

	@BeforeEach
	void clearCache() {
		cache.deleteAll();
	}

	@Test
	void returnsCoordinatesAndCachesThem() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("""
				{"cep": "01001000", "state": "SP", "city": "São Paulo", "service": "open-cep",
				 "location": {"type": "Point", "coordinates": {"longitude": "-46.6339", "latitude": "-23.5503"}}}
				""")));

		var first = client.coordinates("01001-000");
		var second = client.coordinates("01001000");

		assertThat(first).contains(new Coordinates(-23.5503, -46.6339));
		assertThat(second).isEqualTo(first);
		WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void missingCoordinatesReturnEmptyAndAreCached() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("""
				{"cep": "01001000", "state": "SP", "city": "São Paulo", "location": {"type": "Point", "coordinates": {}}}
				""")));

		assertThat(client.coordinates("01001000")).isEmpty();
		assertThat(client.coordinates("01001000")).isEmpty();

		WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void unknownCepReturnsEmpty() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(notFound()));

		assertThat(client.coordinates("01001000")).isEmpty();
	}

	@Test
	void unavailableServiceReturnsEmptyWithoutCaching() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(serviceUnavailable()));

		assertThat(client.coordinates("01001000")).isEmpty();
		assertThat(client.coordinates("01001000")).isEmpty();

		WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
		assertThat(cache.count()).isZero();
	}

}
