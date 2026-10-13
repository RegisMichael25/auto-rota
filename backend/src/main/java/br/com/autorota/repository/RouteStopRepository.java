package br.com.autorota.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.autorota.domain.RouteStop;

public interface RouteStopRepository extends JpaRepository<RouteStop, UUID> {
}
