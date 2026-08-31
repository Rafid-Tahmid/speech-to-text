package com.rafid.speechtotext.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StubTranscriptionService implements TranscriptionService {

	@Override
	public String transcribe(MultipartFile audio) {
		return "This is a stub transcription.";
	}
}