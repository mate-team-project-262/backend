package com.team262.moviematch.dto.room.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Текущий участник с его личным секретным токеном; не используется для общего списка")
public record ParticipantWithTokenDto(
        @Schema(description = "ID участника", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(description = "Отображаемое имя", example = "Anna", requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(description = "Хост комнаты", example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean host,
        @Schema(description = "Токен на 24 часа. Передавайте в X-Participant-Token без Bearer; не делитесь им.",
                format = "uuid", example = "3f2b8c1e-5a4d-4e7b-9c2a-1d6f0b7e8a90",
                requiredMode = Schema.RequiredMode.REQUIRED) String token) {
}
