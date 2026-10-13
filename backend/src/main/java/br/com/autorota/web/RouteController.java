package br.com.autorota.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.autorota.service.RoutePlannerService;
import br.com.autorota.service.RouteTrackingService;
import br.com.autorota.web.dto.DeliveryRequest;
import br.com.autorota.web.dto.PageResponse;
import br.com.autorota.web.dto.PlanRouteRequest;
import br.com.autorota.web.dto.RoutePlanResponse;
import br.com.autorota.web.dto.RouteSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/routes")
@Tag(name = "Rotas", description = "Planejamento, histórico e marcação de entregas")
public class RouteController {

	private static final String VISIT_ORDER_RANGE = "Ordem de visita deve estar entre 1 e 10";

	private final RoutePlannerService planner;

	private final RouteTrackingService tracking;

	public RouteController(RoutePlannerService planner, RouteTrackingService tracking) {
		this.planner = planner;
		this.tracking = tracking;
	}

	@PostMapping("/plan")
	@Operation(summary = "Planeja uma rota",
			description = "Resolve os endereços, otimiza a ordem de visita, desenha a rota e estima o combustível.")
	public ResponseEntity<RoutePlanResponse> plan(@Valid @RequestBody PlanRouteRequest request) {
		var plan = planner.plan(RouteMapper.toCommand(request));
		return ResponseEntity.created(URI.create("/api/routes/" + plan.getId())).body(RouteMapper.toResponse(plan));
	}

	@GetMapping
	@Operation(summary = "Histórico de rotas", description = "Mais recentes primeiro. Período em datas locais, inclusivo.")
	public PageResponse<RouteSummaryResponse> list(
			@Parameter(example = "2026-10-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(example = "2026-10-31") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@Parameter(description = "Trecho do nome") @RequestParam(required = false) String name,
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "Página deve ser 0 ou maior") int page,
			@RequestParam(defaultValue = "20") @Min(value = 1, message = "Tamanho mínimo é 1") @Max(value = 100, message = "Tamanho máximo é 100") int size) {
		return PageResponse.of(tracking.list(from, to, name, page, size, RouteMapper::toSummary));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Detalhe da rota")
	public RoutePlanResponse get(@PathVariable UUID id) {
		return RouteMapper.toResponse(tracking.get(id));
	}

	@PostMapping("/{id}/stops/{visitOrder}/deliver")
	@Operation(summary = "Marca a parada como entregue")
	public RoutePlanResponse deliver(@PathVariable UUID id, @PathVariable @Min(value = 1, message = VISIT_ORDER_RANGE) @Max(value = 10, message = VISIT_ORDER_RANGE) int visitOrder,
			@Valid @RequestBody(required = false) DeliveryRequest request) {
		return RouteMapper.toResponse(tracking.markDelivered(id, visitOrder, note(request)));
	}

	@PostMapping("/{id}/stops/{visitOrder}/fail")
	@Operation(summary = "Marca a parada como não entregue")
	public RoutePlanResponse fail(@PathVariable UUID id, @PathVariable @Min(value = 1, message = VISIT_ORDER_RANGE) @Max(value = 10, message = VISIT_ORDER_RANGE) int visitOrder,
			@Valid @RequestBody(required = false) DeliveryRequest request) {
		return RouteMapper.toResponse(tracking.markNotDelivered(id, visitOrder, note(request)));
	}

	private static String note(DeliveryRequest request) {
		return request == null ? null : request.deliveryNote();
	}

}
