package com.team262.moviematch.service.impl;

import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.service.RoomDeckService;
import com.team262.moviematch.service.deck.DeckCandidateSelector;
import com.team262.moviematch.service.deck.RoomDeckStore;
import com.team262.moviematch.source.DiscoverFilters;
import com.team262.moviematch.source.TmdbTitle;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomDeckServiceImpl implements RoomDeckService {
    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final RoomDeckStore deckStore;
    private final DeckCandidateSelector selector;

    public RoomDeckServiceImpl(RoomRepository roomRepository,
                               ParticipantRepository participantRepository,
                               RoomDeckStore deckStore, DeckCandidateSelector selector) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.deckStore = deckStore;
        this.selector = selector;
    }

    @Override
    @Transactional
    public List<Long> prepare(String code, long participantId) {
        Room room = findRoomByCode(code);
        return prepareDeck(room, participantId);
    }

    @Override
    @Transactional
    public List<Long> prepare(Long roomId, long participantId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() ->
                new NotFoundException(ErrorCode.ROOM_NOT_FOUND, "Room not found"));
        return prepareDeck(room, participantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getTitleIds(String code, long participantId) {
        Room room = findRoomByCode(code);
        validateParticipant(room, participantId, false);
        List<Long> titleIds = deckStore.getTitleIds(room.getId());
        if (titleIds.isEmpty()) {
            throw new ConflictException(ErrorCode.RESULT_NOT_READY, "Room deck is not ready");
        }
        return titleIds;
    }

    private List<Long> prepareDeck(Room room, long participantId) {
        validateParticipant(room, participantId, true);
        List<Long> titleIds = deckStore.getTitleIds(room.getId());
        if (!titleIds.isEmpty()) {
            return titleIds;
        }
        if (room.getStatus() != RoomStatus.LOBBY) {
            throw new ConflictException(ErrorCode.ROOM_NOT_JOINABLE, "Room is not in the lobby");
        }
        DiscoverFilters filters = new DiscoverFilters(
                room.getGenreIds(), room.getMaxDurationMinutes(), null);
        List<TmdbTitle> cards = selector.select(room.getContentType(), filters, new Random());
        return deckStore.save(room, cards);
    }

    private Room findRoomByCode(String code) {
        return roomRepository.findByCode(code).orElseThrow(() ->
                new NotFoundException(ErrorCode.ROOM_NOT_FOUND, "Room not found"));
    }

    private void validateParticipant(Room room, long participantId, boolean hostRequired) {
        Participant participant = participantRepository.findById(participantId).orElseThrow(() ->
                new ForbiddenException(ErrorCode.WRONG_ROOM, "Participant does not belong to this room"));
        if (!participant.getRoom().getId().equals(room.getId())) {
            throw new ForbiddenException(ErrorCode.WRONG_ROOM, "Participant does not belong to this room");
        }
        if (hostRequired && !participant.isHost()) {
            throw new ForbiddenException(ErrorCode.NOT_HOST, "Only the host can prepare the deck");
        }
    }
}
