package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateRoomResponse(
        @Schema(description = "Код для приглашения и запросов; roomId не возвращается", example = "K7M2QX",
                requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "После создания всегда LOBBY", requiredMode = Schema.RequiredMode.REQUIRED) RoomStatus status,
        @Schema(description = "Сохранённые настройки", requiredMode = Schema.RequiredMode.REQUIRED) PreferencesResponse preferences,
        @Schema(description = "Хост с личным токеном. Имя поля именно participant, не currentParticipant.",
                requiredMode = Schema.RequiredMode.REQUIRED) ParticipantWithTokenDto participant) {
}
