package com.rafid.speechtotext.controller;

import java.time.Instant;

import com.rafid.speechtotext.exception.InvalidAudioUploadException;
import com.rafid.speechtotext.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger applicationLogger = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private static final String INVALID_AUDIO_MESSAGE = "A non-empty audio file is required.";
	private static final String INTERNAL_ERROR_MESSAGE = "An unexpected server error occurred.";

	@ExceptionHandler(InvalidAudioUploadException.class)
	public ResponseEntity<ErrorResponse> handleInvalidAudioUpload(
			InvalidAudioUploadException exception,
			HttpServletRequest request) {

		applicationLogger.warn("Invalid API request: path={}, exception={}", request.getRequestURI(),
				exception.getClass().getSimpleName());

		return errorResponse(HttpStatus.BAD_REQUEST, INVALID_AUDIO_MESSAGE, request.getRequestURI());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
		/*
		 * Log only the exception type and request path. Exception messages can contain
		 * upstream response details and must not be exposed to logs or clients.
		 */
		applicationLogger.error("Unexpected API failure: path={}, exception={}", request.getRequestURI(),
				exception.getClass().getSimpleName());

		return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, request.getRequestURI());
	}

	/*
	 * Spring routes framework-level failures, such as a missing multipart part,
	 * through this method. Replacing Spring's default body keeps every API error in
	 * the assignment's five-field ErrorResponse shape.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
			Exception exception,
			Object body,
			HttpHeaders headers,
			HttpStatusCode statusCode,
			WebRequest request) {

		String path = requestPath(request);
		String message = errorMessage(exception, statusCode);

		applicationLogger.warn("API request rejected: path={}, status={}, exception={}", path, statusCode.value(),
				exception.getClass().getSimpleName());

		ErrorResponse errorResponse = new ErrorResponse(Instant.now(), statusCode.value(), reasonPhrase(statusCode),
				message, path);

		return new ResponseEntity<>(errorResponse, headers, statusCode);
	}

	private static ResponseEntity<ErrorResponse> errorResponse(HttpStatus status, String message, String path) {
		ErrorResponse response = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path);

		return ResponseEntity.status(status).body(response);
	}

	private static String errorMessage(Exception exception, HttpStatusCode statusCode) {
		if (exception instanceof MissingServletRequestPartException missingPart
				&& "audio".equals(missingPart.getRequestPartName())) {
			return INVALID_AUDIO_MESSAGE;
		}

		if (statusCode.is5xxServerError()) {
			return INTERNAL_ERROR_MESSAGE;
		}

		return "The request could not be processed.";
	}

	private static String reasonPhrase(HttpStatusCode statusCode) {
		HttpStatus status = HttpStatus.resolve(statusCode.value());

		return status == null ? "HTTP " + statusCode.value() : status.getReasonPhrase();
	}

	private static String requestPath(WebRequest request) {
		if (request instanceof ServletWebRequest servletWebRequest) {
			return servletWebRequest.getRequest().getRequestURI();
		}

		return "";
	}
}
