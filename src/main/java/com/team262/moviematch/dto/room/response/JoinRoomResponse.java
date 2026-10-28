package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record JoinRoomResponse(
        @Schema(description = "Код комнаты", example = "K7M2QX", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "Статус комнаты, при входе LOBBY", requiredMode = Schema.RequiredMode.REQUIRED) RoomStatus status,
        @Schema(description = "Добавленный участник с личным токеном. При входе поле называется currentParticipant.",
                requiredMode = Schema.RequiredMode.REQUIRED) ParticipantWithTokenDto currentParticipant
) {
}
