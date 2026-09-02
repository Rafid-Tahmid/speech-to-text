package com.rafid.speechtotext.exception;

public class InvalidAudioUploadException extends RuntimeException {

	public InvalidAudioUploadException() {
		super("A non-empty audio file is required.");
	}
}
