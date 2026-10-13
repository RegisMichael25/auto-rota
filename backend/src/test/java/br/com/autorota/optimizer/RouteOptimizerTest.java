package br.com.autorota.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import br.com.autorota.client.DistanceMatrix;

class RouteOptimizerTest {

	private final RouteOptimizer optimizer = new RouteOptimizer();

	/** Origem + 3 destinos em linha: 0 — 1 — 2 — 3, 1 km entre vizinhos; duração = 0,1 s por metro. */
	private static final DistanceMatrix LINE = matrix(new double[][] {
			{ 0, 1000, 2000, 3000 },
			{ 1000, 0, 1000, 2000 },
			{ 2000, 1000, 0, 1000 },
			{ 3000, 2000, 1000, 0 } });

	@Test
	void roundTripIncludesReturnLegInTotals() {
		var route = optimizer.optimize(LINE, true, OptimizationMetric.DISTANCE);

		assertThat(route.stops()).extracting(OptimizedRoute.Stop::matrixIndex).containsExactly(1, 2, 3);
		assertThat(route.stops()).extracting(OptimizedRoute.Stop::visitOrder).containsExactly(1, 2, 3);
		assertThat(route.stops()).extracting(s -> s.leg().distanceM()).containsExactly(1000, 1000, 1000);
		assertThat(route.returnLeg()).isEqualTo(new OptimizedRoute.Leg(3000, 300));
		assertThat(route.totalDistanceM()).isEqualTo(6000);
		assertThat(route.totalDurationS()).isEqualTo(600);
	}

	@Test
	void openRouteHasNoReturnLeg() {
		var route = optimizer.optimize(LINE, false, OptimizationMetric.DISTANCE);

		assertThat(route.stops()).extracting(OptimizedRoute.Stop::matrixIndex).containsExactly(1, 2, 3);
		assertThat(route.returnLeg()).isNull();
		assertThat(route.totalDistanceM()).isEqualTo(3000);
		assertThat(route.totalDurationS()).isEqualTo(300);
	}

	@Test
	void openAndRoundTripCanChooseDifferentOrders() {
		// Destino 1 fica perto da origem; destino 2 fica longe e perto de 1.
		// Aberta: 0→1→2 = 10+10 = 20. Circular: 0→2→1→0 = 15+10+1 = 26 vence 0→1→2→0 = 10+10+30 = 50.
		var asymmetric = matrix(new double[][] {
				{ 0, 10, 15 },
				{ 1, 0, 10 },
				{ 30, 10, 0 } });

		var open = optimizer.optimize(asymmetric, false, OptimizationMetric.DISTANCE);
		var roundTrip = optimizer.optimize(asymmetric, true, OptimizationMetric.DISTANCE);

		assertThat(open.stops()).extracting(OptimizedRoute.Stop::matrixIndex).containsExactly(1, 2);
		assertThat(open.totalDistanceM()).isEqualTo(20);
		assertThat(roundTrip.stops()).extracting(OptimizedRoute.Stop::matrixIndex).containsExactly(2, 1);
		assertThat(roundTrip.returnLeg().distanceM()).isEqualTo(1);
		assertThat(roundTrip.totalDistanceM()).isEqualTo(26);
	}

	@Test
	void durationMetricOptimizesTime() {
		// Por distância, 1 antes de 2; por tempo (1 é lento), 2 antes de 1.
		double[][] distances = { { 0, 10, 20 }, { 10, 0, 10 }, { 20, 10, 0 } };
		double[][] durations = { { 0, 500, 20 }, { 500, 0, 10 }, { 20, 10, 0 } };
		var matrix = new DistanceMatrix(distances, durations);

		var byDistance = optimizer.optimize(matrix, false, OptimizationMetric.DISTANCE);
		var byDuration = optimizer.optimize(matrix, false, OptimizationMetric.DURATION);

		assertThat(byDistance.stops()).extracting(OptimizedRoute.Stop::matrixIndex).containsExactly(1, 2);
		assertThat(byDuration.stops()).extracting(OptimizedRoute.Stop::matrixIndex).containsExactly(2, 1);
		assertThat(byDuration.totalDurationS()).isEqualTo(30);
	}

	@Test
	void singleDestination() {
		var route = optimizer.optimize(matrix(new double[][] { { 0, 1234.6 }, { 1500.4, 0 } }), true,
				OptimizationMetric.DISTANCE);

		assertThat(route.stops()).singleElement().satisfies(s -> {
			assertThat(s.matrixIndex()).isEqualTo(1);
			assertThat(s.leg().distanceM()).isEqualTo(1235);
		});
		assertThat(route.returnLeg().distanceM()).isEqualTo(1500);
		assertThat(route.totalDistanceM()).isEqualTo(2735);
	}

	@Test
	void requiresAtLeastOneDestination() {
		assertThatThrownBy(() -> optimizer.optimize(matrix(new double[][] { { 0 } }), true, OptimizationMetric.DISTANCE))
			.isInstanceOf(IllegalArgumentException.class);
	}

	/** Durações derivadas das distâncias (0,1 s/m) quando o teste só se importa com a distância. */
	private static DistanceMatrix matrix(double[][] distances) {
		double[][] durations = new double[distances.length][];
		for (int i = 0; i < distances.length; i++) {
			durations[i] = new double[distances[i].length];
			for (int j = 0; j < distances[i].length; j++) {
				durations[i][j] = distances[i][j] / 10;
			}
		}
		return new DistanceMatrix(distances, durations);
	}

}
