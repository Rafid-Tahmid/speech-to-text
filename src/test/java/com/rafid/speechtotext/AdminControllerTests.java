package com.rafid.speechtotext;

import java.time.Instant;

import com.rafid.speechtotext.controller.AdminController;
import com.rafid.speechtotext.model.UptimeResponse;
import com.rafid.speechtotext.service.ApplicationShutdownService;
import com.rafid.speechtotext.service.ServerLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
class AdminControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ServerLifecycleService lifecycleService;

	@MockitoBean
	private ApplicationShutdownService shutdownService;

	@Test
	void uptimeReturnsRequiredInformation() throws Exception {
		when(lifecycleService.getUptime()).thenReturn(
				new UptimeResponse(Instant.parse("2026-09-02T00:00:00Z"), Instant.parse("2026-09-02T00:01:00Z"), 60.0));

		mockMvc.perform(get("/api/v1/admin/uptime")).andExpect(status().isOk())
				.andExpect(jsonPath("$.utcServerStart").isString()).andExpect(jsonPath("$.utcNow").isString())
				.andExpect(jsonPath("$.serverUptimeSeconds").isNumber());
	}

	@Test
	void firstShutdownRequestIsAccepted() throws Exception {
		when(shutdownService.requestShutdown()).thenReturn(true);

		mockMvc.perform(post("/api/v1/admin/shutdown")).andExpect(status().isAccepted())
				.andExpect(jsonPath("$.message").value("Graceful shutdown requested."));
	}

	@Test
	void duplicateShutdownRequestReturnsConflict() throws Exception {
		when(shutdownService.requestShutdown()).thenReturn(true, false);

		mockMvc.perform(post("/api/v1/admin/shutdown")).andExpect(status().isAccepted());

		mockMvc.perform(post("/api/v1/admin/shutdown")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.timestamp").isString()).andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.message").value("Graceful shutdown is already in progress."))
				.andExpect(jsonPath("$.path").value("/api/v1/admin/shutdown"));
	}
}