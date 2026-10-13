package br.com.autorota.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.autorota.domain.RoutePlanStatus;

public record RouteSummaryResponse(UUID id, String name, Instant createdAt, RoutePlanStatus status,
		boolean roundTrip, String originCity, String originUf, int stopCount, int deliveredCount,
		int notDeliveredCount, int totalDistanceM, int totalDurationS, BigDecimal fuelLiters, BigDecimal fuelCost) {
}
