package com.rafid.speechtotext.service;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ApplicationTerminator {

	private final ConfigurableApplicationContext applicationContext;

	public ApplicationTerminator(ConfigurableApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	public void terminate() {
		int exitCode = SpringApplication.exit(applicationContext, () -> 0);

		System.exit(exitCode);
	}
}