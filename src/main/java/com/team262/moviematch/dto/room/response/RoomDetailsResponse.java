package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record RoomDetailsResponse(
        @Schema(description = "Room code", example = "K7M2QX", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "Current status: LOBBY, VOTING or FINISHED", requiredMode = Schema.RequiredMode.REQUIRED) RoomStatus status,
        @Schema(description = "Room preferences", requiredMode = Schema.RequiredMode.REQUIRED) PreferencesResponse preferences,
        @Schema(description = "Participant count, including the host", example = "2", requiredMode = Schema.RequiredMode.REQUIRED) int participantCount,
        @Schema(description = "Participant limit; defaults to 5", example = "5", requiredMode = Schema.RequiredMode.REQUIRED) int maxParticipants,
        @Schema(description = "Participants ordered by ID, without tokens", requiredMode = Schema.RequiredMode.REQUIRED) List<ParticipantDto> participants
) {
}
