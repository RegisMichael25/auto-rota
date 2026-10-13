package br.com.autorota.client;

/**
 * Matrizes quadradas do OSRM /table. Índice 0 é a origem; os índices 1..n seguem a ordem dos destinos informados.
 *
 * @param distancesM distâncias em metros, {@code [de][para]}
 * @param durationsS durações em segundos, {@code [de][para]}
 */
public record DistanceMatrix(double[][] distancesM, double[][] durationsS) {

	public int size() {
		return distancesM.length;
	}

}
