package com.team262.moviematch.dto.room.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Участник в общем списке; токен и время его выдачи не возвращаются")
public record ParticipantDto(
        @Schema(description = "ID участника", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(description = "Отображаемое имя", example = "Anna", requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(description = "Создатель комнаты", example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean host) {
}
