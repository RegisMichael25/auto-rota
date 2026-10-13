package br.com.autorota.domain;

public class AddressNotGeocodedException extends DomainException {

	public AddressNotGeocodedException(String cep, String number) {
		super("Não foi possível obter as coordenadas do endereço CEP " + cep + ", número " + number + ".");
	}

}
