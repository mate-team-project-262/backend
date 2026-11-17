package com.team262.moviematch.service.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.BadRequestException;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.analytics.AnalyticsEvent;
import com.team262.moviematch.model.analytics.EventSource;
import com.team262.moviematch.repository.AnalyticsEventRepository;
import com.team262.moviematch.service.ParticipantTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Not @Transactional on purpose: the writer owns the transaction, so a unique-constraint error from a
 * concurrent retry can be caught here (same pattern as TitleStoreService).
 */
@Slf4j
@Service
public class AnalyticsIntakeService {

    private final ObjectMapper mapper;
    private final AnalyticsEventValidator validator;
    private final AnalyticsEventWriter writer;
    private final AnalyticsEventRepository repository;
    private final ParticipantTokenService participantTokens;
    private final MeterRegistry meters;
    private final int maxBatchSize;
    private final int maxBodyBytes;

    public AnalyticsIntakeService(ObjectMapper mapper, AnalyticsEventValidator validator,
                                  AnalyticsEventWriter writer, AnalyticsEventRepository repository,
                                  ParticipantTokenService participantTokens, MeterRegistry meters,
                                  @Value("${analytics.max-batch-size:50}") int maxBatchSize,
                                  @Value("${analytics.max-body-bytes:65536}") int maxBodyBytes) {
        this.mapper = mapper;
        this.validator = validator;
        this.writer = writer;
        this.repository = repository;
        this.participantTokens = participantTokens;
        this.meters = meters;
        this.maxBatchSize = maxBatchSize;
        this.maxBodyBytes = maxBodyBytes;
    }

    /** Who sent the events, as far as the server knows. Every part may be null. */
    private record Context(Long userId, Long participantId, Long roomId, String gameSessionId) {
    }

    public void collect(String body, Long userId, String participantToken) {
        List<JsonNode> nodes = extractEvents(body);
        if (nodes.isEmpty()) {
            return;
        }
        Instant receivedAt = Instant.now();
        Context context = context(userId, participantToken);

        List<IncomingEvent> accepted = new ArrayList<>();
        for (JsonNode node : nodes) {
            AnalyticsEventValidator.Result result = validator.validate(node, receivedAt);
            if (result.accepted()) {
                accepted.add(result.event());
            } else {
                count("rejected", result.rejectReason(), 1);
            }
        }

        List<AnalyticsEvent> toSave = withoutDuplicates(accepted).stream()
                .map(event -> new AnalyticsEvent(event.eventId(), event.name(), EventSource.FRONTEND,
                        event.occurredAt(), receivedAt, event.clientSessionId(), event.page(),
                        context.userId(), context.participantId(), context.roomId(),
                        context.gameSessionId(), event.propertiesJson()))
                .toList();
        count("duplicate", "duplicate_event_id", accepted.size() - toSave.size());
        if (toSave.isEmpty()) {
            return;
        }
        try {
            writer.saveAll(toSave);
            count("accepted", "none", toSave.size());
        } catch (DataIntegrityViolationException exception) {
            // two requests with the same eventId arrived at once: the other one already stored them
            log.warn("Analytics batch skipped because of a concurrent duplicate: {}",
                    exception.getMostSpecificCause().getMessage());
            count("duplicate", "concurrent_duplicate", toSave.size());
        }
    }

    /** Structural problems reject the whole request (400); the content of single events never does. */
    private List<JsonNode> extractEvents(String body) {
        if (body == null || body.isBlank()) {
            throw invalid("Request body is empty");
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > maxBodyBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Request body is too large");
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw invalid("Request body is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw invalid("Request body must be a JSON object");
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (root.has("events")) {
            if (!root.get("events").isArray()) {
                throw invalid("'events' must be an array");
            }
            root.get("events").forEach(nodes::add);
        } else if (root.has("name")) {
            nodes.add(root);                    // a single event object
        } else {
            throw invalid("Send {\"events\": [...]} or a single event object");
        }
        if (nodes.size() > maxBatchSize) {
            throw invalid("At most " + maxBatchSize + " events per request");
        }
        return nodes;
    }

    /** Skips events already stored (a retried request) and repeats inside the same request. */
    private List<IncomingEvent> withoutDuplicates(List<IncomingEvent> events) {
        List<String> ids = events.stream().map(IncomingEvent::eventId).filter(id -> id != null).distinct().toList();
        Set<String> seen = new HashSet<>(ids.isEmpty() ? List.of() : repository.findExistingEventIds(ids));
        List<IncomingEvent> unique = new ArrayList<>();
        for (IncomingEvent event : events) {
            if (event.eventId() == null || seen.add(event.eventId())) {
                unique.add(event);
            }
        }
        return unique;
    }

    /**
     * The participant token only comes with fetch (sendBeacon cannot set headers). A missing, wrong or
     * expired token is not an error: the events are stored without participant, room and game session.
     */
    private Context context(Long userId, String participantToken) {
        Long participantId = null;
        Long roomId = null;
        String gameSessionId = null;
        if (participantToken != null && !participantToken.isBlank()) {
            try {
                Participant participant = participantTokens.requireParticipant(participantToken.trim());
                participantId = participant.getId();
                roomId = participant.getRoom().getId();
                gameSessionId = participant.getRoom().getGameSessionId();
            } catch (UnauthorizedException ignored) {
                // analytics must not depend on a valid token
            }
        }
        return new Context(userId, participantId, roomId, gameSessionId);
    }

    private void count(String result, String reason, int amount) {
        if (amount > 0) {
            // every counter of one meter name needs the same tag keys (Prometheus)
            meters.counter("analytics.events", "result", result, "reason", reason).increment(amount);
        }
    }

    private BadRequestException invalid(String message) {
        return new BadRequestException(message, ErrorCode.INVALID_EVENT_BATCH);
    }
}