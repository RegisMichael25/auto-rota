package br.com.autorota.domain;

/** Base das exceções de negócio; a camada web as traduz para ProblemDetail. */
public abstract class DomainException extends RuntimeException {

	protected DomainException(String message) {
		super(message);
	}

	protected DomainException(String message, Throwable cause) {
		super(message, cause);
	}

}
