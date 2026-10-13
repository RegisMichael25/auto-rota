package br.com.autorota.domain;

public class InvalidCepException extends DomainException {

	public InvalidCepException(String rawCep) {
		super("CEP inválido: '" + rawCep + "'. Informe 8 dígitos.");
	}

}
