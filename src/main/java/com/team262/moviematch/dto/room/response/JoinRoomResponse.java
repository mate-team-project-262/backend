package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;

public record JoinRoomResponse(
        String code,
        RoomStatus status,
        ParticipantWithTokenDto currentParticipant
) {
}
