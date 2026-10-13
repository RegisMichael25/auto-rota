package br.com.autorota.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Resposta do ViaCEP por CEP. {@code found = false} registra CEP inexistente. */
@Entity
@Table(name = "cep_cache")
public class CepCache {

	/** SHA-256 (hex) do CEP normalizado. */
	@Id
	@Column(name = "key_hash", length = 64)
	private String keyHash;

	@Column(name = "cep", nullable = false, length = 8)
	private String cep;

	@Column(name = "found", nullable = false)
	private boolean found;

	@Column(name = "street", length = 200)
	private String street;

	@Column(name = "complement", length = 100)
	private String complement;

	@Column(name = "neighborhood", length = 100)
	private String neighborhood;

	@Column(name = "city", length = 100)
	private String city;

	@Column(name = "uf", length = 2)
	private String uf;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected CepCache() {
	}

	public CepCache(String keyHash, String cep, boolean found, String street, String complement,
			String neighborhood, String city, String uf) {
		this.keyHash = keyHash;
		this.cep = cep;
		this.found = found;
		this.street = street;
		this.complement = complement;
		this.neighborhood = neighborhood;
		this.city = city;
		this.uf = uf;
		this.createdAt = Instant.now();
	}

	public static CepCache notFound(String keyHash, String cep) {
		return new CepCache(keyHash, cep, false, null, null, null, null, null);
	}

	public String getKeyHash() {
		return keyHash;
	}

	public String getCep() {
		return cep;
	}

	public boolean isFound() {
		return found;
	}

	public String getStreet() {
		return street;
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

	public Instant getCreatedAt() {
		return createdAt;
	}

}
