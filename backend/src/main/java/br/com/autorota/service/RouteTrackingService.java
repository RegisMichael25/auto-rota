package br.com.autorota.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.autorota.domain.ResourceNotFoundException;
import br.com.autorota.domain.RoutePlan;
import br.com.autorota.domain.RouteStop;
import br.com.autorota.repository.RoutePlanRepository;

/** Histórico de rotas e marcação manual de entregas. */
@Service
public class RouteTrackingService {

	private final RoutePlanRepository routePlans;

	private final ZoneId zone;

	public RouteTrackingService(RoutePlanRepository routePlans,
			@Value("${app.time-zone:America/Sao_Paulo}") String timeZone) {
		this.routePlans = routePlans;
		this.zone = ZoneId.of(timeZone);
	}

	/**
	 * Lista paginada, mais recentes primeiro. O período usa datas locais ({@code app.time-zone}), ambas inclusivas.
	 * O mapeamento roda dentro da transação, então pode ler as paradas.
	 */
	@Transactional(readOnly = true)
	public <T> Page<T> list(LocalDate from, LocalDate to, String name, int page, int size,
			Function<RoutePlan, T> mapper) {
		if (from != null && to != null && to.isBefore(from)) {
			throw new IllegalArgumentException("Data final anterior à inicial");
		}
		Instant start = from == null ? null : from.atStartOfDay(zone).toInstant();
		Instant end = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
		Specification<RoutePlan> filter = Specification.allOf(RoutePlanRepository.createdFrom(start),
				RoutePlanRepository.createdBefore(end), RoutePlanRepository.nameContains(name));
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		return routePlans.findAll(filter, pageable).map(mapper);
	}

	@Transactional(readOnly = true)
	public RoutePlan get(UUID id) {
		return routePlans.findWithStopsById(id).orElseThrow(() -> notFound(id));
	}

	@Transactional
	public RoutePlan markDelivered(UUID id, int visitOrder, String note) {
		RoutePlan plan = lockForUpdate(id);
		stop(plan, visitOrder).markDelivered(Instant.now(), blankToNull(note));
		plan.refreshStatus();
		return plan;
	}

	@Transactional
	public RoutePlan markNotDelivered(UUID id, int visitOrder, String note) {
		RoutePlan plan = lockForUpdate(id);
		stop(plan, visitOrder).markNotDelivered(blankToNull(note));
		plan.refreshStatus();
		return plan;
	}

	private RoutePlan lockForUpdate(UUID id) {
		return routePlans.findForUpdateById(id).orElseThrow(() -> notFound(id));
	}

	private static RouteStop stop(RoutePlan plan, int visitOrder) {
		return plan.findStop(visitOrder)
			.orElseThrow(() -> new ResourceNotFoundException(
					"Parada " + visitOrder + " não existe na rota " + plan.getId() + "."));
	}

	private static ResourceNotFoundException notFound(UUID id) {
		return new ResourceNotFoundException("Rota " + id + " não encontrada.");
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

}
