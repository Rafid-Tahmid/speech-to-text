package com.rafid.speechtotext;

import com.rafid.speechtotext.service.OpenAiTranscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiTranscriptionServiceTests {

	@Test
	void sendsAudioToOpenAiAndReturnsTranscribedText() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

		/*
		 * A random test value verifies the authorization header without placing a real
		 * or realistic API key in the repository.
		 */
		String testApiKey = UUID.randomUUID().toString();

		OpenAiTranscriptionService service = new OpenAiTranscriptionService(restClientBuilder, testApiKey);

		MockMultipartFile audio = new MockMultipartFile("audio", "recording.webm", "audio/webm",
				"fake audio contents".getBytes(StandardCharsets.UTF_8));

		mockServer.expect(requestTo("https://api.openai.com/v1/audio/transcriptions"))
				.andExpect(method(HttpMethod.POST)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + testApiKey))
				.andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
				.andExpect(content().string(containsString("name=\"file\"")))
				.andExpect(content().string(containsString("filename=\"recording.webm\"")))
				.andExpect(content().string(containsString("name=\"model\"")))
				.andExpect(content().string(containsString("gpt-4o-mini-transcribe"))).andRespond(withSuccess("""
						{
						  "text": "Expected transcription.",
						  "usage": {
						    "type": "tokens",
						    "input_tokens": 12,
						    "output_tokens": 3
						  }
						}
						""", MediaType.APPLICATION_JSON));

		String transcription = service.transcribe(audio);

		assertEquals("Expected transcription.", transcription);
		mockServer.verify();
	}

	@Test
	void rejectsMissingApiKey() {
		assertThrows(IllegalArgumentException.class, () -> new OpenAiTranscriptionService(RestClient.builder(), " "));
	}
}