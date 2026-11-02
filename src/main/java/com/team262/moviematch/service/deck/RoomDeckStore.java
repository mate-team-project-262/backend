package com.team262.moviematch.service.deck;

import com.team262.moviematch.config.deck.DeckProperties;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.mapper.TitleMapper;
import com.team262.moviematch.model.DeckBuildStatus;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.Title;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.TitleRepository;
import com.team262.moviematch.source.DiscoverFilters;
import com.team262.moviematch.source.TmdbTitle;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Short independent transactions only; never invokes the title source. */
@Component
public class RoomDeckStore {
    private final RoomRepository rooms;
    private final ParticipantRepository participants;
    private final RoomTitleRepository decks;
    private final TitleRepository titles;
    private final TitleMapper mapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public RoomDeckStore(RoomRepository rooms, ParticipantRepository participants,
                          RoomTitleRepository decks, TitleRepository titles, TitleMapper mapper,
                          JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.rooms = rooms;
        this.participants = participants;
        this.decks = decks;
        this.titles = titles;
        this.mapper = mapper;
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // After waiting for a shared title's row lock, collection reads must also see the winner.
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
    }

    public Access inspect(String code, long participantId, boolean hostRequired) {
        return transaction.execute(status -> {
            Room room = find(code);
            authorize(room, participantId, hostRequired);
            return new Access(room.getId(), readyIds(room));
        });
    }

    public Claim claim(String code, long participantId, Duration remaining) {
        return transaction.execute(status -> {
            Room room = rooms.findByCodeForUpdate(code).orElseThrow(() ->
                    new NotFoundException(ErrorCode.ROOM_NOT_FOUND, "Room not found"));
            authorize(room, participantId, true);
            List<Long> existing = readyIds(room);
            if (!existing.isEmpty()) {
                return new Claim(room.getId(), null, room.getContentType(), filters(room), existing);
            }
            if (room.getStatus() != RoomStatus.LOBBY) {
                throw new ConflictException(ErrorCode.ROOM_NOT_JOINABLE, "Room is not in the lobby");
            }
            if (room.getDeckBuildStatus() == DeckBuildStatus.BUILDING
                    && room.getDeckBuildExpiresAt() != null
                    && room.getDeckBuildExpiresAt().isAfter(Instant.now())) {
                throw new ConflictException(ErrorCode.DECK_BUILD_IN_PROGRESS, "Deck preparation is in progress");
            }
            String token = UUID.randomUUID().toString();
            room.setDeckBuildStatus(DeckBuildStatus.BUILDING);
            room.setDeckBuildToken(token);
            room.setDeckBuildExpiresAt(Instant.now().plus(remaining).plusSeconds(5));
            return new Claim(room.getId(), token, room.getContentType(), filters(room), List.of());
        });
    }

