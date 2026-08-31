package com.rafid.speechtotext;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

@SpringBootTest
@ActiveProfiles("default")
class DefaultProfileApplicationTests {

	/*
	 * Supplies a non-secret value only for constructing the Spring context. No
	 * OpenAI request is made by this test.
	 */
	@DynamicPropertySource
	static void provideRuntimeConfiguration(DynamicPropertyRegistry registry) {

		registry.add("OPENAI_API_KEY", () -> UUID.randomUUID().toString());
	}

	@Test
	void defaultProfileContextLoads() {
	}
}
