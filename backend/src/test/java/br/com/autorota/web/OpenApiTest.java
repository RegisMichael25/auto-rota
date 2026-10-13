package br.com.autorota.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import br.com.autorota.PostgresIntegrationTest;

@AutoConfigureMockMvc
class OpenApiTest extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void apiDocsListAllEndpoints() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("Auto Rota API"))
			.andExpect(jsonPath("$.paths['/api/routes/plan'].post").exists())
			.andExpect(jsonPath("$.paths['/api/routes'].get").exists())
			.andExpect(jsonPath("$.paths['/api/routes/{id}'].get").exists())
			.andExpect(jsonPath("$.paths['/api/routes/{id}/stops/{visitOrder}/deliver'].post").exists())
			.andExpect(jsonPath("$.paths['/api/routes/{id}/stops/{visitOrder}/fail'].post").exists())
			.andExpect(jsonPath("$.paths['/api/cep/{cep}'].get").exists());
	}

	@Test
	void swaggerUiIsServed() throws Exception {
		mvc.perform(get("/swagger-ui/index.html"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("swagger-ui")));
	}

}
