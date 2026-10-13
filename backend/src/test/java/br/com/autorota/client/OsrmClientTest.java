package br.com.autorota.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.tomakehurst.wiremock.http.Fault;

import br.com.autorota.PostgresIntegrationTest;
import br.com.autorota.domain.ExternalServiceUnavailableException;
import br.com.autorota.domain.RouteNotFoundException;

class OsrmClientTest extends PostgresIntegrationTest {

	private static final Coordinates SE = new Coordinates(-23.5505, -46.6333);

	private static final Coordinates PAULISTA = new Coordinates(-23.5649, -46.6519);

	private static final Coordinates CONSOLACAO = new Coordinates(-23.5614, -46.656);

	private static final String TABLE = "/osrm/table/v1/driving/.*";

	private static final String ROUTE = "/osrm/route/v1/driving/.*";

	@Autowired
	private OsrmClient client;

	@Test
	void tableReturnsDistanceAndDurationMatrices() {
		WIREMOCK.stubFor(get(urlPathMatching(TABLE)).willReturn(okJson("""
				{"code": "Ok",
				 "distances": [[0, 3322.9, 3757.4], [3485.2, 0, 2261.8], [4958, 2663.6, 0]],
				 "durations": [[0, 404.7, 450.1], [420.3, 0, 300.2], [600, 320.5, 0]]}
				""")));

		var matrix = client.table(SE, List.of(PAULISTA, CONSOLACAO));

		assertThat(matrix.size()).isEqualTo(3);
		assertThat(matrix.distancesM()[0][1]).isEqualTo(3322.9);
		assertThat(matrix.distancesM()[2][0]).isEqualTo(4958);
		assertThat(matrix.durationsS()[1][2]).isEqualTo(300.2);
		WIREMOCK.verify(getRequestedFor(urlEqualTo("/osrm/table/v1/driving/"
				+ "-46.633300,-23.550500;-46.651900,-23.564900;-46.656000,-23.561400"
				+ "?annotations=distance,duration&radiuses=1000;1000;1000")));
	}

	@Test
	void tableWithUnreachablePairIsRouteNotFound() {
		WIREMOCK.stubFor(get(urlPathMatching(TABLE)).willReturn(okJson("""
				{"code": "Ok", "distances": [[0, null], [10, 0]], "durations": [[0, null], [5, 0]]}
				""")));

		assertThatThrownBy(() -> client.table(SE, List.of(PAULISTA))).isInstanceOf(RouteNotFoundException.class)
			.hasMessageContaining("origem")
			.hasMessageContaining("destino 1");
	}

	@Test
	void pointWithoutNearbyRoadIsRouteNotFound() {
		WIREMOCK.stubFor(get(urlPathMatching(TABLE)).willReturn(aResponse().withStatus(400)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"code\": \"NoSegment\", \"message\": \"Could not find a matching segment for coordinate 1\"}")));

		assertThatThrownBy(() -> client.table(SE, List.of(new Coordinates(-10, -30))))
			.isInstanceOf(RouteNotFoundException.class)
			.hasMessageContaining("1000 m");
	}

	@Test
	void invalidQueryIsServiceError() {
		WIREMOCK.stubFor(get(urlPathMatching(TABLE)).willReturn(aResponse().withStatus(400)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"code\": \"InvalidQuery\", \"message\": \"Query string malformed\"}")));

		assertThatThrownBy(() -> client.table(SE, List.of(PAULISTA)))
			.isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	void serverErrorIsServiceUnavailable() {
		WIREMOCK.stubFor(get(urlPathMatching(TABLE)).willReturn(serverError()));

		assertThatThrownBy(() -> client.table(SE, List.of(PAULISTA)))
			.isInstanceOf(ExternalServiceUnavailableException.class)
			.hasMessageContaining("OSRM");
	}

	@Test
	void connectionFailureIsServiceUnavailable() {
		WIREMOCK.stubFor(get(urlPathMatching(TABLE)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		assertThatThrownBy(() -> client.table(SE, List.of(PAULISTA)))
			.isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	void tableRequiresDestinations() {
		assertThatThrownBy(() -> client.table(SE, List.of())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void routeReturnsGeoJsonGeometryTotalsAndLegs() {
		WIREMOCK.stubFor(get(urlPathMatching(ROUTE)).willReturn(okJson("""
				{"code": "Ok", "routes": [{
				  "geometry": {"type": "LineString", "coordinates": [[-46.6333, -23.5505], [-46.6519, -23.5649], [-46.656, -23.5614]]},
				  "legs": [{"distance": 3322.9, "duration": 404.7, "summary": ""}, {"distance": 2261.8, "duration": 300.2, "summary": ""}],
				  "distance": 5584.7, "duration": 704.9}]}
				""")));

		var route = client.route(List.of(SE, PAULISTA, CONSOLACAO));

		assertThat(route.distanceM()).isEqualTo(5584.7);
		assertThat(route.durationS()).isEqualTo(704.9);
		assertThat(route.legs()).containsExactly(new RouteResult.Leg(3322.9, 404.7), new RouteResult.Leg(2261.8, 300.2));
		assertThat(route.geometryGeoJson()).startsWith("{\"type\":\"LineString\"").contains("[-46.6333,-23.5505]");
		WIREMOCK.verify(getRequestedFor(urlEqualTo("/osrm/route/v1/driving/"
				+ "-46.633300,-23.550500;-46.651900,-23.564900;-46.656000,-23.561400"
				+ "?overview=full&geometries=geojson&steps=false&radiuses=1000;1000;1000")));
	}

	@Test
	void routeWithNoRouteCodeIsRouteNotFound() {
		WIREMOCK.stubFor(get(urlPathMatching(ROUTE)).willReturn(okJson("{\"code\": \"NoRoute\", \"message\": \"Impossible route\"}")));

		assertThatThrownBy(() -> client.route(List.of(SE, PAULISTA))).isInstanceOf(RouteNotFoundException.class);
	}

	@Test
	void routeWithoutGeometryIsServiceError() {
		WIREMOCK.stubFor(get(urlPathMatching(ROUTE)).willReturn(okJson("{\"code\": \"Ok\", \"routes\": []}")));

		assertThatThrownBy(() -> client.route(List.of(SE, PAULISTA)))
			.isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	void routeRequiresTwoCoordinates() {
		assertThatThrownBy(() -> client.route(List.of(SE))).isInstanceOf(IllegalArgumentException.class);
	}

}
