package com.team262.moviematch.config.openapi;

public final class AccountDeletionApiExamples {

    private AccountDeletionApiExamples() {
    }

    public static final String REQUEST = """
            {"currentPassword": "password123"}
            """;

    public static final String INVALID_CURRENT_PASSWORD = """
            {"code": "INVALID_CURRENT_PASSWORD", "message": "Current password is incorrect", "fieldErrors": []}
            """;

    public static final String AUTHENTICATION_REQUIRED = """
            {"code": "AUTHENTICATION_REQUIRED", "message": "Sign in to continue", "fieldErrors": []}
            """;

    public static final String CSRF_DENIED = """
            {"code": "ACCESS_DENIED", "message": "Access denied or invalid CSRF token", "fieldErrors": []}
            """;
}
