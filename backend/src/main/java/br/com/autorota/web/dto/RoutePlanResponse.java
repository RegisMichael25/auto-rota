package br.com.autorota.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRawValue;

import br.com.autorota.domain.RoutePlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param routeGeometry LineString GeoJSON (coordenadas lng, lat)
 * @param returnDistanceM trecho de volta; {@code null} quando {@code roundTrip = false}
 */
public record RoutePlanResponse(UUID id, String name, Instant createdAt, RoutePlanStatus status, boolean roundTrip,
		BigDecimal consumptionKmPerL, BigDecimal fuelPricePerL, int totalDistanceM, int totalDurationS,
		BigDecimal fuelLiters, BigDecimal fuelCost, Integer returnDistanceM, Integer returnDurationS,
		AddressResponse origin, List<StopResponse> stops,
		@JsonRawValue @Schema(type = "object", description = "GeoJSON LineString") String routeGeometry) {
}
