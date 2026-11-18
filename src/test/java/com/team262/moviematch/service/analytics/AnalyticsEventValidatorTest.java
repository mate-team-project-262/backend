package com.team262.moviematch.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.service.analytics.AnalyticsEventValidator.Result;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnalyticsEventValidatorTest {

    private static final String SESSION = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnalyticsEventValidator validator = new AnalyticsEventValidator(2048);

    private Result validate(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        return validator.validate(node, NOW);
    }

    /** {"name": ..., "occurredAt": ..., "sessionId": ...} plus extra fields. */
    private String event(String name, String occurredAt, String extra) {
        return "{\"name\":\"" + name + "\",\"occurredAt\":\"" + occurredAt + "\",\"sessionId\":\"" + SESSION + "\""
                + extra + "}";
    }

    private String valid(String extra) {
        return event("genres_selected", "2026-11-16T09:59:00Z", extra);
    }

    @Test
    void acceptsAMinimalEvent() throws Exception {
        Result result = validate(valid(""));

        assertThat(result.accepted()).isTrue();
        assertThat(result.event().name()).isEqualTo("genres_selected");
        assertThat(result.event().clientSessionId()).isEqualTo(SESSION);
        assertThat(result.event().occurredAt()).isEqualTo(Instant.parse("2026-11-16T09:59:00Z"));
        assertThat(result.event().eventId()).isNull();
        assertThat(result.event().page()).isNull();
        assertThat(result.event().propertiesJson()).isNull();
    }

    @Test
    void normalizesTheEventName() throws Exception {
        Result result = validate(event(" Like_Button ", "2026-11-16T09:59:00Z", ""));

        assertThat(result.accepted()).isTrue();
        assertThat(result.event().name()).isEqualTo("like_button");
    }

    @Test
    void rejectsUnknownAndServerOnlyNames() throws Exception {
        assertThat(validate(event("movie_swiped", "2026-11-16T09:59:00Z", "")).rejectReason())
                .isEqualTo(AnalyticsEventValidator.UNKNOWN_NAME);
        assertThat(validate(event("movie_won", "2026-11-16T09:59:00Z", "")).rejectReason())
                .isEqualTo(AnalyticsEventValidator.RESERVED_NAME);
        assertThat(validate("{\"occurredAt\":\"2026-11-16T09:59:00Z\",\"sessionId\":\"" + SESSION + "\"}")
                .rejectReason()).isEqualTo(AnalyticsEventValidator.UNKNOWN_NAME);
        assertThat(validate("{\"name\":42}").rejectReason()).isEqualTo(AnalyticsEventValidator.UNKNOWN_NAME);
    }

    @Test
    void rejectsAnInvalidSessionIdOrEventId() throws Exception {
        assertThat(validate("{\"name\":\"settings_clicked\",\"occurredAt\":\"2026-11-16T09:59:00Z\"}")
                .rejectReason()).isEqualTo(AnalyticsEventValidator.BAD_SESSION_ID);
        assertThat(validate("{\"name\":\"settings_clicked\",\"occurredAt\":\"2026-11-16T09:59:00Z\","
                + "\"sessionId\":\"not-a-uuid\"}").rejectReason()).isEqualTo(AnalyticsEventValidator.BAD_SESSION_ID);
        assertThat(validate(valid(",\"eventId\":\"123\"")).rejectReason())
                .isEqualTo(AnalyticsEventValidator.BAD_EVENT_ID);
    }

    @Test
    void acceptsAValidEventId() throws Exception {
        Result result = validate(valid(",\"eventId\":\"6F1C8A52-9D3E-4B7A-8C21-5E0A7B1D9F34\""));

        assertThat(result.event().eventId()).isEqualTo("6f1c8a52-9d3e-4b7a-8c21-5e0a7b1d9f34");
    }

    @Test
    void rejectsAMissingOrUnparsableTime() throws Exception {
        assertThat(validate("{\"name\":\"settings_clicked\",\"sessionId\":\"" + SESSION + "\"}").rejectReason())
                .isEqualTo(AnalyticsEventValidator.BAD_TIME);
        assertThat(validate(event("settings_clicked", "yesterday", "")).rejectReason())
                .isEqualTo(AnalyticsEventValidator.BAD_TIME);
    }

    @Test
    void convertsTimeZoneOffsetsToUtc() throws Exception {
        Result result = validate(event("settings_clicked", "2026-11-16T10:59:00+01:00", ""));

        assertThat(result.event().occurredAt()).isEqualTo(Instant.parse("2026-11-16T09:59:00Z"));
    }

    @Test
    void aClearlyWrongClientClockFallsBackToTheServerTime() throws Exception {
        assertThat(validate(event("settings_clicked", "2026-11-16T12:00:00Z", "")).event().occurredAt())
                .isEqualTo(NOW);                                       // 2 hours in the future
        assertThat(validate(event("settings_clicked", "2026-11-01T10:00:00Z", "")).event().occurredAt())
                .isEqualTo(NOW);                                       // 15 days in the past
        assertThat(validate(event("settings_clicked", "2026-11-16T10:03:00Z", "")).event().occurredAt())
                .isEqualTo(Instant.parse("2026-11-16T10:03:00Z"));     // 3 minutes ahead is fine
    }

    @Test
    void pageLosesQueryStringAndInvitationCodes() throws Exception {
        assertThat(validate(valid(",\"page\":\"/room/K7M2QX?utm=1#top\"")).event().page()).isEqualTo("/room/:code");
        assertThat(validate(valid(",\"page\":\"/join/k7m2qx\"")).event().page()).isEqualTo("/join/:code");
        assertThat(validate(valid(",\"page\":\"/room/K7M2QX/results\"")).event().page())
                .isEqualTo("/room/:code/results");
        assertThat(validate(valid(",\"page\":\"/room/:code\"")).event().page()).isEqualTo("/room/:code");
        assertThat(validate(valid(",\"page\":\"/settings\"")).event().page()).isEqualTo("/settings");
    }

    @Test
    void rejectsAPageThatIsNotTextOrTooLong() throws Exception {
        assertThat(validate(valid(",\"page\":42")).rejectReason()).isEqualTo(AnalyticsEventValidator.BAD_PAGE);
        assertThat(validate(valid(",\"page\":\"/" + "a".repeat(250) + "\"")).rejectReason())
                .isEqualTo(AnalyticsEventValidator.BAD_PAGE);
    }

    @Test
    void keepsParamsButRemovesPersonalData() throws Exception {
        Result result = validate(valid(",\"params\":{\"genres\":[28,35],\"Email\":\"a@b.c\",\"roomCode\":\"K7M2QX\","
                + "\"name\":\"Alex\",\"maxDuration\":120}"));

        assertThat(result.accepted()).isTrue();
        JsonNode params = mapper.readTree(result.event().propertiesJson());
        assertThat(params.has("genres")).isTrue();
        assertThat(params.get("maxDuration").asInt()).isEqualTo(120);
        assertThat(params.has("Email")).isFalse();
        assertThat(params.has("roomCode")).isFalse();
        assertThat(params.has("name")).isFalse();
    }

    @Test
    void emptyParamsAreNotStored() throws Exception {
        assertThat(validate(valid(",\"params\":{}")).event().propertiesJson()).isNull();
        assertThat(validate(valid(",\"params\":{\"email\":\"a@b.c\"}")).event().propertiesJson()).isNull();
    }

    @Test
    void rejectsParamsThatAreNotAnObjectOrTooLarge() throws Exception {
        assertThat(validate(valid(",\"params\":[1,2]")).rejectReason()).isEqualTo(AnalyticsEventValidator.BAD_PARAMS);
        assertThat(validate(valid(",\"params\":{\"blob\":\"" + "x".repeat(3000) + "\"}")).rejectReason())
                .isEqualTo(AnalyticsEventValidator.BAD_PARAMS);
    }

    @Test
    void rejectsAnEventThatIsNotAnObject() throws Exception {
        assertThat(validate("\"settings_clicked\"").rejectReason()).isEqualTo(AnalyticsEventValidator.BAD_EVENT);
        assertThat(validate("[1]").rejectReason()).isEqualTo(AnalyticsEventValidator.BAD_EVENT);
    }

    @Test
    void theCatalogMatchesTheSpecification() {
        assertThat(AnalyticsEventCatalog.clientEvents()).hasSize(36)
                .contains("create_room_submitted", "swipe_right", "like_button", "movie_liked", "sign_up_succeeded")
                .doesNotContain("movie_won", "swipe_session_started", "swipe_session_completed", "room_id");
        assertThat(AnalyticsEventCatalog.clientEvents()).allMatch(name -> name.equals(name.toLowerCase()));
    }
}
