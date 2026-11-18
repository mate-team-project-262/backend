package com.team262.moviematch.config.openapi;

public final class PasswordApiExamples {

    private PasswordApiExamples() {
    }

    public static final String REQUEST = """
            {"currentPassword": "oldPassword123", "newPassword": "newPassword456"}
            """;

    public static final String WEAK_PASSWORD = """
            {"code": "VALIDATION_ERROR", "message": "Validation failed",
             "fieldErrors": [{"field": "newPassword", "message": "must contain at least one letter and one digit"}]}
            """;

    public static final String TOO_SHORT = """
            {"code": "VALIDATION_ERROR", "message": "Validation failed",
             "fieldErrors": [{"field": "newPassword", "message": "size must be between 8 and 72"}]}
            """;

    public static final String INVALID_CURRENT_PASSWORD = """
            {"code": "INVALID_CURRENT_PASSWORD", "message": "Current password is incorrect", "fieldErrors": []}
            """;

    public static final String PASSWORD_NOT_CHANGED = """
            {"code": "PASSWORD_NOT_CHANGED", "message": "New password must differ from the current one", "fieldErrors": []}
            """;

    public static final String AUTHENTICATION_REQUIRED = """
            {"code": "AUTHENTICATION_REQUIRED", "message": "Sign in to continue", "fieldErrors": []}
            """;

    public static final String CSRF_DENIED = """
            {"code": "ACCESS_DENIED", "message": "Access denied or invalid CSRF token", "fieldErrors": []}
            """;
}