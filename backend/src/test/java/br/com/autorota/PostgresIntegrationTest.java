package br.com.autorota;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/** Base dos testes de integração: um único PostgreSQL (Testcontainers) compartilhado por todas as classes. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

}
