package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateRoomResponse(
        @Schema(description = "Room code used for invitations and requests; roomId is not returned", example = "K7M2QX",
                requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "Always LOBBY after creation", requiredMode = Schema.RequiredMode.REQUIRED) RoomStatus status,
        @Schema(description = "Saved preferences", requiredMode = Schema.RequiredMode.REQUIRED) PreferencesResponse preferences,
        @Schema(description = "Host with their personal token. This field is named participant, not currentParticipant.",
                requiredMode = Schema.RequiredMode.REQUIRED) ParticipantWithTokenDto participant) {
}
