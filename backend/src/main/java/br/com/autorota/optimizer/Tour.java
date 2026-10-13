package br.com.autorota.optimizer;

/** Operações sobre um percurso: vetor de índices da matriz que começa sempre na origem (índice 0). */
final class Tour {

	private Tour() {
	}

	/** Custo do percurso; no modo circular inclui o trecho de volta do último nó até a origem. */
	static double cost(int[] tour, double[][] cost, boolean roundTrip) {
		double total = 0;
		for (int i = 0; i + 1 < tour.length; i++) {
			total += cost[tour[i]][tour[i + 1]];
		}
		if (roundTrip && tour.length > 1) {
			total += cost[tour[tour.length - 1]][0];
		}
		return total;
	}

}
