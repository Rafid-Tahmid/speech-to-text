package com.rafid.speechtotext.service;

import java.time.Duration;

import com.rafid.speechtotext.model.OpenAiTranscriptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("!local")
public class OpenAiTranscriptionService implements TranscriptionService {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiTranscriptionService.class);

	private static final String OPENAI_BASE_URL = "https://api.openai.com";
	private static final String TRANSCRIPTION_PATH = "/v1/audio/transcriptions";
	private static final String MODEL = "gpt-4o-mini-transcribe";

	private final RestClient restClient;

	public OpenAiTranscriptionService(
			RestClient.Builder restClientBuilder,
			@Value("${OPENAI_API_KEY}") String apiKey) {

		/*
		 * Fail safely if TITAN does not supply the required environment value. The key
		 * itself must never appear in this message or in application logs.
		 */
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException("OPENAI_API_KEY is required for OpenAI transcription");
		}

		this.restClient = restClientBuilder.baseUrl(OPENAI_BASE_URL)
				.defaultHeaders(headers -> headers.setBearerAuth(apiKey)).build();
	}

	@Override
	public String transcribe(MultipartFile audio) {
		MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();

		HttpHeaders fileHeaders = new HttpHeaders();
		fileHeaders.setContentType(resolveContentType(audio));

		/*
		 * OpenAI calls the uploaded part "file", whereas our browser-facing controller
		 * calls its multipart field "audio".
		 */
		requestBody.add("file", new HttpEntity<>(audio.getResource(), fileHeaders));
		requestBody.add("model", MODEL);

		long startedAt = System.nanoTime();

		/*
		 * Log only safe request metadata. Never log the API key, audio contents or
		 * returned transcription.
		 */
		logger.info("OpenAI transcription request started: {} bytes, content type {}", audio.getSize(),
				audio.getContentType());

		try {
			OpenAiTranscriptionResponse response = restClient.post().uri(TRANSCRIPTION_PATH)
					.contentType(MediaType.MULTIPART_FORM_DATA).body(requestBody).retrieve()
					.body(OpenAiTranscriptionResponse.class);

			if (response == null || !StringUtils.hasText(response.text())) {
				logger.warn("OpenAI returned blank transcription text after {} ms", elapsedMillis(startedAt));
				throw new IllegalStateException("OpenAI returned no transcription text");
			}

			logger.info("OpenAI transcription request completed in {} ms with {} characters",
					elapsedMillis(startedAt), response.text().length());

			return response.text();
		}
		catch (RestClientResponseException exception) {
			logger.warn("OpenAI returned HTTP {} after {} ms", exception.getStatusCode().value(),
					elapsedMillis(startedAt));
			throw exception;
		}
		catch (RestClientException exception) {
			logger.warn("OpenAI request failed after {} ms: {}", elapsedMillis(startedAt),
					exception.getClass().getSimpleName());
			throw exception;
		}
	}

	private static long elapsedMillis(long startedAt) {
		return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
	}

	private MediaType resolveContentType(MultipartFile audio) {
		if (!StringUtils.hasText(audio.getContentType())) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}

		return MediaType.parseMediaType(audio.getContentType());
	}
}
