package com.rafid.speechtotext;

import java.nio.charset.StandardCharsets;

import com.rafid.speechtotext.controller.TranscriptionController;
import com.rafid.speechtotext.service.TranscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TranscriptionController.class)
@ExtendWith(OutputCaptureExtension.class)
class TranscriptionControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TranscriptionService transcriptionService;

	@Test
	void audioUploadReturnsStubTranscription() throws Exception {
		/*
		 * The stub does not decode audio, so small fake contents are sufficient. The
		 * field name "audio" must match the controller's @RequestParam.
		 */
		MockMultipartFile audio = new MockMultipartFile("audio", "recording.webm", "audio/webm",
				"fake audio contents".getBytes(StandardCharsets.UTF_8));

		when(transcriptionService.transcribe(any())).thenReturn("This is a stub transcription.");

		mockMvc.perform(multipart("/api/v1/transcriptions").file(audio)).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.text").value("This is a stub transcription."));
	}

	@Test
	void emptyAudioReturnsBadRequest() throws Exception {
		/* The service must never receive an upload that contains no audio bytes. */
		MockMultipartFile emptyAudio = new MockMultipartFile("audio", "recording.webm", "audio/webm", new byte[0]);

		mockMvc.perform(multipart("/api/v1/transcriptions").file(emptyAudio)).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.timestamp").isString()).andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("A non-empty audio file is required."))
				.andExpect(jsonPath("$.path").value("/api/v1/transcriptions"));

		verifyNoInteractions(transcriptionService);
	}

	@Test
	void missingAudioReturnsBadRequest() throws Exception {
		/* Spring raises this error before the controller method runs. */
		mockMvc.perform(multipart("/api/v1/transcriptions")).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("A non-empty audio file is required."))
				.andExpect(jsonPath("$.path").value("/api/v1/transcriptions"));

		verifyNoInteractions(transcriptionService);
	}

	@Test
	void unexpectedServiceFailureReturnsSafeInternalServerError(CapturedOutput output) throws Exception {
		/*
		 * A simulated upstream detail must appear in neither the JSON response nor the
		 * captured log output. Only its safe exception classification is logged.
		 */
		MockMultipartFile audio = new MockMultipartFile("audio", "recording.webm", "audio/webm",
				"fake audio contents".getBytes(StandardCharsets.UTF_8));

		when(transcriptionService.transcribe(any())).thenThrow(new IllegalStateException("Sensitive upstream detail"));

		mockMvc.perform(multipart("/api/v1/transcriptions").file(audio)).andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.timestamp").isString()).andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.error").value("Internal Server Error"))
				.andExpect(jsonPath("$.message").value("An unexpected server error occurred."))
				.andExpect(jsonPath("$.path").value("/api/v1/transcriptions"))
				.andExpect(content().string(not(containsString("Sensitive upstream detail"))));

		assertTrue(output.getAll().contains("exception=IllegalStateException"));
		assertFalse(output.getAll().contains("Sensitive upstream detail"));
	}
}
