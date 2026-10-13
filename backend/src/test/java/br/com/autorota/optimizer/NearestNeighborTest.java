package br.com.autorota.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NearestNeighborTest {

	@Test
	void alwaysGoesToCheapestUnvisitedFromCurrent() {
		double[][] cost = {
				{ 0, 5, 1, 9 },
				{ 5, 0, 2, 1 },
				{ 1, 2, 0, 8 },
				{ 9, 1, 8, 0 } };

		assertThat(NearestNeighbor.order(cost)).containsExactly(0, 2, 1, 3);
	}

	@Test
	void tieGoesToLowestIndex() {
		double[][] cost = {
				{ 0, 3, 3 },
				{ 3, 0, 1 },
				{ 3, 1, 0 } };

		assertThat(NearestNeighbor.order(cost)).containsExactly(0, 1, 2);
	}

	@Test
	void usesDirectionOfAsymmetricMatrix() {
		// De 0: ir até 2 é barato (1), ir até 1 é caro (10), embora 1→0 seja barato.
		double[][] cost = {
				{ 0, 10, 1 },
				{ 1, 0, 1 },
				{ 10, 1, 0 } };

		assertThat(NearestNeighbor.order(cost)).containsExactly(0, 2, 1);
	}

	@Test
	void singleDestination() {
		assertThat(NearestNeighbor.order(new double[][] { { 0, 4 }, { 4, 0 } })).containsExactly(0, 1);
	}

}
