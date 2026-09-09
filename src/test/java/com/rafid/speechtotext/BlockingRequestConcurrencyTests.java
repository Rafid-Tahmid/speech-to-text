package com.rafid.speechtotext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
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
	 * Connections are admitted one at a time to avoid overflowing the operating
	 * system's TCP listen backlog. No transcription is released during admission.
	 * This diagnostic refinement was developed with OpenAI Codex assistance.
	 */
	@Test
	void handlesMoreThanTwoHundredSimultaneousBlockingRequests() throws Exception {

		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

		ExecutorService requestExecutor = Executors.newFixedThreadPool(REQUEST_COUNT);
		List<Future<HttpResponse<String>>> responses = new ArrayList<>();
		ConcurrentLinkedQueue<String> requestFailures = new ConcurrentLinkedQueue<>();

		try {
			long startedAt = System.nanoTime();
			long deadline = startedAt + TEST_TIMEOUT.toNanos();

			for (int requestNumber = 0; requestNumber < REQUEST_COUNT; requestNumber++) {
				int currentRequest = requestNumber;

				responses.add(requestExecutor.submit(() -> {
					try {
						return httpClient.send(createMultipartRequest(currentRequest),
								HttpResponse.BodyHandlers.ofString());
					} catch (Exception exception) {
						requestFailures.add(exception.toString() + "; cause=" + exception.getCause());
						throw exception;
					}
				}));
				assertTrue(blockingService.awaitNextRequest(deadline - System.nanoTime()),
						() -> "Request " + currentRequest + " did not enter the blocking service; active peak: "
								+ blockingService.maximumConcurrentRequests() + "; client failures: " + requestFailures);
			}

			assertTrue(blockingService.awaitAllRequests(TEST_TIMEOUT),
					() -> "All 201 requests should enter the blocking service; maximum observed was "
							+ blockingService.maximumConcurrentRequests() + "; client failures: " + requestFailures);

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
		private final Semaphore requestArrivals = new Semaphore(0);

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
			requestArrivals.release();

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

		boolean awaitNextRequest(long remainingNanos) throws InterruptedException {
			return requestArrivals.tryAcquire(Math.max(0, remainingNanos), TimeUnit.NANOSECONDS);
		}

		int maximumConcurrentRequests() {
			return maximumActiveRequests.get();
		}

		void releaseRequests() {
			releaseRequests.countDown();
		}
	}
}
