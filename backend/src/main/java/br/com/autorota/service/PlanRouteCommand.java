package br.com.autorota.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pedido de planejamento. Campos opcionais nulos assumem o padrão: {@code roundTrip = true} e consumo/preço de
 * {@code app.fuel.*}.
 */
public record PlanRouteCommand(String name, AddressInput origin, List<AddressInput> destinations, Boolean roundTrip,
		BigDecimal consumptionKmPerL, BigDecimal fuelPricePerL) {

	public record AddressInput(String cep, String number) {
	}

}
