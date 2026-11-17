package com.team262.moviematch.model.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One analytics event. There are deliberately no foreign keys: rooms and participants are deleted
 * (host leaves, round restart) while events must survive them.
 */
@Entity
@Table(name = "analytics_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalyticsEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optional id from the client; a retried event with the same id is stored once. */
    @Column(name = "event_id", length = 36, updatable = false)
    private String eventId;

    @Column(name = "event_name", nullable = false, length = 64, updatable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_source", nullable = false, length = 16, updatable = false)
    private EventSource source;

    /** Time reported by the client (replaced with receivedAt when the client clock is clearly wrong). */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    /** Random id of the browser tab (the frontend's "sessionId"), not the voting session. */
    @Column(name = "client_session_id", length = 36, updatable = false)
    private String clientSessionId;

    @Column(length = 200, updatable = false)
    private String page;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "participant_id", updatable = false)
    private Long participantId;

    @Column(name = "room_id", updatable = false)
    private Long roomId;

    /** The voting session ("session_id" in the analytics specification): one started game of a room. */
    @Column(name = "game_session_id", length = 36, updatable = false)
    private String gameSessionId;

    @Column(name = "properties_json", columnDefinition = "TEXT", updatable = false)
    private String propertiesJson;

    public AnalyticsEvent(String eventId, String name, EventSource source, Instant occurredAt,
                          Instant receivedAt, String clientSessionId, String page, Long userId,
                          Long participantId, Long roomId, String gameSessionId, String propertiesJson) {
        this.eventId = eventId;
        this.name = name;
        this.source = source;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.clientSessionId = clientSessionId;
        this.page = page;
        this.userId = userId;
        this.participantId = participantId;
        this.roomId = roomId;
        this.gameSessionId = gameSessionId;
        this.propertiesJson = propertiesJson;
    }

    /** Without a game session (kept so that existing callers and tests do not change). */
    public AnalyticsEvent(String eventId, String name, EventSource source, Instant occurredAt,
                          Instant receivedAt, String clientSessionId, String page, Long userId,
                          Long participantId, Long roomId, String propertiesJson) {
        this(eventId, name, source, occurredAt, receivedAt, clientSessionId, page, userId,
                participantId, roomId, null, propertiesJson);
    }
}