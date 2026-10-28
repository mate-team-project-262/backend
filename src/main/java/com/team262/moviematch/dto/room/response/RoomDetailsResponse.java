package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record RoomDetailsResponse(
        @Schema(description = "Код комнаты", example = "K7M2QX", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "Текущий статус LOBBY, VOTING или FINISHED", requiredMode = Schema.RequiredMode.REQUIRED) RoomStatus status,
        @Schema(description = "Настройки комнаты", requiredMode = Schema.RequiredMode.REQUIRED) PreferencesResponse preferences,
        @Schema(description = "Число участников, включая хоста", example = "2", requiredMode = Schema.RequiredMode.REQUIRED) int participantCount,
        @Schema(description = "Лимит участников, по умолчанию 5", example = "5", requiredMode = Schema.RequiredMode.REQUIRED) int maxParticipants,
        @Schema(description = "Участники по возрастанию ID; без токенов", requiredMode = Schema.RequiredMode.REQUIRED) List<ParticipantDto> participants
) {
}
