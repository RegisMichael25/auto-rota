package br.com.autorota.web;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import br.com.autorota.PostgresIntegrationTest;
import br.com.autorota.repository.CepCacheRepository;
import br.com.autorota.repository.GeocodeCacheRepository;
import br.com.autorota.repository.RoutePlanRepository;
import br.com.autorota.service.PlanRouteCommand;
import br.com.autorota.service.PlanRouteCommand.AddressInput;
import br.com.autorota.service.RoutePlannerService;

/** Mesmo cenário do RoutePlannerServiceTest: O — C — A — B numa reta; ordem ótima C, A, B. */
@AutoConfigureMockMvc
class RouteControllerTest extends PostgresIntegrationTest {

	private static final String ORIGIN = "01001000";

	private static final String A = "01310100";

	private static final String B = "01311000";

	private static final String C = "01002000";

	private static final String VALID_PLAN = """
			{"name": "Entregas manhã",
			 "origin": {"cep": "01001-000", "number": "1"},
			 "destinations": [{"cep": "01310-100", "number": "1000"}, {"cep": "01311000", "number": "2000"},
			                  {"cep": "01002-000", "number": "10"}],
			 "roundTrip": true, "consumptionKmPerL": 10, "fuelPricePerL": 6.199}
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RoutePlanRepository routePlans;

	@Autowired
	private RoutePlannerService planner;

	@Autowired
	private CepCacheRepository cepCache;

	@Autowired
	private GeocodeCacheRepository geocodeCache;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		routePlans.deleteAll();
		cepCache.deleteAll();
		geocodeCache.deleteAll();
		stubCep(ORIGIN, -23.5505, -46.6333);
		stubCep(A, -23.5649, -46.6519);
		stubCep(B, -23.5700, -46.6600);
		stubCep(C, -23.5480, -46.6360);
		WIREMOCK.stubFor(get(urlPathMatching("/osrm/table/v1/driving/.*")).willReturn(okJson("""
				{"code": "Ok",
				 "distances": [[0, 2000, 3000, 1000], [2000, 0, 1000, 1000], [3000, 1000, 0, 2000], [1000, 1000, 2000, 0]],
				 "durations": [[0, 200, 300, 100], [200, 0, 100, 100], [300, 100, 0, 200], [100, 100, 200, 0]]}
				""")));
		stubRoute(4);
	}

	// ---------- POST /api/routes/plan ----------

	@Test
	void planReturnsCreatedWithOptimizedRoute() throws Exception {
		postJson("/api/routes/plan", VALID_PLAN).andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/routes/")))
			.andExpect(jsonPath("$.id").value(notNullValue()))
			.andExpect(jsonPath("$.status").value("PLANEJADA"))
			.andExpect(jsonPath("$.roundTrip").value(true))
			.andExpect(jsonPath("$.stops[*].visitOrder").value(contains(1, 2, 3)))
			.andExpect(jsonPath("$.stops[*].inputOrder").value(contains(3, 1, 2)))
			.andExpect(jsonPath("$.stops[0].address.cep").value(C))
			.andExpect(jsonPath("$.stops[0].coordSource").value("BRASILAPI"))
			.andExpect(jsonPath("$.totalDistanceM").value(6000))
			.andExpect(jsonPath("$.returnDistanceM").value(3000))
			.andExpect(jsonPath("$.fuelLiters").value(0.6))
			.andExpect(jsonPath("$.fuelCost").value(3.72))
			.andExpect(jsonPath("$.routeGeometry.type").value("LineString"));
	}

	@Test
	void openRoutePlanHasNoReturnLeg() throws Exception {
		stubRoute(3);

		postJson("/api/routes/plan", VALID_PLAN.replace("\"roundTrip\": true", "\"roundTrip\": false"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.roundTrip").value(false))
			.andExpect(jsonPath("$.returnDistanceM").value(nullValue()))
			.andExpect(jsonPath("$.totalDistanceM").value(3000));
	}

	@Test
	void rejectsMoreThanTenDestinations() throws Exception {
		String eleven = String.join(",", Collections.nCopies(11, "{\"cep\": \"01310100\", \"number\": \"1\"}"));

		postJson("/api/routes/plan", """
				{"origin": {"cep": "01001000", "number": "1"}, "destinations": [%s]}
				""".formatted(eleven))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[*].field").value(hasItem("destinations")));
		WIREMOCK.verify(0, anyRequestedFor(urlPathMatching(".*")));
	}

	@Test
	void rejectsInvalidFields() throws Exception {
		postJson("/api/routes/plan", """
				{"origin": {"cep": "123", "number": " "},
				 "destinations": [{"cep": "01310-100", "number": "1"}],
				 "consumptionKmPerL": 0, "fuelPricePerL": 6.1999}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(hasItem("origin.cep")))
			.andExpect(jsonPath("$.errors[*].field").value(hasItem("origin.number")))
			.andExpect(jsonPath("$.errors[*].field").value(hasItem("consumptionKmPerL")))
			.andExpect(jsonPath("$.errors[*].field").value(hasItem("fuelPricePerL")))
			.andExpect(jsonPath("$.errors[*].message").value(hasItem("CEP deve ter 8 dígitos")));
	}

	@Test
	void rejectsMissingOriginAndEmptyDestinations() throws Exception {
		postJson("/api/routes/plan", "{\"destinations\": []}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(hasItem("origin")))
			.andExpect(jsonPath("$.errors[*].field").value(hasItem("destinations")));
	}

	@Test
	void rejectsMalformedJson() throws Exception {
		postJson("/api/routes/plan", "{\"origin\": ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void unknownCepInPlanIsNotFoundProblem() throws Exception {
		WIREMOCK.stubFor(get(urlPathEqualTo("/viacep/ws/" + A + "/json/")).willReturn(okJson("{\"erro\": \"true\"}")));

		postJson("/api/routes/plan", VALID_PLAN).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CEP_NOT_FOUND"))
			.andExpect(jsonPath("$.cep").value(A));
	}

	@Test
	void impossibleRouteIsUnprocessable() throws Exception {
		WIREMOCK.stubFor(get(urlPathMatching("/osrm/table/v1/driving/.*"))
			.willReturn(okJson("{\"code\": \"NoRoute\", \"message\": \"Impossible route\"}")));

		postJson("/api/routes/plan", VALID_PLAN).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
	}

	@Test
	void osrmDownIsServiceUnavailable() throws Exception {
		WIREMOCK.stubFor(get(urlPathMatching("/osrm/table/v1/driving/.*")).willReturn(serverError()));

		postJson("/api/routes/plan", VALID_PLAN).andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("EXTERNAL_SERVICE_UNAVAILABLE"))
			.andExpect(jsonPath("$.service").value("OSRM"));
	}

	// ---------- GET /api/routes ----------

	@Test
	void listsMostRecentFirstWithPaging() throws Exception {
		var older = planNamed("Rota antiga");
		var newer = planNamed("Rota nova");
		setCreatedAt(older, "2026-10-01T12:00:00Z");
		setCreatedAt(newer, "2026-10-10T12:00:00Z");

		mvc.perform(getRequest("/api/routes").param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(newer.toString()))
			.andExpect(jsonPath("$.content[0].stopCount").value(3))
			.andExpect(jsonPath("$.content[0].originCity").value("São Paulo"))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void filtersByPeriodAndName() throws Exception {
		var first = planNamed("Entregas Centro");
		var second = planNamed("Entregas Zona Sul");
		var third = planNamed("Coleta");
		setCreatedAt(first, "2026-10-01T15:00:00Z");
		setCreatedAt(second, "2026-10-05T15:00:00Z");
		setCreatedAt(third, "2026-10-05T16:00:00Z");

		mvc.perform(getRequest("/api/routes").param("from", "2026-10-02").param("to", "2026-10-05")
			.param("name", "entregas"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(second.toString()));
	}

	@Test
	void periodEndIsInclusiveInLocalTime() throws Exception {
		var lateEvening = planNamed("Noite");
		// 23:30 em São Paulo do dia 05 = 02:30Z do dia 06.
		setCreatedAt(lateEvening, "2026-10-06T02:30:00Z");

		mvc.perform(getRequest("/api/routes").param("to", "2026-10-05"))
			.andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(getRequest("/api/routes").param("from", "2026-10-06"))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void rejectsInvalidPagingAndPeriod() throws Exception {
		mvc.perform(getRequest("/api/routes").param("size", "500")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(getRequest("/api/routes").param("from", "2026-10-10").param("to", "2026-10-01"))
			.andExpect(status().isBadRequest());
		mvc.perform(getRequest("/api/routes").param("from", "10/10/2026")).andExpect(status().isBadRequest());
	}

	// ---------- GET /api/routes/{id} ----------

	@Test
	void getsDetail() throws Exception {
		var id = planNamed("Detalhe");

		mvc.perform(getRequest("/api/routes/" + id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id.toString()))
			.andExpect(jsonPath("$.origin.cep").value(ORIGIN))
			.andExpect(jsonPath("$.stops.length()").value(3))
			.andExpect(jsonPath("$.routeGeometry.coordinates").isArray());
	}

	@Test
	void unknownOrMalformedIdIsProblem() throws Exception {
		mvc.perform(getRequest("/api/routes/" + UUID.randomUUID())).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mvc.perform(getRequest("/api/routes/nao-e-uuid")).andExpect(status().isBadRequest());
	}

	// ---------- entregas ----------

	@Test
	void deliveringAndFailingStopsUpdatesPlanStatus() throws Exception {
		var id = planNamed("Entregas");

		postJson("/api/routes/" + id + "/stops/1/deliver", "{\"deliveryNote\": \"Recebido pelo porteiro\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
			.andExpect(jsonPath("$.stops[0].status").value("ENTREGUE"))
			.andExpect(jsonPath("$.stops[0].deliveredAt").value(notNullValue()))
			.andExpect(jsonPath("$.stops[0].deliveryNote").value("Recebido pelo porteiro"));

		mvc.perform(post("/api/routes/" + id + "/stops/2/fail")).andExpect(status().isOk())
			.andExpect(jsonPath("$.stops[1].status").value("NAO_ENTREGUE"))
			.andExpect(jsonPath("$.stops[1].deliveredAt").value(nullValue()))
			.andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));

		postJson("/api/routes/" + id + "/stops/3/deliver", "{}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONCLUIDA"));

		// Persistido, não só na resposta.
		mvc.perform(getRequest("/api/routes/" + id)).andExpect(jsonPath("$.status").value("CONCLUIDA"))
			.andExpect(jsonPath("$.stops[0].status").value("ENTREGUE"));
	}

	@Test
	void correctingAStopIsAllowed() throws Exception {
		var id = planNamed("Correção");

		postJson("/api/routes/" + id + "/stops/1/deliver", "{}").andExpect(status().isOk());
		postJson("/api/routes/" + id + "/stops/1/fail", "{\"deliveryNote\": \"Endereço errado\"}")
			.andExpect(jsonPath("$.stops[0].status").value("NAO_ENTREGUE"))
			.andExpect(jsonPath("$.stops[0].deliveredAt").value(nullValue()));
	}

	@Test
	void deliveryErrors() throws Exception {
		var id = planNamed("Erros");

		postJson("/api/routes/" + id + "/stops/9/deliver", "{}").andExpect(status().isNotFound());
		postJson("/api/routes/" + id + "/stops/11/deliver", "{}").andExpect(status().isBadRequest());
		postJson("/api/routes/" + UUID.randomUUID() + "/stops/1/deliver", "{}").andExpect(status().isNotFound());
		postJson("/api/routes/" + id + "/stops/1/deliver", "{\"deliveryNote\": \"" + "x".repeat(501) + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("deliveryNote"));
	}

	// ---------- auxiliares ----------

	private UUID planNamed(String name) {
		return planner.plan(new PlanRouteCommand(name, new AddressInput(ORIGIN, "1"),
				List.of(new AddressInput(A, "1000"), new AddressInput(B, "2000"), new AddressInput(C, "10")), true,
				BigDecimal.TEN, new BigDecimal("6.199")))
			.getId();
	}

	private void setCreatedAt(UUID id, String instant) {
		jdbc.update("UPDATE route_plan SET created_at = ? WHERE id = ?", Timestamp.from(Instant.parse(instant)), id);
	}

	private ResultActions postJson(String url, String body) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** {@code MockMvcRequestBuilders.get} colide com o {@code get} estático do WireMock. */
	private static MockHttpServletRequestBuilder getRequest(String url) {
		return MockMvcRequestBuilders.get(url);
	}

	private static void stubCep(String cep, double lat, double lng) {
		WIREMOCK.stubFor(get(urlPathEqualTo("/viacep/ws/" + cep + "/json/")).willReturn(okJson("""
				{"cep": "%s", "logradouro": "Rua Teste", "bairro": "Centro", "localidade": "São Paulo", "uf": "SP"}
				""".formatted(cep))));
		WIREMOCK.stubFor(get(urlPathEqualTo("/brasilapi/api/cep/v2/" + cep)).willReturn(okJson(String.format(Locale.ROOT,
				"{\"location\": {\"coordinates\": {\"latitude\": \"%s\", \"longitude\": \"%s\"}}}", lat, lng))));
	}

	/** Trechos de 1 km/100 s; com 4 trechos o último (volta) tem 3 km/300 s. */
	private static void stubRoute(int legCount) {
		var legs = new StringBuilder("[");
		for (int i = 0; i < legCount; i++) {
			boolean back = legCount == 4 && i == 3;
			legs.append(i == 0 ? "" : ",")
				.append(back ? "{\"distance\": 3000, \"duration\": 300}" : "{\"distance\": 1000, \"duration\": 100}");
		}
		legs.append("]");
		WIREMOCK.stubFor(get(urlPathMatching("/osrm/route/v1/driving/.*")).willReturn(okJson("""
				{"code": "Ok", "routes": [{
				  "geometry": {"type": "LineString", "coordinates": [[-46.6333, -23.5505], [-46.66, -23.57]]},
				  "legs": %s, "distance": 0, "duration": 0}]}
				""".formatted(legs))));
	}

}
