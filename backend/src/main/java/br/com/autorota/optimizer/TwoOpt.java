package br.com.autorota.optimizer;

/**
 * Melhoria local 2-opt: inverte o trecho entre duas posições sempre que isso reduz o custo, até não haver melhora.
 * A origem (posição 0) nunca se move. No modo aberto a última posição também pode ser invertida, então o ponto final
 * é livre.
 * <p>
 * As matrizes do OSRM são assimétricas (ida ≠ volta), então o custo de cada candidato é recalculado inteiro em vez
 * de usar o delta simétrico clássico. Com no máximo 11 nós isso é barato. Só trocas que reduzem o custo são
 * aceitas, logo o resultado nunca é pior que a entrada.
 */
public final class TwoOpt {

	private static final double EPSILON = 1e-9;

	private TwoOpt() {
	}

	public static int[] improve(int[] initial, double[][] cost, boolean roundTrip) {
		int[] tour = initial.clone();
		double best = Tour.cost(tour, cost, roundTrip);
		boolean improved = true;
		while (improved) {
			improved = false;
			for (int i = 1; i < tour.length - 1; i++) {
				for (int k = i + 1; k < tour.length; k++) {
					int[] candidate = reversed(tour, i, k);
					double candidateCost = Tour.cost(candidate, cost, roundTrip);
					if (candidateCost < best - EPSILON) {
						tour = candidate;
						best = candidateCost;
						improved = true;
					}
				}
			}
		}
		return tour;
	}

	private static int[] reversed(int[] tour, int from, int to) {
		int[] copy = tour.clone();
		for (int i = from, j = to; i < j; i++, j--) {
			int tmp = copy[i];
			copy[i] = copy[j];
			copy[j] = tmp;
		}
		return copy;
	}

}
