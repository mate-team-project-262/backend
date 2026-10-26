package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;
import java.util.List;

public record RoomDetailsResponse(
        String code,
        RoomStatus status,
        PreferencesResponse preferences,
        int participantCount,
        int maxParticipants,
        List<ParticipantDto> participants
) {
}
