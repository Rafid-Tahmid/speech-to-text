package com.rafid.speechtotext.service;

import com.rafid.speechtotext.model.GlobalStatsResponse;
import org.springframework.stereotype.Service;

@Service
public class GlobalStatsService {

	private long inputTokens;
	private long outputTokens;

	/*
	 * Synchronization prevents concurrent transcription requests from losing token
	 * updates or exposing a partially updated pair of counters.
	 */
	public synchronized void recordUsage(long inputTokens, long outputTokens) {
		this.inputTokens += inputTokens;
		this.outputTokens += outputTokens;
	}

	/*
	 * Both values are read while holding the same lock, producing one consistent
	 * statistics snapshot.
	 */
	public synchronized GlobalStatsResponse getStats() {
		return new GlobalStatsResponse(inputTokens, outputTokens);
	}
}