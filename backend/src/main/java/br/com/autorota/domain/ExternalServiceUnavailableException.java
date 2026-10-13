package br.com.autorota.domain;

public class ExternalServiceUnavailableException extends DomainException {

	private final String service;

	public ExternalServiceUnavailableException(String service, Throwable cause) {
		super("Serviço externo indisponível: " + service, cause);
		this.service = service;
	}

	public String getService() {
		return service;
	}

}
