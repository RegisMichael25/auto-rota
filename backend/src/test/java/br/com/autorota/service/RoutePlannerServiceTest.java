package br.com.autorota.service;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.autorota.PostgresIntegrationTest;
import br.com.autorota.domain.CoordSource;
import br.com.autorota.domain.RouteNotFoundException;
import br.com.autorota.domain.RoutePlanStatus;
import br.com.autorota.domain.RouteStop;
import br.com.autorota.domain.StopStatus;
import br.com.autorota.repository.CepCacheRepository;
import br.com.autorota.repository.GeocodeCacheRepository;
import br.com.autorota.repository.RoutePlanRepository;
import br.com.autorota.service.PlanRouteCommand.AddressInput;

/**
 * Fluxo completo com APIs e OSRM simulados. Origem O e destinos informados A, B, C ficam numa reta:
 * O(0 km) — C(1 km) — A(2 km) — B(3 km). A ordem ótima é C, A, B nos dois modos (input_order 3, 1, 2).
 */
class RoutePlannerServiceTest extends PostgresIntegrationTest {

	private static final String ORIGIN = "01001000";

	private static final String A = "01310100";

	private static final String B = "01311000";

	private static final String C = "01002000";

	@Autowired
	private RoutePlannerService planner;

	@Autowired
	private RoutePlanRepository routePlans;

	@Autowired
	private CepCacheRepository cepCache;

	@Autowired
	private GeocodeCacheRepository geocodeCache;

	@BeforeEach
	void setUp() {
		routePlans.deleteAll();
		cepCache.deleteAll();
		geocodeCache.deleteAll();
		stubCep(ORIGIN, "Praça da Sé", -23.5505, -46.6333);
		stubCep(A, "Avenida Paulista", -23.5649, -46.6519);
		stubCep(B, "Avenida Paulista", -23.5700, -46.6600);
		stubCep(C, "Rua Direita", -23.5480, -46.6360);

		// Índices: 0 = O, 1 = A, 2 = B, 3 = C (ordem informada).
		WIREMOCK.stubFor(get(urlPathMatching("/osrm/table/v1/driving/.*")).willReturn(okJson("""
				{"code": "Ok",
				 "distances": [[0, 2000, 3000, 1000], [2000, 0, 1000, 1000], [3000, 1000, 0, 2000], [1000, 1000, 2000, 0]],
				 "durations": [[0, 200, 300, 100], [200, 0, 100, 100], [300, 100, 0, 200], [100, 100, 200, 0]]}
				""")));
	}

