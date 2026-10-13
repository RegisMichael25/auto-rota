package br.com.autorota;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Base dos testes de integração: um único PostgreSQL (Testcontainers) e um único WireMock, compartilhados por todas
 * as classes. As APIs externas apontam para o WireMock, cada uma com um prefixo de caminho.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	protected static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

	static {
		POSTGRES.start();
		WIREMOCK.start();
	}

	@DynamicPropertySource
	static void externalServices(DynamicPropertyRegistry registry) {
		registry.add("app.external.viacep.base-url", () -> WIREMOCK.baseUrl() + "/viacep/ws");
		registry.add("app.external.brasilapi.base-url", () -> WIREMOCK.baseUrl() + "/brasilapi/api/cep/v2");
		registry.add("app.external.nominatim.base-url", () -> WIREMOCK.baseUrl() + "/nominatim");
		registry.add("app.external.osrm.base-url", () -> WIREMOCK.baseUrl() + "/osrm");
	}

	@BeforeEach
	void resetWireMock() {
		WIREMOCK.resetAll();
	}

}
