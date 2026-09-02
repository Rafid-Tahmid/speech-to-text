package com.rafid.speechtotext.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/*
 * Represents the JSON returned by OpenAI's transcription endpoint.
 * Unknown properties are ignored so additional API fields do not break parsing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiTranscriptionResponse(String text, Usage usage) {

	/*
	 * OpenAI uses snake_case JSON names, while Java uses camelCase names.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Usage(String type, @JsonProperty("input_tokens") long inputTokens,
			@JsonProperty("output_tokens") long outputTokens) {
	}
}