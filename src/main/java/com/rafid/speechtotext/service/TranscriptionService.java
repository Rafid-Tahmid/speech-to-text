package com.rafid.speechtotext.service;

import org.springframework.web.multipart.MultipartFile;

public interface TranscriptionService {	//the controller will depend on this interface instead of directly depending on OpenAI

    String transcribe(MultipartFile audio);
}