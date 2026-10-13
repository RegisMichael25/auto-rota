package br.com.autorota.optimizer;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.autorota.client.DistanceMatrix;

/**
 * Define a ordem de visita: vizinho mais próximo seguido de 2-opt sobre a matriz da métrica escolhida. A origem é o
 * índice 0 e sempre o início. Circular: o custo inclui a volta à origem. Aberta: termina na última parada, sem volta.
 */
@Component
public class RouteOptimizer {

	public OptimizedRoute optimize(DistanceMatrix matrix, boolean roundTrip, OptimizationMetric metric) {
		if (matrix.size() < 2) {
			throw new IllegalArgumentException("A matriz precisa da origem e de ao menos um destino");
		}
		double[][] cost = metric == OptimizationMetric.DURATION ? matrix.durationsS() : matrix.distancesM();

		int[] tour = TwoOpt.improve(NearestNeighbor.order(cost), cost, roundTrip);
		return toResult(tour, matrix, roundTrip);
	}

	private static OptimizedRoute toResult(int[] tour, DistanceMatrix matrix, boolean roundTrip) {
		List<OptimizedRoute.Stop> stops = new ArrayList<>(tour.length - 1);
		int totalDistance = 0;
		int totalDuration = 0;
		for (int position = 1; position < tour.length; position++) {
			var leg = leg(matrix, tour[position - 1], tour[position]);
			stops.add(new OptimizedRoute.Stop(tour[position], position, leg));
			totalDistance += leg.distanceM();
			totalDuration += leg.durationS();
		}

		OptimizedRoute.Leg returnLeg = null;
		if (roundTrip) {
			returnLeg = leg(matrix, tour[tour.length - 1], 0);
			totalDistance += returnLeg.distanceM();
			totalDuration += returnLeg.durationS();
		}
		return new OptimizedRoute(List.copyOf(stops), returnLeg, totalDistance, totalDuration);
	}

	/** Metros e segundos inteiros, como gravados no banco. */
	private static OptimizedRoute.Leg leg(DistanceMatrix matrix, int from, int to) {
		return new OptimizedRoute.Leg((int) Math.round(matrix.distancesM()[from][to]),
				(int) Math.round(matrix.durationsS()[from][to]));
	}

}
