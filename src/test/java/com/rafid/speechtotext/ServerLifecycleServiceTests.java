package com.rafid.speechtotext;

import java.time.Duration;
import java.time.Instant;

import com.rafid.speechtotext.model.UptimeResponse;
import com.rafid.speechtotext.service.ServerLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class ServerLifecycleServiceTests {

	@Test
	void uptimeStartsWhenWebServerIsInitialized() {
		ServerLifecycleService lifecycleService = new ServerLifecycleService();

		WebServer webServer = mock(WebServer.class);
		ServletWebServerApplicationContext applicationContext = mock(ServletWebServerApplicationContext.class);

		ServletWebServerInitializedEvent event = new ServletWebServerInitializedEvent(webServer, applicationContext);

		Instant expectedStart = Instant.ofEpochMilli(event.getTimestamp());

		lifecycleService.recordServerStart(event);

		UptimeResponse response = lifecycleService.getUptime();

		assertEquals(expectedStart, response.utcServerStart());
		assertFalse(response.utcNow().isBefore(response.utcServerStart()));

		Duration expectedUptime = Duration.between(response.utcServerStart(), response.utcNow());

		double expectedSeconds = expectedUptime.getSeconds() + expectedUptime.getNano() / 1_000_000_000.0;

		assertEquals(expectedSeconds, response.serverUptimeSeconds(), 0.000000001);
	}
}