    public List<Long> complete(Claim claim, List<TmdbTitle> cards, DeckDeadline deadline) {
        return transaction.execute(status -> {
            deadline.check();
            Room room = rooms.findByIdForUpdate(claim.roomId()).orElseThrow(() ->
                    new NotFoundException(ErrorCode.ROOM_NOT_FOUND, "Room not found"));
            if (!Objects.equals(claim.token(), room.getDeckBuildToken())
                    || room.getDeckBuildStatus() != DeckBuildStatus.BUILDING
                    || room.getStatus() != RoomStatus.LOBBY
                    || room.getContentType() != claim.contentType()
                    || !filters(room).equals(claim.filters())
                    || room.getDeckBuildExpiresAt() == null
                    || !room.getDeckBuildExpiresAt().isAfter(Instant.now())) {
                throw new ConflictException(ErrorCode.DECK_BUILD_STALE, "Deck preparation is no longer current");
            }
            if (cards.size() != DeckProperties.SIZE
                    || cards.stream().map(card -> card.mediaType() + ":" + card.tmdbId()).distinct().count()
                            != DeckProperties.SIZE || !decks.findSlotsByRoomId(room.getId()).isEmpty()) {
                throw new IllegalStateException("Expected one complete, unique deck for an empty room");
            }
            var titleIds = new HashMap<String, Long>();
            // Consistent lock order across rooms prevents common cross-title deadlocks.
            List<TmdbTitle> sorted = cards.stream()
                    .sorted(Comparator.comparing((TmdbTitle card) -> card.mediaType().name())
                            .thenComparing(TmdbTitle::tmdbId)).toList();
            for (TmdbTitle card : sorted) {
                deadline.check();
                jdbc.update("""
                        INSERT INTO titles (tmdb_id, media_type, title)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
                        """, card.tmdbId(), card.mediaType().name(), card.title());
                // A locking read sees a concurrent upsert winner even under REPEATABLE READ.
                Title title = titles.findForUpdate(card.tmdbId(), card.mediaType()).orElseThrow();
                mapper.update(card, title);
                titleIds.put(key(card), title.getId());
            }
            List<Long> ordered = new ArrayList<>();
            for (int position = 0; position < cards.size(); position++) {
                Long id = titleIds.get(key(cards.get(position)));
                decks.save(new RoomTitle(room, titles.getReferenceById(id), position));
                ordered.add(id);
            }
            decks.flush();
            deadline.check();
            room.setDeckBuildStatus(DeckBuildStatus.READY);
            room.setDeckBuildToken(null);
            room.setDeckBuildExpiresAt(null);
            return List.copyOf(ordered);
        });
    }

    public void fail(Claim claim) {
        transaction.executeWithoutResult(status -> {
            rooms.findByIdForUpdate(claim.roomId()).ifPresent(room -> {
                if (Objects.equals(claim.token(), room.getDeckBuildToken())
                        && room.getDeckBuildStatus() == DeckBuildStatus.BUILDING) {
                    room.setDeckBuildStatus(DeckBuildStatus.FAILED);
                    room.setDeckBuildToken(null);
                    room.setDeckBuildExpiresAt(null);
                }
            });
        });
    }

    private List<Long> readyIds(Room room) {
        List<RoomTitleRepository.Slot> cards = decks.findSlotsByRoomId(room.getId());
        if (cards.isEmpty() && room.getDeckBuildStatus() != DeckBuildStatus.READY) {
            return List.of();
        }
        if (cards.size() != DeckProperties.SIZE) {
            throw new IllegalStateException("Stored room deck is incomplete");
        }
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getPosition() != i) {
                throw new IllegalStateException("Stored room deck positions are invalid");
            }
        }
        return cards.stream().map(RoomTitleRepository.Slot::getTitleId).toList();
    }

    private Room find(String code) {
        return rooms.findByCode(code).orElseThrow(() ->
                new NotFoundException(ErrorCode.ROOM_NOT_FOUND, "Room not found"));
    }

    private void authorize(Room room, long participantId, boolean hostRequired) {
        Participant actor = participants.findById(participantId).orElseThrow(() ->
                new ForbiddenException(ErrorCode.WRONG_ROOM, "Participant does not belong to this room"));
        if (!actor.getRoom().getId().equals(room.getId())) {
            throw new ForbiddenException(ErrorCode.WRONG_ROOM, "Participant does not belong to this room");
        }
        if (hostRequired && !actor.isHost()) {
            throw new ForbiddenException(ErrorCode.NOT_HOST, "Only the host can prepare the deck");
        }
    }

    private DiscoverFilters filters(Room room) {
        return new DiscoverFilters(room.getGenreIds(), room.getMaxDurationMinutes(), null);
    }

    private String key(TmdbTitle card) {
        return card.mediaType() + ":" + card.tmdbId();
    }

    public record Access(Long roomId, List<Long> titleIds) {
    }

    public record Claim(Long roomId, String token,
                        com.team262.moviematch.model.ContentType contentType,
                        DiscoverFilters filters, List<Long> titleIds) {
    }
}
