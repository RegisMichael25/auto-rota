package br.com.autorota.service;

import java.math.BigDecimal;

/**
 * @param distanceKm distância considerada no cálculo (inclui a volta no modo circular)
 * @param liters litros com 3 casas
 * @param cost custo em reais com 2 casas
 */
public record FuelEstimate(BigDecimal distanceKm, BigDecimal liters, BigDecimal cost) {
}
