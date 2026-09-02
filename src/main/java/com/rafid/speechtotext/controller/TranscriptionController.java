package com.rafid.speechtotext.controller;

import com.rafid.speechtotext.exception.InvalidAudioUploadException;
import com.rafid.speechtotext.model.TranscriptionResponse;
import com.rafid.speechtotext.service.TranscriptionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController // Converts the returned response object into JSON.
@RequestMapping("/api/v1/transcriptions")
public class TranscriptionController {

	/*
	 * The controller depends on the interface rather than the stub directly. This
	 * lets us replace the stub with an OpenAI implementation later.
	 */
	private final TranscriptionService transcriptionService;

	// Spring supplies the available TranscriptionService through constructor
	// injection.
	public TranscriptionController(TranscriptionService transcriptionService) {
		this.transcriptionService = transcriptionService;
	}

	/*
	 * Accepts a multipart/form-data POST request. The uploaded file must use the
	 * form field name "audio".
	 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public TranscriptionResponse transcribe(@RequestParam("audio") MultipartFile audio) {
		if (audio.isEmpty()) {
			throw new InvalidAudioUploadException();
		}

		String text = transcriptionService.transcribe(audio);

		// Spring serializes this record as JSON containing a "text" property.
		return new TranscriptionResponse(text);
	}
}
