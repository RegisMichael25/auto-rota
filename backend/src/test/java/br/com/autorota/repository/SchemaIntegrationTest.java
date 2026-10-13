package br.com.autorota.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.autorota.PostgresIntegrationTest;
import br.com.autorota.domain.Address;
import br.com.autorota.domain.CoordSource;
import br.com.autorota.domain.GeocodeCache;
import br.com.autorota.domain.RoutePlan;
import br.com.autorota.domain.RoutePlanStatus;
import br.com.autorota.domain.RouteStop;
import br.com.autorota.domain.StopStatus;
import jakarta.persistence.EntityManager;

@Transactional
class SchemaIntegrationTest extends PostgresIntegrationTest {

	private static final String GEOMETRY = "{\"type\":\"LineString\",\"coordinates\":[[-46.63,-23.55],[-46.65,-23.56]]}";

	@Autowired
	private RoutePlanRepository routePlans;

	@Autowired
	private GeocodeCacheRepository geocodeCache;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager em;

	@Test
	void flywayAppliesV1() {
		var applied = jdbc.queryForList(
				"SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
		assertThat(applied).containsExactly("1");

		var tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
		assertThat(tables).contains("route_plan", "route_stop", "cep_cache", "geocode_cache");
	}

	@Test
	void persistsRoundTripPlanWithStopsOrderedByVisit() {
		var plan = roundTripPlan();
		plan.addStop(stop(1, 2));
		plan.addStop(stop(2, 1));
		var id = routePlans.saveAndFlush(plan).getId();
		em.clear();

		var loaded = routePlans.findById(id).orElseThrow();
		assertThat(loaded.getStatus()).isEqualTo(RoutePlanStatus.PLANEJADA);
		assertThat(loaded.getFuelCost()).isEqualByComparingTo("12.34");
		assertThat(loaded.getReturnDistanceM()).isEqualTo(900);
		assertThat(loaded.getRouteGeometry()).contains("LineString");
		assertThat(loaded.getStops()).extracting(RouteStop::getVisitOrder).containsExactly(1, 2);
		assertThat(loaded.getStops()).allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(StopStatus.PENDENTE));
	}

	@Test
	void persistsOpenPlanWithoutReturnLeg() {
		var plan = new RoutePlan(null, origin(), false, new BigDecimal("10"), new BigDecimal("6.199"));
		plan.applyTotals(1000, 120, new BigDecimal("0.100"), new BigDecimal("0.62"), null, null, GEOMETRY);
		plan.addStop(stop(1, 1));

		assertThat(routePlans.saveAndFlush(plan).getId()).isNotNull();
	}

	@Test
	void rejectsRoundTripWithoutReturnLeg() {
		var plan = roundTripPlan();
		plan.applyTotals(1000, 120, BigDecimal.ONE, BigDecimal.ONE, null, null, GEOMETRY);

		assertThatThrownBy(() -> routePlans.saveAndFlush(plan)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_route_plan_return");
	}

	@Test
	void rejectsOpenRouteWithReturnLeg() {
		var plan = new RoutePlan(null, origin(), false, BigDecimal.TEN, BigDecimal.ONE);
		plan.applyTotals(1000, 120, BigDecimal.ONE, BigDecimal.ONE, 500, 60, GEOMETRY);

		assertThatThrownBy(() -> routePlans.saveAndFlush(plan)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_route_plan_return");
	}

	@Test
	void rejectsNonPositiveConsumption() {
		var plan = new RoutePlan(null, origin(), false, BigDecimal.ZERO, BigDecimal.ONE);
		plan.applyTotals(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null);

		assertThatThrownBy(() -> routePlans.saveAndFlush(plan)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_route_plan_consumption");
	}

	@Test
	void rejectsInvalidUf() {
		var plan = new RoutePlan(null, new Address("01001000", "Praça da Sé", "1", null, "Sé", "São Paulo", "XX",
				-23.55, -46.63), false, BigDecimal.TEN, BigDecimal.ONE);
		plan.applyTotals(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null);

		assertThatThrownBy(() -> routePlans.saveAndFlush(plan)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_route_plan_origin_uf");
	}

	@Test
	void rejectsDuplicateVisitOrder() {
		var plan = roundTripPlan();
		plan.addStop(stop(1, 1));
		plan.addStop(stop(2, 1));

		assertThatThrownBy(() -> routePlans.saveAndFlush(plan)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uq_route_stop_visit_order");
	}

	@Test
	void rejectsDeliveredWithoutDeliveredAt() {
		var plan = roundTripPlan();
		plan.addStop(stop(1, 1));
		var stopId = routePlans.saveAndFlush(plan).getStops().get(0).getId();

		assertThatThrownBy(() -> jdbc.update("UPDATE route_stop SET status = 'ENTREGUE' WHERE id = ?", stopId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_route_stop_delivered_at");
	}

	@Test
	void marksStopDelivered() {
		var plan = roundTripPlan();
		plan.addStop(stop(1, 1));
		plan.getStops().get(0).markDelivered(Instant.now(), "Recebido pelo porteiro");
		var id = routePlans.saveAndFlush(plan).getId();
		em.clear();

		var stop = routePlans.findById(id).orElseThrow().getStops().get(0);
		assertThat(stop.getStatus()).isEqualTo(StopStatus.ENTREGUE);
		assertThat(stop.getDeliveredAt()).isNotNull();
	}

	@Test
	void deletingPlanCascadesToStops() {
		var plan = roundTripPlan();
		plan.addStop(stop(1, 1));
		plan.addStop(stop(2, 2));
		UUID id = routePlans.saveAndFlush(plan).getId();

		jdbc.update("DELETE FROM route_plan WHERE id = ?", id);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM route_stop WHERE route_plan_id = ?", Integer.class, id))
			.isZero();
	}

	@Test
	void geocodeCacheStoresNotFound() {
		var key = "a".repeat(64);
		geocodeCache.saveAndFlush(new GeocodeCache(key, "rua inexistente, 1, cidade, uf", CoordSource.NOMINATIM,
				null, null));
		em.clear();

		assertThat(geocodeCache.findById(key).orElseThrow().isFound()).isFalse();
	}

	private static RoutePlan roundTripPlan() {
		var plan = new RoutePlan("Entrega teste", origin(), true, new BigDecimal("10.50"), new BigDecimal("6.199"));
		plan.applyTotals(5000, 600, new BigDecimal("0.476"), new BigDecimal("12.34"), 900, 120, GEOMETRY);
		return plan;
	}

	private static Address origin() {
		return new Address("01001000", "Praça da Sé", "100", null, "Sé", "São Paulo", "SP", -23.5505, -46.6333);
	}

	private static RouteStop stop(int inputOrder, int visitOrder) {
		var address = new Address("01310100", "Avenida Paulista", String.valueOf(inputOrder * 100), null,
				"Bela Vista", "São Paulo", "SP", -23.5614, -46.6560);
		return new RouteStop(inputOrder, visitOrder, address, CoordSource.BRASILAPI, 1000 * visitOrder,
				100 * visitOrder);
	}

}
