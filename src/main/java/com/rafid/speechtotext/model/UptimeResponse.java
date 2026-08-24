package com.rafid.speechtotext.model;
import java.time.Instant;

/**
 * This record describes the data returned to the client.
 * The three Java fields become these JSON fields:
 */
public record UptimeResponse(
        Instant utcServerStart,	 //Instant represents a moment in UTC. Spring automatically converts the record to JSON.
        Instant utcNow,
        double serverUptimeSeconds
) {
}