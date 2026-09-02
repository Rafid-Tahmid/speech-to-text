package com.rafid.speechtotext.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

@Service
public class ApplicationShutdownService {

	private static final Duration RESPONSE_HANDOFF_DELAY = Duration.ofMillis(200);

	private final ApplicationTerminator applicationTerminator;
	private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

	public ApplicationShutdownService(ApplicationTerminator applicationTerminator) {
		this.applicationTerminator = applicationTerminator;
	}

	public boolean requestShutdown() {
		if (!shutdownRequested.compareAndSet(false, true)) {
			return false;
		}

		Thread shutdownThread = new Thread(this::shutdownApplication, "application-shutdown");

		shutdownThread.setDaemon(false);
		shutdownThread.start();

		return true;
	}

	private void shutdownApplication() {
		try {
			Thread.sleep(RESPONSE_HANDOFF_DELAY.toMillis());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}

		applicationTerminator.terminate();
	}
}