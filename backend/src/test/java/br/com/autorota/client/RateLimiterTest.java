package br.com.autorota.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

	@Test
	void concurrentCallersAreSpacedByTheInterval() throws Exception {
		var limiter = new RateLimiter(Duration.ofMillis(100));
		long start = System.nanoTime();

		try (var pool = Executors.newFixedThreadPool(4)) {
			var futures = IntStream.range(0, 4).mapToObj(i -> pool.submit(limiter::acquire)).toList();
			for (var future : futures) {
				future.get();
			}
		}

		// 4 chamadas: a primeira sai na hora, as outras 3 esperam 100 ms cada.
		assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isGreaterThanOrEqualTo(295);
	}

	@Test
	void zeroIntervalNeverWaits() {
		var limiter = new RateLimiter(Duration.ZERO);
		long start = System.nanoTime();

		for (int i = 0; i < 100; i++) {
			limiter.acquire();
		}

		assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isLessThan(50);
	}

}
