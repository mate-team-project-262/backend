package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;

public record CreateRoomResponse(String code,
                                 RoomStatus status,
                                 PreferencesResponse preferences,
                                 ParticipantWithTokenDto participant) {
}