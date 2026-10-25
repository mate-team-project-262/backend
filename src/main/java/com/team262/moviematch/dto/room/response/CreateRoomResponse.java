package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.RoomStatus;

public record CreateRoomResponse(String code, String joinUrl, RoomStatus status,
                                 PreferencesResponse preferences,
                                 ParticipantWithTokenDto participant) {}