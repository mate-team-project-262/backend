package com.team262.moviematch.service.analytics;

import java.time.Instant;

/** One validated event from the browser. */
public record IncomingEvent(String eventId, String name, Instant occurredAt, String clientSessionId,
                            String page, String propertiesJson) {
}
