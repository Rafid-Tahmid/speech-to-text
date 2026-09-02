package com.rafid.speechtotext.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import com.rafid.speechtotext.model.UptimeResponse;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ServerLifecycleService {

	private final AtomicReference<Instant> serverStart = new AtomicReference<>();

	/*
	 * Spring publishes this event after the embedded web server is ready. The event
	 * timestamp therefore represents the server's usable start time.
	 */
	@EventListener
	public void recordServerStart(WebServerInitializedEvent event) {
		Instant readyTime = Instant.ofEpochMilli(event.getTimestamp());

		serverStart.compareAndSet(null, readyTime);
	}

	public UptimeResponse getUptime() {
		Instant now = Instant.now();

		/*
		 * The fallback handles the extremely small possibility that an uptime request
		 * arrives before the initialization event is processed.
		 */
		Instant start = serverStart.updateAndGet(existingStart -> existingStart == null ? now : existingStart);

		Duration uptime = Duration.between(start, now);

		double uptimeSeconds = uptime.getSeconds() + uptime.getNano() / 1_000_000_000.0;

		return new UptimeResponse(start, now, uptimeSeconds);
	}
}