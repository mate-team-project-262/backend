package com.team262.moviematch.dto.error;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record ErrorResponse(
        @Schema(description = "Machine-readable code for the frontend; applicable codes are listed for each operation",
                example = "INVALID_TOKEN", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "Human-readable explanation; do not use it as a stable error identifier",
                example = "Invalid participant token", requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(description = "Field errors for VALIDATION_ERROR; an empty array for business errors",
                requiredMode = Schema.RequiredMode.REQUIRED) List<FieldErrorResponse> fieldErrors
) {
}
