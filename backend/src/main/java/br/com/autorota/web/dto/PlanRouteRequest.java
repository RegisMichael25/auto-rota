package br.com.autorota.web.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Consumo e preço seguem a precisão do banco (2 e 3 casas) para não serem arredondados sem aviso. */
public record PlanRouteRequest(
		@Schema(example = "Entregas da manhã")
		@Size(max = 120, message = "Nome deve ter no máximo 120 caracteres") String name,

		@NotNull(message = "Origem é obrigatória") @Valid AddressRequest origin,

		@NotNull(message = "Destinos são obrigatórios")
		@Size(min = 1, max = 10, message = "Informe de 1 a 10 destinos")
		List<@NotNull(message = "Destino não pode ser nulo") @Valid AddressRequest> destinations,

		@Schema(description = "Retornar à origem. Padrão: true", defaultValue = "true") Boolean roundTrip,

		@Schema(example = "10.5", description = "km/l. Padrão: app.fuel.default-consumption-km-per-l")
		@Positive(message = "Consumo deve ser maior que zero")
		@Digits(integer = 4, fraction = 2, message = "Consumo aceita até 4 dígitos inteiros e 2 decimais")
		BigDecimal consumptionKmPerL,

		@Schema(example = "6.199", description = "R$/l. Padrão: app.fuel.default-price-per-l")
		@Positive(message = "Preço deve ser maior que zero")
		@Digits(integer = 5, fraction = 3, message = "Preço aceita até 5 dígitos inteiros e 3 decimais")
		BigDecimal fuelPricePerL) {
}
