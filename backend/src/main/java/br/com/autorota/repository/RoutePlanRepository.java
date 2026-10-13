package br.com.autorota.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.autorota.domain.RoutePlan;

public interface RoutePlanRepository extends JpaRepository<RoutePlan, UUID> {
}
