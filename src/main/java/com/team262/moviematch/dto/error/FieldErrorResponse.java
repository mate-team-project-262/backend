package com.team262.moviematch.dto.error;

import io.swagger.v3.oas.annotations.media.Schema;

public record FieldErrorResponse(
        @Schema(description = "Request field, including its nested path", example = "preferences.maxDurationMinutes",
                requiredMode = Schema.RequiredMode.REQUIRED) String field,
        @Schema(description = "Field validation message", example = "must be less than or equal to 150",
                requiredMode = Schema.RequiredMode.REQUIRED) String message
) {
}
