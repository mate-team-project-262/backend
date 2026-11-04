package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;

public record StartGameResponse(
        String code,
        RoomStatus status,
        int deckSize
) {
}
