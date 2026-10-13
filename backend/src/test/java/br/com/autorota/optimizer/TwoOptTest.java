package br.com.autorota.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TwoOptTest {

	/** 5 pontos no plano (distância euclidiana × 10). NN circular custa 248; o ótimo, 245. */
	private static final double[][] ROUND_TRIP_CASE = {
			{ 0, 22, 72, 50, 45 },
			{ 22, 0, 94, 71, 30 },
			{ 72, 94, 0, 30, 113 },
			{ 50, 71, 30, 0, 94 },
			{ 45, 30, 113, 94, 0 } };

	/** 5 pontos no plano. NN aberto custa 174; o ótimo, 162. */
	private static final double[][] OPEN_CASE = {
			{ 0, 50, 22, 61, 82 },
			{ 50, 0, 45, 85, 106 },
			{ 22, 45, 0, 45, 67 },
			{ 61, 85, 45, 0, 22 },
			{ 82, 106, 67, 22, 0 } };

	@Test
	void improvesNearestNeighborToOptimumOnRoundTrip() {
		int[] nn = NearestNeighbor.order(ROUND_TRIP_CASE);
		assertThat(nn).containsExactly(0, 1, 4, 3, 2);
		assertThat(Tour.cost(nn, ROUND_TRIP_CASE, true)).isEqualTo(248);

		int[] improved = TwoOpt.improve(nn, ROUND_TRIP_CASE, true);

		assertThat(Tour.cost(improved, ROUND_TRIP_CASE, true)).isEqualTo(245)
			.isEqualTo(BruteForce.optimalCost(ROUND_TRIP_CASE, true));
		assertThat(improved[0]).isZero();
	}

	@Test
	void improvesNearestNeighborToOptimumOnOpenRoute() {
		int[] nn = NearestNeighbor.order(OPEN_CASE);
		assertThat(nn).containsExactly(0, 2, 1, 3, 4);
		assertThat(Tour.cost(nn, OPEN_CASE, false)).isEqualTo(174);

		int[] improved = TwoOpt.improve(nn, OPEN_CASE, false);

		assertThat(improved).containsExactly(0, 1, 2, 3, 4);
		assertThat(Tour.cost(improved, OPEN_CASE, false)).isEqualTo(162)
			.isEqualTo(BruteForce.optimalCost(OPEN_CASE, false));
	}

	@Test
	void openRouteCanChangeTheLastStop() {
		// Ponto 3 é o mais longe de todos: no modo aberto ele deve ficar por último.
		double[][] cost = {
				{ 0, 1, 2, 100 },
				{ 1, 0, 1, 99 },
				{ 2, 1, 0, 98 },
				{ 100, 99, 98, 0 } };

		int[] improved = TwoOpt.improve(new int[] { 0, 3, 1, 2 }, cost, false);

		assertThat(improved).containsExactly(0, 1, 2, 3);
	}

	@Test
	void keepsInputWhenAlreadyOptimal() {
		int[] optimal = { 0, 1, 2, 3, 4 };

		assertThat(TwoOpt.improve(optimal, OPEN_CASE, false)).containsExactly(optimal);
	}

	@Test
	void doesNotMutateInput() {
		int[] nn = NearestNeighbor.order(OPEN_CASE);
		int[] copy = nn.clone();

		TwoOpt.improve(nn, OPEN_CASE, false);

		assertThat(nn).containsExactly(copy);
	}

	/** Matrizes aleatórias assimétricas (como as do OSRM): o 2-opt nunca piora e nunca fica abaixo do ótimo. */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void neverWorseThanNearestNeighborAndNeverBelowOptimum(boolean roundTrip) {
		var random = new Random(42);
		for (int run = 0; run < 300; run++) {
			int n = 2 + random.nextInt(8);
			double[][] cost = randomAsymmetric(n, random);

			int[] nn = NearestNeighbor.order(cost);
			int[] improved = TwoOpt.improve(nn, cost, roundTrip);

			assertThat(Tour.cost(improved, cost, roundTrip)).isLessThanOrEqualTo(Tour.cost(nn, cost, roundTrip))
				.isGreaterThanOrEqualTo(BruteForce.optimalCost(cost, roundTrip) - 1e-9);
			assertThat(improved[0]).isZero();
			assertThat(Arrays.stream(improved).sorted().toArray()).containsExactly(range(n));
		}
	}

	private static double[][] randomAsymmetric(int n, Random random) {
		double[][] cost = new double[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				cost[i][j] = i == j ? 0 : 100 + random.nextInt(10_000);
			}
		}
		return cost;
	}

	private static int[] range(int n) {
		int[] values = new int[n];
		for (int i = 0; i < n; i++) {
			values[i] = i;
		}
		return values;
	}

}
