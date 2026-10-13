package br.com.autorota.client;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Garante um intervalo mínimo entre chamadas, entre threads. Cada chamada reserva o próximo horário livre sob
 * lock e dorme fora dele, então chamadas concorrentes saem em fila, espaçadas pelo intervalo.
 */
public class RateLimiter {

	private final long intervalNanos;

	private long nextSlotNanos = Long.MIN_VALUE;

	public RateLimiter(Duration minInterval) {
		this.intervalNanos = minInterval.toNanos();
	}

	public void acquire() {
		if (intervalNanos <= 0) {
			return;
		}
		long waitNanos;
		synchronized (this) {
			long now = System.nanoTime();
			long slot = (nextSlotNanos == Long.MIN_VALUE || nextSlotNanos - now < 0) ? now : nextSlotNanos;
			nextSlotNanos = slot + intervalNanos;
			waitNanos = slot - now;
		}
		if (waitNanos > 0) {
			try {
				TimeUnit.NANOSECONDS.sleep(waitNanos);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrompido aguardando o limite de requisições", ex);
			}
		}
	}

}
