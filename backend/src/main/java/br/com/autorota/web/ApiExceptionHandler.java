package br.com.autorota.web;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import br.com.autorota.domain.AddressNotGeocodedException;
import br.com.autorota.domain.CepNotFoundException;
import br.com.autorota.domain.ExternalServiceUnavailableException;
import br.com.autorota.domain.InvalidCepException;
import br.com.autorota.domain.ResourceNotFoundException;
import br.com.autorota.domain.RouteNotFoundException;

/**
 * Erros no formato ProblemDetail (RFC 7807). Cada resposta traz {@code code} estável para o frontend; validação
 * traz também {@code errors} com campo e mensagem.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(InvalidCepException.class)
	ProblemDetail invalidCep(InvalidCepException ex) {
		return problem(HttpStatus.BAD_REQUEST, "CEP inválido", ex.getMessage(), "INVALID_CEP");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail illegalArgument(IllegalArgumentException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Requisição inválida", ex.getMessage(), "INVALID_REQUEST");
	}

	@ExceptionHandler(CepNotFoundException.class)
	ProblemDetail cepNotFound(CepNotFoundException ex) {
		var problem = problem(HttpStatus.NOT_FOUND, "CEP não encontrado", ex.getMessage(), "CEP_NOT_FOUND");
		problem.setProperty("cep", ex.getCep());
		return problem;
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail notFound(ResourceNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Não encontrado", ex.getMessage(), "NOT_FOUND");
	}

	@ExceptionHandler(AddressNotGeocodedException.class)
	ProblemDetail notGeocoded(AddressNotGeocodedException ex) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Endereço sem coordenadas", ex.getMessage(),
				"ADDRESS_NOT_GEOCODED");
	}

	@ExceptionHandler(RouteNotFoundException.class)
	ProblemDetail routeNotFound(RouteNotFoundException ex) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Rota impossível", ex.getMessage(), "ROUTE_NOT_FOUND");
	}

	@ExceptionHandler(ExternalServiceUnavailableException.class)
	ProblemDetail unavailable(ExternalServiceUnavailableException ex) {
		log.warn("Serviço externo indisponível: {}", ex.getService(), ex);
		var problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "Serviço externo indisponível",
				ex.getService() + " não respondeu corretamente. Tente novamente em instantes.",
				"EXTERNAL_SERVICE_UNAVAILABLE");
		problem.setProperty("service", ex.getService());
		return problem;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(ApiExceptionHandler::fieldError)
			.toList();
		var problem = validationProblem(errors);
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// Corpo (@Valid) chega como ParameterErrors, com erros por campo; @PathVariable/@RequestParam, por parâmetro.
		List<Map<String, String>> errors = ex.getParameterValidationResults().stream().flatMap(result -> {
			if (result instanceof ParameterErrors bodyErrors) {
				return bodyErrors.getFieldErrors().stream().map(ApiExceptionHandler::fieldError);
			}
			String parameter = result.getMethodParameter().getParameterName();
			return result.getResolvableErrors()
				.stream()
				.map(error -> Map.of("field", parameter, "message", String.valueOf(error.getDefaultMessage())));
		}).toList();
		var problem = validationProblem(errors);
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		// Erros do Spring MVC (JSON malformado, tipo inválido...) também ganham "code".
		if (body instanceof ProblemDetail problem && (problem.getProperties() == null
				|| !problem.getProperties().containsKey("code"))) {
			problem.setProperty("code", statusCode.is4xxClientError() ? "INVALID_REQUEST" : "INTERNAL_ERROR");
		}
		return super.handleExceptionInternal(ex, body, headers, statusCode, request);
	}

	private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
		var problem = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "Corrija os campos indicados.",
				"VALIDATION_ERROR");
		problem.setProperty("errors", errors);
		return problem;
	}

	private static Map<String, String> fieldError(FieldError error) {
		return Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage()));
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}

}
