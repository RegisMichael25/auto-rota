package br.com.autorota.web.dto;

public record AddressResponse(String cep, String street, String number, String complement, String neighborhood,
		String city, String uf, double lat, double lng) {
}
