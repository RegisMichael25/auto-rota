package br.com.autorota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Endereço brasileiro com coordenadas. Usado na origem do plano e em cada parada. */
@Embeddable
public class Address {

	@Column(name = "cep", nullable = false, length = 8)
	private String cep;

	@Column(name = "street", length = 200)
	private String street;

	@Column(name = "number", nullable = false, length = 20)
	private String number;

	@Column(name = "complement", length = 100)
	private String complement;

	@Column(name = "neighborhood", length = 100)
	private String neighborhood;

	@Column(name = "city", nullable = false, length = 100)
	private String city;

	@Column(name = "uf", nullable = false, length = 2)
	private String uf;

	@Column(name = "lat", nullable = false)
	private double lat;

	@Column(name = "lng", nullable = false)
	private double lng;

	protected Address() {
	}

	public Address(String cep, String street, String number, String complement, String neighborhood, String city,
			String uf, double lat, double lng) {
		this.cep = cep;
		this.street = street;
		this.number = number;
		this.complement = complement;
		this.neighborhood = neighborhood;
		this.city = city;
		this.uf = uf;
		this.lat = lat;
		this.lng = lng;
	}

	public String getCep() {
		return cep;
	}

	public String getStreet() {
		return street;
	}

	public String getNumber() {
		return number;
	}

	public String getComplement() {
		return complement;
	}

	public String getNeighborhood() {
		return neighborhood;
	}

	public String getCity() {
		return city;
	}

	public String getUf() {
		return uf;
	}

	public double getLat() {
		return lat;
	}

	public double getLng() {
		return lng;
	}

}
