package br.com.autorota.optimizer;

import java.util.List;

/**
 * Resultado da otimização.
 *
 * @param stops paradas na ordem de visita, cada uma com o trecho que chega até ela
 * @param returnLeg trecho da última parada de volta à origem; {@code null} no modo aberto
 */
public record OptimizedRoute(List<Stop> stops, Leg returnLeg, int totalDistanceM, int totalDurationS) {

	/**
	 * @param matrixIndex índice do destino na matriz (1..n, na ordem em que foi informado)
	 * @param visitOrder posição na ordem de visita (1..n)
	 * @param leg trecho do ponto anterior (origem ou parada anterior) até esta parada
	 */
	public record Stop(int matrixIndex, int visitOrder, Leg leg) {
	}

	public record Leg(int distanceM, int durationS) {
	}

}
