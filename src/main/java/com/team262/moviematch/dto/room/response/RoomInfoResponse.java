package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;

public record RoomInfoResponse(String code,
                               RoomStatus status,
                               int participantCount,
                               int maxParticipants) {
}