	@Test
	void plansRoundTripUsingRouteLegsAndPersistsEverything() {
		// Trechos do /route levemente diferentes da matriz: os gravados devem ser os do /route.
		stubRoute(legs(1010, 101, 1020, 102, 1030, 103, 3040, 304));

		var plan = planner.plan(new PlanRouteCommand("Entregas manhã", input(ORIGIN, "1"),
				List.of(input(A, "1000"), input(B, "2000"), input(C, "10")), true, new BigDecimal("10"),
				new BigDecimal("6.199")));

		var loaded = routePlans.findWithStopsById(plan.getId()).orElseThrow();
		assertThat(loaded.getName()).isEqualTo("Entregas manhã");
		assertThat(loaded.isRoundTrip()).isTrue();
		assertThat(loaded.getStatus()).isEqualTo(RoutePlanStatus.PLANEJADA);
		assertThat(loaded.getOrigin().getCep()).isEqualTo(ORIGIN);
		assertThat(loaded.getStops()).extracting(RouteStop::getVisitOrder).containsExactly(1, 2, 3);
		assertThat(loaded.getStops()).extracting(RouteStop::getInputOrder).containsExactly(3, 1, 2);
		assertThat(loaded.getStops()).extracting(s -> s.getAddress().getCep()).containsExactly(C, A, B);
		assertThat(loaded.getStops()).extracting(RouteStop::getLegDistanceM).containsExactly(1010, 1020, 1030);
		assertThat(loaded.getStops()).extracting(RouteStop::getLegDurationS).containsExactly(101, 102, 103);
		assertThat(loaded.getStops()).allSatisfy(s -> {
			assertThat(s.getStatus()).isEqualTo(StopStatus.PENDENTE);
			assertThat(s.getCoordSource()).isEqualTo(CoordSource.BRASILAPI);
		});
		assertThat(loaded.getReturnDistanceM()).isEqualTo(3040);
		assertThat(loaded.getReturnDurationS()).isEqualTo(304);
		assertThat(loaded.getTotalDistanceM()).isEqualTo(6100);
		assertThat(loaded.getTotalDurationS()).isEqualTo(610);
		// 6,1 km / 10 = 0,61 L × 6,199 = 3,78139 → 3,78
		assertThat(loaded.getFuelLiters()).isEqualByComparingTo("0.610");
		assertThat(loaded.getFuelCost()).isEqualByComparingTo("3.78");
		assertThat(loaded.getRouteGeometry()).contains("LineString");

		// /route recebe O → C → A → B → O.
		WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/osrm/route/v1/driving/"
				+ coords(-46.6333, -23.5505, -46.6360, -23.5480, -46.6519, -23.5649, -46.6600, -23.5700, -46.6333,
						-23.5505))));
	}

	@Test
	void plansOpenRouteWithoutReturnLeg() {
		stubRoute(legs(1010, 101, 1020, 102, 1030, 103));

		var plan = planner.plan(new PlanRouteCommand(null, input(ORIGIN, "1"),
				List.of(input(A, "1000"), input(B, "2000"), input(C, "10")), false, new BigDecimal("10"),
				new BigDecimal("6.199")));

		var loaded = routePlans.findWithStopsById(plan.getId()).orElseThrow();
		assertThat(loaded.isRoundTrip()).isFalse();
		assertThat(loaded.getName()).isNull();
		assertThat(loaded.getStops()).extracting(RouteStop::getInputOrder).containsExactly(3, 1, 2);
		assertThat(loaded.getReturnDistanceM()).isNull();
		assertThat(loaded.getReturnDurationS()).isNull();
		assertThat(loaded.getTotalDistanceM()).isEqualTo(3060);
		assertThat(loaded.getTotalDurationS()).isEqualTo(306);
		// 3,06 km / 10 = 0,306 L × 6,199 = 1,896894 → 1,90
		assertThat(loaded.getFuelLiters()).isEqualByComparingTo("0.306");
		assertThat(loaded.getFuelCost()).isEqualByComparingTo("1.90");

		WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/osrm/route/v1/driving/"
				+ coords(-46.6333, -23.5505, -46.6360, -23.5480, -46.6519, -23.5649, -46.6600, -23.5700))));
	}

	@Test
	void usesDefaultsWhenOptionalFieldsAreMissing() {
		stubRoute(legs(1000, 100, 1000, 100, 1000, 100, 3000, 300));

		var plan = planner.plan(new PlanRouteCommand(" ", input(ORIGIN, "1"),
				List.of(input(A, "1000"), input(B, "2000"), input(C, "10")), null, null, null));

		assertThat(plan.isRoundTrip()).isTrue();
		assertThat(plan.getName()).isNull();
		assertThat(plan.getConsumptionKmPerL()).isEqualByComparingTo("10.00");
		assertThat(plan.getFuelPricePerL()).isEqualByComparingTo("6.000");
		// 6 km / 10 = 0,6 L × 6,00 = 3,60
		assertThat(plan.getFuelCost()).isEqualByComparingTo("3.60");
	}

	@Test
	void routeNotFoundPersistsNothing() {
		WIREMOCK.stubFor(get(urlPathMatching("/osrm/table/v1/driving/.*"))
			.willReturn(okJson("{\"code\": \"NoRoute\", \"message\": \"Impossible route\"}")));

		assertThatThrownBy(() -> planner.plan(new PlanRouteCommand(null, input(ORIGIN, "1"), List.of(input(A, "1")),
				true, null, null)))
			.isInstanceOf(RouteNotFoundException.class);
		assertThat(routePlans.count()).isZero();
	}

	@Test
	void unexpectedLegCountFromRoutePersistsNothing() {
		stubRoute(legs(1000, 100));

		assertThatThrownBy(() -> planner.plan(new PlanRouteCommand(null, input(ORIGIN, "1"),
				List.of(input(A, "1000"), input(B, "2000")), true, null, null)))
			.hasMessageContaining("OSRM");
		assertThat(routePlans.count()).isZero();
	}

	@Test
	void rejectsMoreThanTenDestinationsBeforeAnyExternalCall() {
		var eleven = Collections.nCopies(11, input(A, "1"));

		assertThatThrownBy(
				() -> planner.plan(new PlanRouteCommand(null, input(ORIGIN, "1"), eleven, true, null, null)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> planner.plan(new PlanRouteCommand(null, input(ORIGIN, "1"), List.of(), true, null, null)))
			.isInstanceOf(IllegalArgumentException.class);
		WIREMOCK.verify(0, anyRequestedFor(urlPathMatching(".*")));
	}

	private static AddressInput input(String cep, String number) {
		return new AddressInput(cep, number);
	}

	private static void stubCep(String cep, String street, double lat, double lng) {
		WIREMOCK.stubFor(get(urlPathEqualTo("/viacep/ws/" + cep + "/json/")).willReturn(okJson("""
				{"cep": "%s", "logradouro": "%s", "bairro": "Centro", "localidade": "São Paulo", "uf": "SP"}
				""".formatted(cep, street))));
		WIREMOCK.stubFor(get(urlPathEqualTo("/brasilapi/api/cep/v2/" + cep)).willReturn(okJson(String.format(Locale.ROOT,
				"{\"location\": {\"coordinates\": {\"latitude\": \"%s\", \"longitude\": \"%s\"}}}", lat, lng))));
	}

	private static void stubRoute(String legsJson) {
		WIREMOCK.stubFor(get(urlPathMatching("/osrm/route/v1/driving/.*")).willReturn(okJson("""
				{"code": "Ok", "routes": [{
				  "geometry": {"type": "LineString", "coordinates": [[-46.6333, -23.5505], [-46.66, -23.57]]},
				  "legs": %s, "distance": 0, "duration": 0}]}
				""".formatted(legsJson))));
	}

	/** Pares (distância, duração) → array JSON de trechos. */
	private static String legs(int... values) {
		var json = new StringBuilder("[");
		for (int i = 0; i < values.length; i += 2) {
			json.append(i == 0 ? "" : ",")
				.append("{\"distance\": ").append(values[i]).append(", \"duration\": ").append(values[i + 1]).append("}");
		}
		return json.append("]").toString();
	}

	/** Pares (lng, lat) → "lng,lat;lng,lat" com 6 casas, como o OsrmClient envia. */
	private static String coords(double... lngLat) {
		var path = new StringBuilder();
		for (int i = 0; i < lngLat.length; i += 2) {
			path.append(i == 0 ? "" : ";").append(String.format(Locale.ROOT, "%.6f,%.6f", lngLat[i], lngLat[i + 1]));
		}
		return path.toString();
	}

}
