package br.com.autorota.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.autorota.client.Coordinates;
import br.com.autorota.client.DistanceMatrix;
import br.com.autorota.client.OsrmClient;
import br.com.autorota.client.RouteResult;
import br.com.autorota.config.AppProperties;
import br.com.autorota.domain.Address;
import br.com.autorota.domain.ExternalServiceUnavailableException;
import br.com.autorota.domain.RoutePlan;
import br.com.autorota.domain.RouteStop;
import br.com.autorota.optimizer.OptimizedRoute;
import br.com.autorota.optimizer.RouteOptimizer;
import br.com.autorota.repository.RoutePlanRepository;

/**
 * Planeja uma rota: resolve endereços, monta a matriz no OSRM, otimiza a ordem, busca a geometria, estima o
 * combustível e grava plano + paradas.
 * <p>
 * A matriz do /table só decide a ordem. Distâncias, durações e combustível gravados vêm dos trechos do /route, para
 * baterem com a linha desenhada no mapa. As chamadas externas ficam fora da transação; só a gravação é transacional.
 */
@Service
public class RoutePlannerService {

	public static final int MAX_DESTINATIONS = 10;

	private final AddressResolutionService addresses;

	private final OsrmClient osrm;

	private final RouteOptimizer optimizer;

	private final FuelCalculator fuel;

	private final RoutePlanRepository routePlans;

	private final TransactionTemplate transaction;

	private final AppProperties properties;

	public RoutePlannerService(AddressResolutionService addresses, OsrmClient osrm, RouteOptimizer optimizer,
			FuelCalculator fuel, RoutePlanRepository routePlans, TransactionTemplate transaction,
			AppProperties properties) {
		this.addresses = addresses;
		this.osrm = osrm;
		this.optimizer = optimizer;
		this.fuel = fuel;
		this.routePlans = routePlans;
		this.transaction = transaction;
		this.properties = properties;
	}

	public RoutePlan plan(PlanRouteCommand command) {
		validate(command);
		boolean roundTrip = command.roundTrip() == null || command.roundTrip();
		BigDecimal consumption = orDefault(command.consumptionKmPerL(), properties.fuel().defaultConsumptionKmPerL());
		BigDecimal price = orDefault(command.fuelPricePerL(), properties.fuel().defaultPricePerL());

		ResolvedAddress origin = addresses.resolve(command.origin().cep(), command.origin().number());
		List<ResolvedAddress> destinations = new ArrayList<>();
		for (var input : command.destinations()) {
			destinations.add(addresses.resolve(input.cep(), input.number()));
		}

		DistanceMatrix matrix = osrm.table(coordinates(origin),
				destinations.stream().map(RoutePlannerService::coordinates).toList());
		OptimizedRoute optimized = optimizer.optimize(matrix, roundTrip, properties.optimizer().metric());

		RouteResult route = osrm.route(routeCoordinates(origin, destinations, optimized, roundTrip));
		int expectedLegs = optimized.stops().size() + (roundTrip ? 1 : 0);
		if (route.legs().size() != expectedLegs) {
			throw new ExternalServiceUnavailableException("OSRM", new IllegalStateException(
					"/route devolveu " + route.legs().size() + " trechos; esperado " + expectedLegs));
		}

		RoutePlan plan = buildPlan(command, roundTrip, consumption, price, origin, destinations, optimized, route);
		return transaction.execute(status -> routePlans.save(plan));
	}

	private RoutePlan buildPlan(PlanRouteCommand command, boolean roundTrip, BigDecimal consumption,
			BigDecimal price, ResolvedAddress origin, List<ResolvedAddress> destinations, OptimizedRoute optimized,
			RouteResult route) {
		var plan = new RoutePlan(blankToNull(command.name()), origin.address(), roundTrip, consumption, price);

		int routeDistanceM = 0;
		int routeDurationS = 0;
		for (OptimizedRoute.Stop stop : optimized.stops()) {
			RouteResult.Leg leg = route.legs().get(stop.visitOrder() - 1);
			int legDistance = round(leg.distanceM());
			int legDuration = round(leg.durationS());
			routeDistanceM += legDistance;
			routeDurationS += legDuration;

			// matrixIndex k (1..n) é o k-ésimo destino informado.
			ResolvedAddress destination = destinations.get(stop.matrixIndex() - 1);
			plan.addStop(new RouteStop(stop.matrixIndex(), stop.visitOrder(), destination.address(),
					destination.coordSource(), legDistance, legDuration));
		}

		Integer returnDistanceM = null;
		Integer returnDurationS = null;
		if (roundTrip) {
			RouteResult.Leg back = route.legs().get(route.legs().size() - 1);
			returnDistanceM = round(back.distanceM());
			returnDurationS = round(back.durationS());
		}

		FuelEstimate estimate = fuel.estimate(routeDistanceM, returnDistanceM, consumption, price);
		int totalDistanceM = routeDistanceM + (returnDistanceM == null ? 0 : returnDistanceM);
		int totalDurationS = routeDurationS + (returnDurationS == null ? 0 : returnDurationS);
		plan.applyTotals(totalDistanceM, totalDurationS, estimate.liters(), estimate.cost(), returnDistanceM,
				returnDurationS, route.geometryGeoJson());
		return plan;
	}

	/** Origem, paradas na ordem otimizada e, no modo circular, a origem de novo. */
	private static List<Coordinates> routeCoordinates(ResolvedAddress origin, List<ResolvedAddress> destinations,
			OptimizedRoute optimized, boolean roundTrip) {
		List<Coordinates> points = new ArrayList<>();
		points.add(coordinates(origin));
		for (OptimizedRoute.Stop stop : optimized.stops()) {
			points.add(coordinates(destinations.get(stop.matrixIndex() - 1)));
		}
		if (roundTrip) {
			points.add(coordinates(origin));
		}
		return points;
	}

	private static void validate(PlanRouteCommand command) {
		if (command == null || command.origin() == null) {
			throw new IllegalArgumentException("Origem é obrigatória");
		}
		var destinations = command.destinations();
		if (destinations == null || destinations.isEmpty() || destinations.size() > MAX_DESTINATIONS) {
			throw new IllegalArgumentException("Informe de 1 a " + MAX_DESTINATIONS + " destinos");
		}
		if (destinations.stream().anyMatch(d -> d == null)) {
			throw new IllegalArgumentException("Destino não pode ser nulo");
		}
	}

	private static Coordinates coordinates(ResolvedAddress resolved) {
		Address address = resolved.address();
		return new Coordinates(address.getLat(), address.getLng());
	}

	private static BigDecimal orDefault(BigDecimal value, BigDecimal fallback) {
		return value != null ? value : fallback;
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

	private static int round(double value) {
		return (int) Math.round(value);
	}

}
