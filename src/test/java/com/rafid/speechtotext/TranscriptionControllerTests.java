package com.rafid.speechtotext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TranscriptionControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void audioUploadReturnsStubTranscription() throws Exception {
		/*
		 * The stub does not decode audio, so small fake contents are sufficient. The
		 * field name "audio" must match the controller's @RequestParam.
		 */
		MockMultipartFile audio = new MockMultipartFile("audio", "recording.webm", "audio/webm",
				"fake audio contents".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/v1/transcriptions").file(audio)).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.text").value("This is a stub transcription."));
	}
}