package com.team262.moviematch.service.impl;

import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.service.ParticipantAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParticipantAccessServiceImpl implements ParticipantAccessService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;

    @Override
    @Transactional(readOnly = true)
    public Room requireRoomMembership(String code, Participant participant) {
        Room room = roomRepository.findByCodeIgnoreCase(code).orElseThrow(() ->
                new NotFoundException(ErrorCode.ROOM_NOT_FOUND, "Room not found"));
        checkMembership(room, participant);
        return room;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Participant requireRoomMembershipForUpdate(String code, Participant participant) {
        // Always lock the room before reading the current participant and host flag.
        Room room = roomRepository.findLockedByCodeIgnoreCase(code).orElse(null);
        if (room != null) {
            checkMembership(room, participant);
        }
        Participant current = participantRepository.findByIdForUpdate(participant.getId())
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Invalid participant token"));
        if (room == null) {
            throw new NotFoundException(ErrorCode.ROOM_NOT_FOUND, "Room not found");
        }
        return current;
    }

    private void checkMembership(Room room, Participant participant) {
        if (!room.getId().equals(participant.getRoom().getId())) {
            throw new ForbiddenException(ErrorCode.WRONG_ROOM,
                    "Participant does not belong to this room");
        }
    }
}
