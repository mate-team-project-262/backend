package com.team262.moviematch.config.openapi;

public final class AccountApiExamples {

    private AccountApiExamples() {
    }

    public static final String UPDATE_REQUEST = """
            {"displayName": "NewEgor", "email": "new-email@example.com", "currentPassword": "oldPassword123"}
            """;

    public static final String UPDATE_NAME_ONLY_REQUEST = """
            {"displayName": "NewEgor"}
            """;

    public static final String UPDATE_RESPONSE = """
            {"id": 7, "email": "new-email@example.com", "displayName": "NewEgor"}
            """;

    public static final String VALIDATION_ERROR_FIELD = """
            {"code": "VALIDATION_ERROR", "message": "Validation failed",
             "fieldErrors": [{"field": "displayName", "message": "size must be between 3 and 20"}]}
            """;

    public static final String EMPTY_REQUEST = """
            {"code": "VALIDATION_ERROR", "message": "Validation failed",
             "fieldErrors": [{"field": "request", "message": "Provide displayName and/or email"}]}
            """;

    public static final String PASSWORD_REQUIRED = """
            {"code": "VALIDATION_ERROR", "message": "Validation failed",
             "fieldErrors": [{"field": "currentPassword", "message": "Current password is required to change the email"}]}
            """;

    public static final String AUTHENTICATION_REQUIRED = """
            {"code": "AUTHENTICATION_REQUIRED", "message": "Sign in to continue", "fieldErrors": []}
            """;

    public static final String INVALID_PASSWORD = """
            {"code": "INVALID_PASSWORD", "message": "Current password is incorrect", "fieldErrors": []}
            """;

    public static final String CSRF_DENIED = """
            {"code": "ACCESS_DENIED", "message": "Access denied or invalid CSRF token", "fieldErrors": []}
            """;

    public static final String USERNAME_TAKEN = """
            {"code": "USERNAME_TAKEN", "message": "Display name is already taken", "fieldErrors": []}
            """;

    public static final String EMAIL_TAKEN = """
            {"code": "EMAIL_TAKEN", "message": "Email is already registered", "fieldErrors": []}
            """;
}