package br.com.autorota.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class FuelCalculatorTest {

	private final FuelCalculator calculator = new FuelCalculator();

	@Test
	void openRouteUsesOnlyRouteDistance() {
		var estimate = calculator.estimate(20_000, null, new BigDecimal("10"), new BigDecimal("6.199"));

		assertThat(estimate.distanceKm()).isEqualByComparingTo("20");
		assertThat(estimate.liters()).isEqualTo(new BigDecimal("2.000"));
		assertThat(estimate.cost()).isEqualTo(new BigDecimal("12.40"));
	}

	@Test
	void roundTripAddsReturnDistance() {
		var estimate = calculator.estimate(20_000, 5_000, new BigDecimal("10"), new BigDecimal("6.199"));

		assertThat(estimate.distanceKm()).isEqualByComparingTo("25");
		assertThat(estimate.liters()).isEqualTo(new BigDecimal("2.500"));
		// 2,5 × 6,199 = 15,4975 → 15,50
		assertThat(estimate.cost()).isEqualTo(new BigDecimal("15.50"));
	}

	@Test
	void costUsesUnroundedLiters() {
		// 1,014 km / 8,7 = 0,11655... L. Custo exato 0,7435 → 0,74.
		// Arredondando os litros antes (0,117 × 6,379 = 0,7463) daria 0,75.
		var estimate = calculator.estimate(1_014, null, new BigDecimal("8.7"), new BigDecimal("6.379"));

		assertThat(estimate.liters()).isEqualTo(new BigDecimal("0.117"));
		assertThat(estimate.cost()).isEqualTo(new BigDecimal("0.74"));
	}

	@Test
	void moneyRoundsHalfUp() {
		// 1 L × 2,345 = 2,345 → 2,35 (HALF_EVEN daria 2,34)
		var estimate = calculator.estimate(10_000, null, new BigDecimal("10"), new BigDecimal("2.345"));

		assertThat(estimate.cost()).isEqualTo(new BigDecimal("2.35"));
	}

	@Test
	void zeroDistanceCostsNothing() {
		var estimate = calculator.estimate(0, null, BigDecimal.TEN, BigDecimal.ONE);

		assertThat(estimate.liters()).isEqualTo(new BigDecimal("0.000"));
		assertThat(estimate.cost()).isEqualTo(new BigDecimal("0.00"));
	}

	@Test
	void rejectsNonPositiveAssumptions() {
		assertThatThrownBy(() -> calculator.estimate(1000, null, BigDecimal.ZERO, BigDecimal.ONE))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> calculator.estimate(1000, null, BigDecimal.TEN, new BigDecimal("-1")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> calculator.estimate(1000, null, null, BigDecimal.ONE))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNegativeDistance() {
		assertThatThrownBy(() -> calculator.estimate(-1, null, BigDecimal.TEN, BigDecimal.ONE))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
