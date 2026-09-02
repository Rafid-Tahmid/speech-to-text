package com.rafid.speechtotext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.rafid.speechtotext.service.TranscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BlockingRequestConcurrencyTests.BlockingServiceConfiguration.class)
class BlockingRequestConcurrencyTests {

	private static final int REQUEST_COUNT = 201;
	private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

	@LocalServerPort
	private int serverPort;

	@Autowired
	private BlockingTranscriptionService blockingService;

	/*
	 * Sends more than 200 real HTTP requests through Tomcat and
	 * TranscriptionController. The blocking service holds every request until all
	 * 201 have entered, proving that they overlap instead of being handled only in
	 * small sequential batches.
	 */
	@Test
	void handlesMoreThanTwoHundredSimultaneousBlockingRequests() throws Exception {

		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

		ExecutorService requestExecutor = Executors.newFixedThreadPool(REQUEST_COUNT);
		CountDownLatch clientsReady = new CountDownLatch(REQUEST_COUNT);
		CountDownLatch startRequests = new CountDownLatch(1);
		List<Future<HttpResponse<String>>> responses = new ArrayList<>();

		try {
			for (int requestNumber = 0; requestNumber < REQUEST_COUNT; requestNumber++) {
				int currentRequest = requestNumber;

				responses.add(requestExecutor.submit(() -> {
					clientsReady.countDown();

					if (!startRequests.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("HTTP clients did not start together");
					}

					return httpClient.send(createMultipartRequest(currentRequest),
							HttpResponse.BodyHandlers.ofString());
				}));
			}

			assertTrue(clientsReady.await(5, TimeUnit.SECONDS), "All HTTP clients should be ready");

			long startedAt = System.nanoTime();

			startRequests.countDown();

			assertTrue(blockingService.awaitAllRequests(TEST_TIMEOUT),
					"All 201 requests should enter the blocking service; maximum observed was "
							+ blockingService.maximumConcurrentRequests());

			assertEquals(REQUEST_COUNT, blockingService.maximumConcurrentRequests(),
					"All requests should overlap before being released");

			blockingService.releaseRequests();

			for (Future<HttpResponse<String>> responseFuture : responses) {
				HttpResponse<String> response = responseFuture.get(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

				assertEquals(200, response.statusCode());
				assertTrue(response.body().contains("Load test transcription."));
			}

			long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

			assertTrue(elapsedMillis < TEST_TIMEOUT.toMillis(),
					"The concurrent requests took too long: " + elapsedMillis + " ms");
		} finally {
			startRequests.countDown();
			blockingService.releaseRequests();
			requestExecutor.shutdownNow();

			assertTrue(requestExecutor.awaitTermination(5, TimeUnit.SECONDS),
					"The HTTP client threads should stop");
		}
	}

	private HttpRequest createMultipartRequest(int requestNumber) {
		String boundary = "LoadTestBoundary" + requestNumber;

		String requestBody = "--" + boundary + "\r\n" + "Content-Disposition: form-data; " + "name=\"audio\"; "
				+ "filename=\"recording.webm\"\r\n" + "Content-Type: audio/webm\r\n" + "\r\n" + "test audio\r\n" + "--"
				+ boundary + "--\r\n";

		return HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + serverPort + "/api/v1/transcriptions"))
				.timeout(Duration.ofSeconds(15)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(requestBody.getBytes(StandardCharsets.UTF_8))).build();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class BlockingServiceConfiguration {

		@Bean
		@Primary
		BlockingTranscriptionService blockingTranscriptionService() {
			return new BlockingTranscriptionService(REQUEST_COUNT);
		}
	}

	static class BlockingTranscriptionService implements TranscriptionService {

		private final CountDownLatch requestsEntered;
		private final CountDownLatch releaseRequests = new CountDownLatch(1);

		private final AtomicInteger activeRequests = new AtomicInteger();

		private final AtomicInteger maximumActiveRequests = new AtomicInteger();

		BlockingTranscriptionService(int requestCount) {
			this.requestsEntered = new CountDownLatch(requestCount);
		}

		@Override
		public String transcribe(MultipartFile audio) {
			int currentActive = activeRequests.incrementAndGet();

			maximumActiveRequests.accumulateAndGet(currentActive, Math::max);

			requestsEntered.countDown();

			try {
				if (!releaseRequests.await(20, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Timed out waiting to release load-test requests");
				}

				return "Load test transcription.";
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();

				throw new IllegalStateException("Load-test request was interrupted", exception);
			} finally {
				activeRequests.decrementAndGet();
			}
		}

		boolean awaitAllRequests(Duration timeout) throws InterruptedException {

			return requestsEntered.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}

		int maximumConcurrentRequests() {
			return maximumActiveRequests.get();
		}

		void releaseRequests() {
			releaseRequests.countDown();
		}
	}
}
