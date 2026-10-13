package br.com.autorota.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiExceptionHandlerTest {

	@Test
	void unexpectedErrorHidesInternalDetails() {
		var problem = new ApiExceptionHandler().unexpected(new IllegalStateException("senha=segredo; SQL quebrado"));

		assertThat(problem.getStatus()).isEqualTo(500);
		assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
		assertThat(problem.getDetail()).doesNotContain("segredo").doesNotContain("SQL");
	}

}
