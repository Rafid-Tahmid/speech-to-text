package com.rafid.speechtotext;

import com.rafid.speechtotext.service.GlobalStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalStatsControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private GlobalStatsService globalStatsService;

	/*
	 * Verifies the exact initial response and confirms that recorded token usage
	 * appears in the next statistics snapshot.
	 */
	@Test
	void returnsInitialAndAccumulatedTokenUsage() throws Exception {
		mockMvc.perform(get("/api/v1/global/stats")).andExpect(status().isOk())
				.andExpect(jsonPath("$.inputTokens").value(0)).andExpect(jsonPath("$.outputTokens").value(0));

		globalStatsService.recordUsage(12, 3);

		mockMvc.perform(get("/api/v1/global/stats")).andExpect(status().isOk())
				.andExpect(jsonPath("$.inputTokens").value(12)).andExpect(jsonPath("$.outputTokens").value(3));
	}
}
