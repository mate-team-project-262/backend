package com.team262.moviematch.service.analytics;

import java.time.Instant;

/**
 * An event recorded by the server. Plain values only (no entities), so it is safe to handle it after
 * the game transaction has finished. occurredAt is the moment the thing happened, not the write time.
 */
public record BackendAnalyticsEvent(String name, Instant occurredAt, Long roomId, String gameSessionId,
                                    Long participantId, Long userId, String propertiesJson) {
}
