package com.team262.moviematch.config.openapi;

import io.swagger.v3.oas.annotations.media.Schema;

/** Documentation of Spring Boot's default error body, not a new application response DTO. */
@Schema(description = "Spring's default error body for malformed JSON/HTTP and technical failures. "
        + "No business code is provided; additional fields depend on server configuration.")
public record FrameworkErrorResponse(
        @Schema(description = "Error timestamp", format = "date-time") String timestamp,
        @Schema(description = "HTTP status", example = "400") int status,
        @Schema(description = "HTTP error name", example = "Bad Request") String error,
        @Schema(description = "Request path", example = "/rooms") String path) {
}
