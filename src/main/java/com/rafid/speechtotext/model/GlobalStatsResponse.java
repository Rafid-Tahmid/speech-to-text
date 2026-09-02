package com.rafid.speechtotext.model;

/*
 * Represents cumulative OpenAI token usage since the current server started.
 * Spring converts this record into the exact JSON required by the YAML contract.
 */
public record GlobalStatsResponse(long inputTokens, long outputTokens) {
}