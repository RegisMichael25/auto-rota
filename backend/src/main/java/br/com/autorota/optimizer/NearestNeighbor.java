package br.com.autorota.optimizer;

/**
 * Heurística construtiva: parte da origem (índice 0) e vai sempre ao nó não visitado mais barato a partir do atual.
 * Empates ficam com o menor índice, para o resultado ser determinístico.
 */
public final class NearestNeighbor {

	private NearestNeighbor() {
	}

	public static int[] order(double[][] cost) {
		int n = cost.length;
		int[] tour = new int[n];
		boolean[] visited = new boolean[n];
		visited[0] = true;
		for (int position = 1; position < n; position++) {
			int current = tour[position - 1];
			int next = -1;
			for (int candidate = 1; candidate < n; candidate++) {
				if (!visited[candidate] && (next == -1 || cost[current][candidate] < cost[current][next])) {
					next = candidate;
				}
			}
			tour[position] = next;
			visited[next] = true;
		}
		return tour;
	}

}
