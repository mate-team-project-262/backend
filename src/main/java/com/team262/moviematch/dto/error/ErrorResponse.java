package com.team262.moviematch.dto.error;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record ErrorResponse(
        @Schema(description = "Код для обработки на фронтенде; допустимые коды перечислены у конкретного эндпоинта",
                example = "INVALID_TOKEN", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "Пояснение, не используйте как стабильный идентификатор ошибки",
                example = "Invalid participant token", requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(description = "Ошибки полей при VALIDATION_ERROR; для бизнес-ошибок []",
                requiredMode = Schema.RequiredMode.REQUIRED) List<FieldErrorResponse> fieldErrors
) {
}
