package com.team262.moviematch.dto.error;

import io.swagger.v3.oas.annotations.media.Schema;

public record FieldErrorResponse(
        @Schema(description = "Request field, including its nested path",
                requiredMode = Schema.RequiredMode.REQUIRED) String field,
        @Schema(description = "Field validation message",
                requiredMode = Schema.RequiredMode.REQUIRED) String message
) {
}
