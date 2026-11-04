package com.team262.moviematch.service.impl;

import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.service.ParticipantAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParticipantAccessServiceImpl implements ParticipantAccessService {

    private final RoomRepository roomRepository;

    @Override
    @Transactional(readOnly = true)
    public Room requireRoomMembership(String code, Participant participant) {
        Room room = roomRepository.findByCode(code).orElseThrow(() ->
                new NotFoundException(ErrorCode.ROOM_NOT_FOUND, "Room not found"));
        if (!room.getId().equals(participant.getRoom().getId())) {
            throw new ForbiddenException(ErrorCode.WRONG_ROOM,
                    "Participant does not belong to this room");
        }
        return room;
    }
}
