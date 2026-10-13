package br.com.autorota.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.autorota.service.AddressResolutionService;
import br.com.autorota.web.dto.CepResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/cep")
@Tag(name = "CEP")
public class CepController {

	private final AddressResolutionService addresses;

	public CepController(AddressResolutionService addresses) {
		this.addresses = addresses;
	}

	@GetMapping("/{cep}")
	@Operation(summary = "Consulta endereço por CEP", description = "Usado para preencher o formulário. Usa cache.")
	public CepResponse lookup(@PathVariable String cep) {
		return RouteMapper.toResponse(addresses.lookupCep(cep));
	}

}
