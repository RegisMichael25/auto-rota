package br.com.autorota.web.dto;

import jakarta.validation.constraints.Size;

public record DeliveryRequest(
		@Size(max = 500, message = "Observação deve ter no máximo 500 caracteres") String deliveryNote) {
}
