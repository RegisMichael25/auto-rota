package br.com.autorota.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
		@Schema(example = "01310-100", description = "8 dígitos, com ou sem hífen")
		@NotBlank(message = "CEP é obrigatório")
		@Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP deve ter 8 dígitos") String cep,

		@Schema(example = "1000")
		@NotBlank(message = "Número é obrigatório")
		@Size(max = 20, message = "Número deve ter no máximo 20 caracteres") String number) {
}
