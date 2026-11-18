package com.team262.moviematch.config.openapi;

public final class AnalyticsApiExamples {

    private AnalyticsApiExamples() {
    }

    public static final String BATCH_REQUEST = """
            {"events": [
              {"eventId": "6f1c8a52-9d3e-4b7a-8c21-5e0a7b1d9f34", "name": "genres_selected",
               "occurredAt": "2026-11-16T10:15:30.123Z", "sessionId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
               "page": "/create", "params": {"genres": [28, 35]}},
              {"name": "room_invite_link_copied", "occurredAt": "2026-11-16T10:15:42.500Z",
               "sessionId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", "page": "/room/:code"}
            ]}
            """;

    public static final String SINGLE_REQUEST = """
            {"name": "like_button", "occurredAt": "2026-11-16T10:16:01.000Z",
             "sessionId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", "page": "/room/:code",
             "params": {"roomTitleId": 41}}
            """;

    public static final String INVALID_BATCH = """
            {"code": "INVALID_EVENT_BATCH", "message": "Request body is not valid JSON", "fieldErrors": []}
            """;

    public static final String TOO_LARGE = """
            {"code": "REQUEST_FAILED", "message": "Request body is too large", "fieldErrors": []}
            """;
}
