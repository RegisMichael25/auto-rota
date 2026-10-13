package br.com.autorota.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.autorota.domain.CepCache;

public interface CepCacheRepository extends JpaRepository<CepCache, String> {
}
