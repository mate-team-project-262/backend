package com.team262.moviematch.config.openapi;

public final class RoomApiExamples {
    private RoomApiExamples() {
    }

    public static final String CREATE_REQUEST = """
            {"displayName":"Anna","preferences":{"contentType":"MOVIES",
            "genreIds":[28,35],"maxDurationMinutes":120}}
            """;
    public static final String DEFAULT_REQUEST = """
            {"displayName":"Anna"}
            """;
    public static final String CREATE_RESPONSE = """
            {"code":"K7M2QX","status":"LOBBY","preferences":{"contentType":"MOVIES",
            "genreIds":[28,35],"maxDurationMinutes":120},"participant":{"id":1,
            "displayName":"Anna","host":true,"token":"3f2b8c1e-5a4d-4e7b-9c2a-1d6f0b7e8a90"}}
            """;
    public static final String DEFAULT_RESPONSE = """
            {"code":"K7M2QX","status":"LOBBY","preferences":{"contentType":"MOVIES",
            "genreIds":[],"maxDurationMinutes":null},"participant":{"id":1,
            "displayName":"Anna","host":true,"token":"3f2b8c1e-5a4d-4e7b-9c2a-1d6f0b7e8a90"}}
            """;
    public static final String JOIN_REQUEST = """
            {"displayName":"Egor"}
            """;
    public static final String JOIN_RESPONSE = """
            {"code":"K7M2QX","status":"LOBBY","currentParticipant":{"id":2,
            "displayName":"Egor","host":false,"token":"7c1487a2-c472-4482-b426-e361409028e7"}}
            """;
    public static final String LOBBY_RESPONSE = """
            {"code":"K7M2QX","status":"LOBBY","preferences":{"contentType":"MOVIES",
            "genreIds":[28,35],"maxDurationMinutes":120},"participantCount":2,"maxParticipants":5,
            "participants":[{"id":1,"displayName":"Anna","host":true},
            {"id":2,"displayName":"Egor","host":false}]}
            """;
    public static final String VALIDATION_ERROR = """
            {"code":"VALIDATION_ERROR","message":"Validation failed",
            "fieldErrors":[{"field":"displayName","message":"size must be between 3 and 20"}]}
            """;
    public static final String BAD_JSON = """
            {"status":400,"error":"Bad Request","path":"/rooms"}
            """;
    public static final String MISSING_TOKEN = """
            {"code":"INVALID_TOKEN","message":"Missing participant token","fieldErrors":[]}
            """;
    public static final String INVALID_TOKEN = """
            {"code":"INVALID_TOKEN","message":"Invalid participant token","fieldErrors":[]}
            """;
    public static final String EXPIRED_TOKEN = """
            {"code":"INVALID_TOKEN","message":"Participant token has expired","fieldErrors":[]}
            """;
    public static final String WRONG_ROOM = """
            {"code":"WRONG_ROOM","message":"Participant does not belong to this room","fieldErrors":[]}
            """;
    public static final String ROOM_NOT_FOUND = """
            {"code":"ROOM_NOT_FOUND","message":"Room not found","fieldErrors":[]}
            """;
    public static final String ROOM_FULL = """
            {"code":"ROOM_FULL","message":"Room has more than 5 participants","fieldErrors":[]}
            """;
    public static final String NAME_TAKEN = """
            {"code":"NAME_TAKEN","message":"Name already has taken","fieldErrors":[]}
            """;
    public static final String ROOM_NOT_JOINABLE = """
            {"code":"ROOM_NOT_JOINABLE","message":"Room not in status LOBBY","fieldErrors":[]}
            """;
}
