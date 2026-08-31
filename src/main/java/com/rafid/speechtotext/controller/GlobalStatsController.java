package com.rafid.speechtotext.controller;

import com.rafid.speechtotext.model.GlobalStatsResponse;
import com.rafid.speechtotext.service.GlobalStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/global/stats")
public class GlobalStatsController {

	private final GlobalStatsService globalStatsService;

	/*
	 * Spring supplies the shared statistics service through constructor injection.
	 */
	public GlobalStatsController(GlobalStatsService globalStatsService) {
		this.globalStatsService = globalStatsService;
	}

	/*
	 * Returns cumulative token usage since the current Java process started.
	 */
	@GetMapping
	public GlobalStatsResponse getGlobalStats() {
		return globalStatsService.getStats();
	}
}
