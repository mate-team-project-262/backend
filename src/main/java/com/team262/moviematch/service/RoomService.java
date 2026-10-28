package com.team262.moviematch.service;

import com.team262.moviematch.dto.room.request.CreateRoomRequest;
import com.team262.moviematch.dto.room.request.JoinRoomRequest;
import com.team262.moviematch.dto.room.response.CreateRoomResponse;
import com.team262.moviematch.dto.room.response.JoinRoomResponse;
import com.team262.moviematch.dto.room.response.RoomDetailsResponse;
import com.team262.moviematch.model.Participant;

public interface RoomService {
    CreateRoomResponse createRoom(CreateRoomRequest request);

    RoomDetailsResponse getLobby(String code, Participant participant);

    JoinRoomResponse joinRoom(String code, JoinRoomRequest request);
}
