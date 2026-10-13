package br.com.autorota.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.autorota.PostgresIntegrationTest;
import br.com.autorota.domain.Address;
import br.com.autorota.domain.CoordSource;
import br.com.autorota.domain.RoutePlan;
import br.com.autorota.domain.RoutePlanStatus;
import br.com.autorota.domain.RouteStop;
import br.com.autorota.repository.RoutePlanRepository;

class RouteTrackingServiceTest extends PostgresIntegrationTest {

	private static final String GEOMETRY = "{\"type\":\"LineString\",\"coordinates\":[[-46.63,-23.55],[-46.65,-23.56]]}";

	@Autowired
	private RouteTrackingService tracking;

	@Autowired
	private RoutePlanRepository routePlans;

	@BeforeEach
	void clean() {
		routePlans.deleteAll();
	}

	/** Sem lock, as duas transações veem a outra parada pendente e o plano fica EM_ANDAMENTO para sempre. */
	@RepeatedTest(5)
	void concurrentMarksOnDifferentStopsCompleteThePlan() throws Exception {
		UUID id = routePlans.save(planWithTwoStops()).getId();
		var start = new CountDownLatch(1);

		var first = CompletableFuture.runAsync(() -> {
			await(start);
			tracking.markDelivered(id, 1, null);
		});
		var second = CompletableFuture.runAsync(() -> {
			await(start);
			tracking.markNotDelivered(id, 2, "Cliente ausente");
		});
		start.countDown();
		CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);

		assertThat(tracking.get(id).getStatus()).isEqualTo(RoutePlanStatus.CONCLUIDA);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static RoutePlan planWithTwoStops() {
		var origin = new Address("01001000", "Praça da Sé", "1", null, "Sé", "São Paulo", "SP", -23.5505, -46.6333);
		var plan = new RoutePlan("Concorrência", origin, false, BigDecimal.TEN, new BigDecimal("6.000"));
		plan.applyTotals(2000, 200, new BigDecimal("0.200"), new BigDecimal("1.20"), null, null, GEOMETRY);
		for (int order = 1; order <= 2; order++) {
			var address = new Address("01310100", "Avenida Paulista", String.valueOf(order), null, "Bela Vista",
					"São Paulo", "SP", -23.56, -46.65);
			plan.addStop(new RouteStop(order, order, address, CoordSource.NOMINATIM, 1000, 100));
		}
		return plan;
	}

}
