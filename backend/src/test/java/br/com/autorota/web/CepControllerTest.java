package br.com.autorota.web;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import br.com.autorota.PostgresIntegrationTest;
import br.com.autorota.repository.CepCacheRepository;

@AutoConfigureMockMvc
class CepControllerTest extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CepCacheRepository cache;

	@BeforeEach
	void clearCache() {
		cache.deleteAll();
	}

	@Test
	void returnsAddress() throws Exception {
		WIREMOCK.stubFor(get(urlPathEqualTo("/viacep/ws/01310100/json/")).willReturn(okJson("""
				{"cep": "01310-100", "logradouro": "Avenida Paulista", "complemento": "de 612 a 1510 - lado par",
				 "bairro": "Bela Vista", "localidade": "São Paulo", "uf": "SP"}
				""")));

		mvc.perform(MockMvcRequestBuilders.get("/api/cep/01310-100"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cep").value("01310100"))
			.andExpect(jsonPath("$.street").value("Avenida Paulista"))
			.andExpect(jsonPath("$.city").value("São Paulo"))
			.andExpect(jsonPath("$.uf").value("SP"));
	}

	@Test
	void invalidCepIsBadRequest() throws Exception {
		mvc.perform(MockMvcRequestBuilders.get("/api/cep/123"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CEP"));
	}

	@Test
	void unknownCepIsNotFound() throws Exception {
		WIREMOCK.stubFor(get(urlPathEqualTo("/viacep/ws/99999999/json/")).willReturn(okJson("{\"erro\": \"true\"}")));

		mvc.perform(MockMvcRequestBuilders.get("/api/cep/99999999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CEP_NOT_FOUND"))
			.andExpect(jsonPath("$.title").value("CEP não encontrado"));
	}

	@Test
	void viaCepDownIsServiceUnavailable() throws Exception {
		WIREMOCK.stubFor(get(urlPathEqualTo("/viacep/ws/01310100/json/")).willReturn(serviceUnavailable()));

		mvc.perform(MockMvcRequestBuilders.get("/api/cep/01310100"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.service").value("ViaCEP"));
	}

}
