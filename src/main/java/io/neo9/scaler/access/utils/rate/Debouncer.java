package io.neo9.scaler.access.utils.rate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Debouncer {

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	private final ConcurrentHashMap<Object, Future<?>> delayedMap = new ConcurrentHashMap<>();

	/**
	 * Debounces {@code callable} by {@code delay}, i.e., schedules it to be executed after {@code delay},
	 * or cancels its execution if the method is called with the same key within the {@code delay} again.
	 */
	public void debounce(final Object key, final Runnable runnable, long delay, TimeUnit unit) {
		final Future<?> prev = delayedMap.put(key, scheduler.schedule(() -> {
			try {
				runnable.run();
			}
			finally {
				delayedMap.remove(key);
			}
		}, delay, unit));
		if (prev != null) {
			prev.cancel(true);
		}
	}

	/**
	 * debounce with default delay of 1 second
	 */
	public void debounce(final Object key, final Runnable runnable) {
		this.debounce(key, runnable, 1, TimeUnit.SECONDS);
	}

	public void shutdown() {
		scheduler.shutdownNow();
	}
}

