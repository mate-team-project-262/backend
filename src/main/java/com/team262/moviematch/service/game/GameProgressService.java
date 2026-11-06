package com.team262.moviematch.service.game;

import com.team262.moviematch.dto.game.GameProgressResponse;
import com.team262.moviematch.dto.game.ParticipantProgressDto;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.ParticipantVoteCount;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.VoteRepository;
import com.team262.moviematch.service.ParticipantAccessService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameProgressService {

    private final ParticipantAccessService accessService;
    private final ParticipantRepository participantRepository;
    private final RoomTitleRepository roomTitleRepository;
    private final VoteRepository voteRepository;

    /*
     * Order of errors: 401 (token resolver) -> 404 ROOM_NOT_FOUND -> 403 WRONG_ROOM
     * -> 409 ROOM_NOT_VOTING (room is in LOBBY state).
     * VOTING and FINISHED states return a standard response.
     * This method does not save any data or change the room's status.
     */
    @Transactional(readOnly = true)
    public GameProgressResponse getProgress(String code, Participant current) {

        // Verify that:
        // 1. Room exists
        // 2. Current participant belongs to the room
        // Otherwise appropriate exceptions are thrown (404, 403, etc.)
        Room room = accessService.requireRoomMembership(code, current);

        // Progress endpoint is available only after voting has started.
        // Lobby means the game has not started yet.
        if (room.getStatus() == RoomStatus.LOBBY) {
            throw new ConflictException(
                    ErrorCode.ROOM_NOT_VOTING,
                    "Game has not started yet"
            );
        }

        // Count how many movie cards/titles exist in the room.
        // This value is later used to determine whether a participant
        // has completed voting on all available titles.
        long totalCards = roomTitleRepository.countByRoomId(room.getId());

        // Load all room participants sorted by ID.
        // Sorting guarantees stable ordering in the response.
        List<Participant> participants =
                participantRepository.findAllByRoomIdOrderByIdAsc(room.getId());

        // Fetch vote counts for all participants in the room and convert them into a map:
        // participantId -> numberOfVotes
        // Example:
        // {
        //    1 -> 10,
        //    2 -> 7,
        //    3 -> 10
        // }
        //
        // Using a map allows constant-time lookup later.
        Map<Long, Long> votesByParticipant = voteRepository
                .countVotesByParticipantInRoom(room.getId())
                .stream()
                .collect(Collectors.toMap(
                        ParticipantVoteCount::participantId,
                        ParticipantVoteCount::votes
                ));

        // Build progress DTO for every participant.
        List<ParticipantProgressDto> progress = participants.stream()

                .map(p -> new ParticipantProgressDto(
                        p.getId(),
                        p.getName(),
                        p.isHost(),

                        // Determine whether participant finished voting.
                        // getOrDefault(...) protects against missing entries:
                        // if participant has no votes yet, use 0 instead.
                        //
                        // Example:
                        // votes = 10
                        // totalCards = 10
                        // -> finished = true
                        isFinished(
                                votesByParticipant.getOrDefault(
                                        p.getId(),
                                        0L),
                                totalCards)))
                .toList();

        // Count participants that finished voting.
        //
        // Example:
        // Adam  -> true
        // Kate  -> false
        // John  -> true
        //
        // Result:
        // finishedCount = 2
        int finishedCount = (int) progress.stream()
                .filter(ParticipantProgressDto::finished)
                .count();

        // Check whether the current participant has finished voting.
        boolean myFinished = progress.stream()
                .anyMatch(p ->
                        p.id().equals(current.getId())
                                && p.finished()
                );

        // Determine whether everybody finished voting.
        //
        // progress.isEmpty() guard prevents an empty room from being
        // considered "fully completed".
        boolean allFinished =
                !progress.isEmpty()
                        && finishedCount == progress.size();

        // Return aggregated game progress information.
        return new GameProgressResponse(
                finishedCount,      // participants who finished
                progress.size(),    // total participants
                myFinished,         // current participant finished?
                allFinished,        // everyone finished?
                progress            // detailed participant progress
        );
    }

    /**
     * Participant is considered finished when:
     * - there is at least one card in the room
     * - participant has voted on every available card
     * Examples:
     * votes=10, totalCards=10 -> true
     * votes=7, totalCards=10  -> false
     * votes=0, totalCards=0   -> false
     */
    private static boolean isFinished(long votes, long totalCards) {
        return totalCards > 0 && votes >= totalCards;
    }
}