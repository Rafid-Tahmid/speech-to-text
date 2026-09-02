package com.rafid.speechtotext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.rafid.speechtotext.service.ApplicationShutdownService;
import com.rafid.speechtotext.service.ApplicationTerminator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class ApplicationShutdownServiceTests {

	@Test
	void acceptsExactlyOneConcurrentShutdownRequest() throws Exception {
		ApplicationTerminator terminator = mock(ApplicationTerminator.class);

		ApplicationShutdownService shutdownService = new ApplicationShutdownService(terminator);

		int requestCount = 16;
		ExecutorService executor = Executors.newFixedThreadPool(requestCount);

		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();

		try {
			for (int request = 0; request < requestCount; request++) {
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return shutdownService.requestShutdown();
				}));
			}

			assertTrue(ready.await(2, TimeUnit.SECONDS));
			start.countDown();

			int acceptedRequests = 0;

			for (Future<Boolean> result : results) {
				if (result.get(2, TimeUnit.SECONDS)) {
					acceptedRequests++;
				}
			}

			assertEquals(1, acceptedRequests);

			verify(terminator, timeout(2000).times(1)).terminate();
		} finally {
			start.countDown();
			executor.shutdownNow();
			executor.awaitTermination(2, TimeUnit.SECONDS);
		}
	}
}