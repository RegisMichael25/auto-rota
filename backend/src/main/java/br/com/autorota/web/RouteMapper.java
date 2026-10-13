package br.com.autorota.web;

import br.com.autorota.client.CepAddress;
import br.com.autorota.domain.Address;
import br.com.autorota.domain.RoutePlan;
import br.com.autorota.domain.RouteStop;
import br.com.autorota.domain.StopStatus;
import br.com.autorota.service.PlanRouteCommand;
import br.com.autorota.web.dto.AddressRequest;
import br.com.autorota.web.dto.AddressResponse;
import br.com.autorota.web.dto.CepResponse;
import br.com.autorota.web.dto.PlanRouteRequest;
import br.com.autorota.web.dto.RoutePlanResponse;
import br.com.autorota.web.dto.RouteSummaryResponse;
import br.com.autorota.web.dto.StopResponse;

final class RouteMapper {

	private RouteMapper() {
	}

	static PlanRouteCommand toCommand(PlanRouteRequest request) {
		return new PlanRouteCommand(request.name(), toInput(request.origin()),
				request.destinations().stream().map(RouteMapper::toInput).toList(), request.roundTrip(),
				request.consumptionKmPerL(), request.fuelPricePerL());
	}

	/** Exige o plano com as paradas carregadas. */
	static RoutePlanResponse toResponse(RoutePlan plan) {
		return new RoutePlanResponse(plan.getId(), plan.getName(), plan.getCreatedAt(), plan.getStatus(),
				plan.isRoundTrip(), plan.getConsumptionKmPerL(), plan.getFuelPricePerL(), plan.getTotalDistanceM(),
				plan.getTotalDurationS(), plan.getFuelLiters(), plan.getFuelCost(), plan.getReturnDistanceM(),
				plan.getReturnDurationS(), toResponse(plan.getOrigin()),
				plan.getStops().stream().map(RouteMapper::toResponse).toList(), plan.getRouteGeometry());
	}

	/** Chamado dentro da transação da listagem. */
	static RouteSummaryResponse toSummary(RoutePlan plan) {
		var stops = plan.getStops();
		return new RouteSummaryResponse(plan.getId(), plan.getName(), plan.getCreatedAt(), plan.getStatus(),
				plan.isRoundTrip(), plan.getOrigin().getCity(), plan.getOrigin().getUf(), stops.size(),
				count(plan, StopStatus.ENTREGUE), count(plan, StopStatus.NAO_ENTREGUE), plan.getTotalDistanceM(),
				plan.getTotalDurationS(), plan.getFuelLiters(), plan.getFuelCost());
	}

	static CepResponse toResponse(CepAddress address) {
		return new CepResponse(address.cep(), address.street(), address.complement(), address.neighborhood(),
				address.city(), address.uf());
	}

	private static StopResponse toResponse(RouteStop stop) {
		return new StopResponse(stop.getVisitOrder(), stop.getInputOrder(), toResponse(stop.getAddress()),
				stop.getCoordSource(), stop.getLegDistanceM(), stop.getLegDurationS(), stop.getStatus(),
				stop.getDeliveredAt(), stop.getDeliveryNote());
	}

	private static AddressResponse toResponse(Address a) {
		return new AddressResponse(a.getCep(), a.getStreet(), a.getNumber(), a.getComplement(), a.getNeighborhood(),
				a.getCity(), a.getUf(), a.getLat(), a.getLng());
	}

	private static PlanRouteCommand.AddressInput toInput(AddressRequest request) {
		return new PlanRouteCommand.AddressInput(request.cep(), request.number());
	}

	private static int count(RoutePlan plan, StopStatus status) {
		return (int) plan.getStops().stream().filter(stop -> stop.getStatus() == status).count();
	}

}
