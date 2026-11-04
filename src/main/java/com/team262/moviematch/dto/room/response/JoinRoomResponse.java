package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record JoinRoomResponse(
        @Schema(description = "Room code", example = "K7M2QX", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "Room status; LOBBY when joining", requiredMode = Schema.RequiredMode.REQUIRED) RoomStatus status,
        @Schema(description = "The added participant with their personal token. Joining uses the currentParticipant field.",
                requiredMode = Schema.RequiredMode.REQUIRED) ParticipantWithTokenDto currentParticipant
) {
}
