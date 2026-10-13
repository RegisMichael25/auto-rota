package br.com.autorota.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * {@code litros = distância_km / consumo_km_por_litro}; {@code custo = litros × preço_por_litro}.
 * <p>
 * Tudo em {@link BigDecimal}. O custo é calculado sobre os litros sem arredondar e só então arredondado para
 * centavos (HALF_UP), evitando arredondamento duplo. Litros são arredondados para 3 casas só na saída.
 */
@Component
public class FuelCalculator {

	private static final BigDecimal METERS_PER_KM = BigDecimal.valueOf(1000);

	private static final int LITERS_SCALE = 3;

	private static final int MONEY_SCALE = 2;

	/**
	 * @param routeDistanceM distância da origem até a última parada
	 * @param returnDistanceM trecho de volta à origem; {@code null} no modo aberto
	 */
	public FuelEstimate estimate(int routeDistanceM, Integer returnDistanceM, BigDecimal consumptionKmPerL,
			BigDecimal pricePerL) {
		if (routeDistanceM < 0 || (returnDistanceM != null && returnDistanceM < 0)) {
			throw new IllegalArgumentException("Distância não pode ser negativa");
		}
		requirePositive(consumptionKmPerL, "Consumo (km/l)");
		requirePositive(pricePerL, "Preço por litro");

		long totalM = (long) routeDistanceM + (returnDistanceM == null ? 0 : returnDistanceM);
		BigDecimal km = BigDecimal.valueOf(totalM).divide(METERS_PER_KM);
		BigDecimal exactLiters = km.divide(consumptionKmPerL, MathContext.DECIMAL128);
		BigDecimal cost = exactLiters.multiply(pricePerL).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		return new FuelEstimate(km, exactLiters.setScale(LITERS_SCALE, RoundingMode.HALF_UP), cost);
	}

	private static void requirePositive(BigDecimal value, String name) {
		if (value == null || value.signum() <= 0) {
			throw new IllegalArgumentException(name + " deve ser maior que zero");
		}
	}

}
