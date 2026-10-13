package br.com.autorota.optimizer;

/** Ótimo exato por enumeração de permutações; só para matrizes pequenas nos testes. */
final class BruteForce {

	private BruteForce() {
	}

	static double optimalCost(double[][] cost, boolean roundTrip) {
		int n = cost.length;
		int[] tour = new int[n];
		for (int i = 0; i < n; i++) {
			tour[i] = i;
		}
		return search(tour, 1, cost, roundTrip);
	}

	private static double search(int[] tour, int position, double[][] cost, boolean roundTrip) {
		if (position == tour.length) {
			return Tour.cost(tour, cost, roundTrip);
		}
		double best = Double.MAX_VALUE;
		for (int i = position; i < tour.length; i++) {
			swap(tour, position, i);
			best = Math.min(best, search(tour, position + 1, cost, roundTrip));
			swap(tour, position, i);
		}
		return best;
	}

	private static void swap(int[] a, int i, int j) {
		int tmp = a[i];
		a[i] = a[j];
		a[j] = tmp;
	}

}
