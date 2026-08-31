package com.rafid.speechtotext.service;

import com.rafid.speechtotext.model.OpenAiTranscriptionResponse;
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
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("!local")
public class OpenAiTranscriptionService implements TranscriptionService {

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

		OpenAiTranscriptionResponse response = restClient.post().uri(TRANSCRIPTION_PATH)
				.contentType(MediaType.MULTIPART_FORM_DATA).body(requestBody).retrieve()
				.body(OpenAiTranscriptionResponse.class);

		if (response == null || response.text() == null) {
			throw new IllegalStateException("OpenAI returned an invalid transcription response");
		}

		return response.text();
	}

	private MediaType resolveContentType(MultipartFile audio) {
		if (!StringUtils.hasText(audio.getContentType())) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}

		return MediaType.parseMediaType(audio.getContentType());
	}
}
