package br.com.autorota.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import br.com.autorota.domain.RoutePlan;

public interface RoutePlanRepository extends JpaRepository<RoutePlan, UUID>, JpaSpecificationExecutor<RoutePlan> {

	/** Plano com as paradas já carregadas (uma consulta, sem lazy loading fora da transação). */
	@EntityGraph(attributePaths = "stops")
	Optional<RoutePlan> findWithStopsById(UUID id);

	/** Criados a partir de {@code from} (inclusive). {@code null} não filtra. */
	static Specification<RoutePlan> createdFrom(Instant from) {
		return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
	}

	/** Criados antes de {@code to} (exclusivo). {@code null} não filtra. */
	static Specification<RoutePlan> createdBefore(Instant to) {
		return (root, query, cb) -> to == null ? null : cb.lessThan(root.get("createdAt"), to);
	}

	/** Nome contém o texto, sem diferenciar maiúsculas. {@code null} ou vazio não filtra. */
	static Specification<RoutePlan> nameContains(String text) {
		return (root, query, cb) -> {
			if (text == null || text.isBlank()) {
				return null;
			}
			String escaped = text.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
			return cb.like(cb.lower(root.get("name")), "%" + escaped + "%", '\\');
		};
	}

}
