package br.com.autorota.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Coordenadas por consulta. {@code lat}/{@code lng} nulos registram "não encontrado". */
@Entity
@Table(name = "geocode_cache")
public class GeocodeCache {

	/** SHA-256 (hex) da consulta normalizada. */
	@Id
	@Column(name = "key_hash", length = 64)
	private String keyHash;

	@Column(name = "query_text", nullable = false, length = 500)
	private String queryText;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 20)
	private CoordSource provider;

	@Column(name = "lat")
	private Double lat;

	@Column(name = "lng")
	private Double lng;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected GeocodeCache() {
	}

	public GeocodeCache(String keyHash, String queryText, CoordSource provider, Double lat, Double lng) {
		this.keyHash = keyHash;
		this.queryText = queryText;
		this.provider = provider;
		this.lat = lat;
		this.lng = lng;
		this.createdAt = Instant.now();
	}

	public boolean isFound() {
		return lat != null && lng != null;
	}

	public String getKeyHash() {
		return keyHash;
	}

	public String getQueryText() {
		return queryText;
	}

	public CoordSource getProvider() {
		return provider;
	}

	public Double getLat() {
		return lat;
	}

	public Double getLng() {
		return lng;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
