package br.com.autorota.web.dto;

import java.time.Instant;

import br.com.autorota.domain.CoordSource;
import br.com.autorota.domain.StopStatus;

/**
 * @param visitOrder posição na ordem otimizada
 * @param inputOrder posição em que o destino foi informado
 * @param legDistanceM trecho do ponto anterior até esta parada
 */
public record StopResponse(int visitOrder, int inputOrder, AddressResponse address, CoordSource coordSource,
		int legDistanceM, int legDurationS, StopStatus status, Instant deliveredAt, String deliveryNote) {
}
