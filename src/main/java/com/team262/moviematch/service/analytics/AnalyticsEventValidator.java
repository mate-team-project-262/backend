package com.team262.moviematch.service.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Checks ONE event from a batch. A bad event is dropped (with a reason), it never fails the batch.
 * Pure logic, no database.
 */
@Component
public class AnalyticsEventValidator {

    public static final String BAD_EVENT = "bad_event";
    public static final String UNKNOWN_NAME = "unknown_name";
    public static final String RESERVED_NAME = "reserved_name";
    public static final String BAD_SESSION_ID = "bad_session_id";
    public static final String BAD_EVENT_ID = "bad_event_id";
    public static final String BAD_TIME = "bad_time";
    public static final String BAD_PAGE = "bad_page";
    public static final String BAD_PARAMS = "bad_params";

    private static final Duration MAX_PAST = Duration.ofDays(7);
    private static final Duration MAX_FUTURE = Duration.ofMinutes(5);
    private static final int MAX_PAGE_LENGTH = 200;

    /** Keys that must never be stored (the spec forbids names, emails and room codes in events). */
    private static final Set<String> FORBIDDEN_PARAM_KEYS = Set.of(
            "name", "displayname", "display_name", "username", "email", "password", "currentpassword",
            "newpassword", "token", "participanttoken", "roomcode", "room_code", "code");

    /** /room/K7M2QX and /join/K7M2QX -> /room/:code. The invitation code is a secret. */
    private static final Pattern ROOM_CODE_IN_PATH =
            Pattern.compile("/(room|join)/[A-Za-z0-9]{6}(?=/|$)", Pattern.CASE_INSENSITIVE);

    private final int maxParamsBytes;

    public AnalyticsEventValidator(@Value("${analytics.max-params-bytes:2048}") int maxParamsBytes) {
        this.maxParamsBytes = maxParamsBytes;
    }

    public record Result(IncomingEvent event, String rejectReason) {
        public boolean accepted() {
            return event != null;
        }

        static Result ok(IncomingEvent event) {
            return new Result(event, null);
        }

        static Result reject(String reason) {
            return new Result(null, reason);
        }
    }

    public Result validate(JsonNode node, Instant receivedAt) {
        if (node == null || !node.isObject()) {
            return Result.reject(BAD_EVENT);
        }
        String rawName = text(node, "name");
        if (rawName == null) {
            return Result.reject(UNKNOWN_NAME);
        }
        String name = rawName.trim().toLowerCase(Locale.ROOT);      // Like_button -> like_button
        if (AnalyticsEventCatalog.isBackendOnly(name)) {
            return Result.reject(RESERVED_NAME);
        }
        if (!AnalyticsEventCatalog.isClientEvent(name)) {
            return Result.reject(UNKNOWN_NAME);
        }

        String sessionId = uuid(text(node, "sessionId"));
        if (sessionId == null) {
            return Result.reject(BAD_SESSION_ID);
        }

        String eventId = null;
        if (node.hasNonNull("eventId")) {
            eventId = uuid(text(node, "eventId"));
            if (eventId == null) {
                return Result.reject(BAD_EVENT_ID);
            }
        }

        Instant occurredAt = occurredAt(text(node, "occurredAt"), receivedAt);
        if (occurredAt == null) {
            return Result.reject(BAD_TIME);
        }

        String page = null;
        if (node.hasNonNull("page")) {
            page = page(text(node, "page"));
            if (page == null) {
                return Result.reject(BAD_PAGE);
            }
        }

        String propertiesJson = null;
        if (node.hasNonNull("params")) {
            JsonNode params = node.get("params");
            if (!params.isObject()) {
                return Result.reject(BAD_PARAMS);
            }
            ObjectNode cleaned = ((ObjectNode) params).deepCopy();
            for (String key : new ArrayList<>(iterate(cleaned.fieldNames()))) {
                if (FORBIDDEN_PARAM_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                    cleaned.remove(key);
                }
            }
            if (!cleaned.isEmpty()) {
                propertiesJson = cleaned.toString();
                if (propertiesJson.getBytes(StandardCharsets.UTF_8).length > maxParamsBytes) {
                    return Result.reject(BAD_PARAMS);
                }
            }
        }
        return Result.ok(new IncomingEvent(eventId, name, occurredAt, sessionId, page, propertiesJson));
    }

    /** A clearly wrong client clock (more than 5 minutes ahead or 7 days behind) falls back to the server time. */
    private Instant occurredAt(String text, Instant receivedAt) {
        if (text == null) {
            return null;
        }
        Instant parsed;
        try {
            parsed = OffsetDateTime.parse(text.trim()).toInstant();
        } catch (DateTimeParseException exception) {
            return null;
        }
        if (parsed.isAfter(receivedAt.plus(MAX_FUTURE)) || parsed.isBefore(receivedAt.minus(MAX_PAST))) {
            return receivedAt;
        }
        return parsed;
    }

    /** Drops the query string and fragment and hides invitation codes. Null when invalid or too long. */
    private String page(String text) {
        if (text == null) {
            return null;
        }
        String page = text.trim().split("[?#]", 2)[0];
        page = ROOM_CODE_IN_PATH.matcher(page).replaceAll("/$1/:code");
        if (page.length() > MAX_PAGE_LENGTH) {
            return null;
        }
        return page.isEmpty() ? null : page;
    }

    private static String uuid(String text) {
        if (text == null || text.trim().length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(text.trim()).toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static List<String> iterate(java.util.Iterator<String> iterator) {
        List<String> list = new ArrayList<>();
        iterator.forEachRemaining(list::add);
        return list;
    }
}
