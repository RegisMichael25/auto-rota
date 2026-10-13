package br.com.autorota.client;

/** Endereço retornado pelo ViaCEP (sem número e sem coordenadas). */
public record CepAddress(String cep, String street, String complement, String neighborhood, String city, String uf) {
}
