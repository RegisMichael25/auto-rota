package br.com.autorota.web.dto;

public record CepResponse(String cep, String street, String complement, String neighborhood, String city, String uf) {
}
