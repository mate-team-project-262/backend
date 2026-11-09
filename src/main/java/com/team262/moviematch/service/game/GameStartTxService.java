package com.team262.moviematch.service.game;

import com.team262.moviematch.config.deck.DeckProperties;
import com.team262.moviematch.dto.room.response.StartGameResponse;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.model.DeckBuildStatus;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.TitleRepository;
import com.team262.moviematch.service.ParticipantAccessService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameStartTxService {
    static final int MIN_PARTICIPANTS = 2;

    private final ParticipantAccessService accessService;
    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final RoomTitleRepository roomTitleRepository;
    private final TitleRepository titleRepository;


    // Transaction 1 (short): checks conditions and takes a position (BUILDING).
    @Transactional
    public StartPlan claimStart(String code, Participant participant) {
        Participant current = accessService.requireRoomMembershipForUpdate(code, participant);
        Room room = current.getRoom();
        if (!current.isHost()) {
            throw new ForbiddenException(ErrorCode.NOT_HOST, "Only the host can start the game");
        }
        if (room.getStatus() != RoomStatus.LOBBY) {
            throw alreadyStarted();
        }
        if (participantRepository.countByRoom(room) < MIN_PARTICIPANTS) {
            throw new ConflictException(ErrorCode.NOT_ENOUGH_PARTICIPANTS,
                    "At least " + MIN_PARTICIPANTS + " participants are required");
        }
        StartPlan plan = new StartPlan(room.getId(), room.getContentType(),
                new HashSet<>(room.getGenreIds()), room.getMaxDurationMinutes());

        if (roomRepository.claimDeckBuild(room.getId()) == 0) {
            throw alreadyStarted();
        }
        return plan;
    }


    //Transaction 2 (short): room lock, encoding of 20 cards, VOTING.
    @Transactional(timeout = 10)
    public StartGameResponse completeStart(Long roomId, List<Long> titleIds) {
        if (titleIds.size() != DeckProperties.SIZE) {
            throw new IllegalStateException("Deck must have " + DeckProperties.SIZE + " titles");
        }
        Room room = roomRepository.findLockedById(roomId).orElseThrow(
                () -> new NotFoundException(ErrorCode.ROOM_NOT_FOUND, "Room not found"));
        if (room.getStatus() != RoomStatus.LOBBY
                || room.getDeckBuildStatus() != DeckBuildStatus.BUILDING) {
            throw alreadyStarted();
        }
        List<RoomTitle> cards = IntStream.range(0, titleIds.size())
                .mapToObj(position -> new RoomTitle(room,
                        titleRepository.getReferenceById(titleIds.get(position)), position))
                .toList();
        roomTitleRepository.saveAll(cards);

        room.setStatus(RoomStatus.VOTING);
        room.setDeckBuildStatus(DeckBuildStatus.READY);
        room.setStartedAt(Instant.now());
        return new StartGameResponse(room.getCode(), room.getStatus(), cards.size());
    }
    //After an error: BUILDING -> FAILED (the room remains in the LOBBY; the host can retry).
    @Transactional
    public void markBuildFailed(Long roomId) {
        roomRepository.markDeckBuildFailed(roomId);
    }

    private ConflictException alreadyStarted() {
        return new ConflictException(ErrorCode.ROOM_ALREADY_STARTED,
                "Game has already started or is being started");
    }

}
