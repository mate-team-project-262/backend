package com.team262.moviematch.service.analytics;

import com.team262.moviematch.model.analytics.AnalyticsEvent;
import com.team262.moviematch.model.analytics.EventSource;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes backend events AFTER the game transaction committed: a rolled back vote leaves no event.
 * fallbackExecution: without a surrounding transaction the event is written immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsBackendEventListener {

    private final AnalyticsEventWriter writer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEvent(BackendAnalyticsEvent event) {
        try {
            writer.saveIsolated(List.of(new AnalyticsEvent(null, event.name(), EventSource.BACKEND,
                    event.occurredAt(), Instant.now(), null, null, event.userId(), event.participantId(),
                    event.roomId(), event.gameSessionId(), event.propertiesJson())));
        } catch (RuntimeException exception) {
            // an exception here would surface in the request that already committed its vote
            log.warn("Could not store backend analytics event {}", event.name(), exception);
        }
    }
}
