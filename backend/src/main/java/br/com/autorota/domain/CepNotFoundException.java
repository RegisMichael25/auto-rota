package br.com.autorota.domain;

public class CepNotFoundException extends DomainException {

	private final String cep;

	public CepNotFoundException(String cep) {
		super("CEP não encontrado: " + cep);
		this.cep = cep;
	}

	public String getCep() {
		return cep;
	}

}
