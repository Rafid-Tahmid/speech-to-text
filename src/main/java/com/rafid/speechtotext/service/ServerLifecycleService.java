/*
  When Spring creates the service, this line records the startup time:
  private final Instant serverStart = Instant.now();

  Later, getUptime():
  1. Gets the current time.
  2. Calculates the difference between the start time and current time.
  3. Converts the duration into seconds.
  4. Returns an UptimeResponse.

  @Service tells Spring to create and manage one instance of this class.
 */


package com.rafid.speechtotext.service;

import com.rafid.speechtotext.model.UptimeResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class ServerLifecycleService {

    private final Instant serverStart = Instant.now(); //runs when Spring creates the service object. It stores the startup time.

    public UptimeResponse getUptime() {
        Instant now = Instant.now();
        Duration uptime = Duration.between(serverStart, now); //calculates the difference between serverStart and now

        double uptimeSeconds =
                uptime.getSeconds() + uptime.getNano() / 1_000_000_000.0;  //converts duration into seconds,

        return new UptimeResponse(	//creates the record and puts the three values into UptimeResponse.java
                serverStart,
                now,
                uptimeSeconds
        );
    }
}