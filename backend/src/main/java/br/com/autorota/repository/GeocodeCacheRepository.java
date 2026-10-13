package br.com.autorota.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.autorota.domain.GeocodeCache;

public interface GeocodeCacheRepository extends JpaRepository<GeocodeCache, String> {
}
