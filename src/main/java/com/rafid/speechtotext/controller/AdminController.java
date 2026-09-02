package com.rafid.speechtotext.controller;

import java.time.Instant;

import com.rafid.speechtotext.model.ErrorResponse;
import com.rafid.speechtotext.model.ShutdownResponse;
import com.rafid.speechtotext.model.UptimeResponse;
import com.rafid.speechtotext.service.ApplicationShutdownService;
import com.rafid.speechtotext.service.ServerLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

	private final ServerLifecycleService lifecycleService;
	private final ApplicationShutdownService shutdownService;

	public AdminController(ServerLifecycleService lifecycleService, ApplicationShutdownService shutdownService) {
		this.lifecycleService = lifecycleService;
		this.shutdownService = shutdownService;
	}

	@GetMapping("/uptime")
	public UptimeResponse getUptime() {
		return lifecycleService.getUptime();
	}

	@PostMapping("/shutdown")
	public ResponseEntity<?> shutdownServer() {
		if (shutdownService.requestShutdown()) {
			return ResponseEntity.accepted().body(new ShutdownResponse("Graceful shutdown requested."));
		}

		ErrorResponse errorResponse = new ErrorResponse(Instant.now(), HttpStatus.CONFLICT.value(), "Conflict",
				"Graceful shutdown is already in progress.", "/api/v1/admin/shutdown");

		return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
	}
}