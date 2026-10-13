package br.com.autorota.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import br.com.autorota.domain.RoutePlan;

public interface RoutePlanRepository extends JpaRepository<RoutePlan, UUID> {

	/** Plano com as paradas já carregadas (uma consulta, sem lazy loading fora da transação). */
	@EntityGraph(attributePaths = "stops")
	Optional<RoutePlan> findWithStopsById(UUID id);

}
