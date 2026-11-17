package com.team262.moviematch.service.game;

import com.team262.moviematch.dto.game.VoteRequest;
import com.team262.moviematch.dto.game.VoteResponse;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.BadRequestException;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.Vote;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.VoteRepository;
import com.team262.moviematch.service.ParticipantAccessService;
import com.team262.moviematch.service.analytics.AnalyticsRecorder;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteService {
    private final ParticipantAccessService accessService;
    private final RoomTitleRepository roomTitles;
    private final VoteRepository votes;
    private final GameResultService results;
    private final AnalyticsRecorder analytics;

    @Transactional
    public VoteResponse vote(String code, Participant participant, VoteRequest request) {
        Participant lockedParticipant = accessService.requireRoomMembershipForUpdate(code, participant);
        Room room = lockedParticipant.getRoom();
        if (room.getStatus() == RoomStatus.LOBBY) {
            throw new ConflictException(ErrorCode.ROOM_NOT_VOTING, "Room is not accepting votes");
        }
        RoomTitle card = roomTitles.findByIdAndRoomId(request.roomTitleId(), room.getId())
                .orElseThrow(() -> new BadRequestException("Title is not in this room's deck",
                        ErrorCode.TITLE_NOT_IN_DECK));
        Optional<Vote> existing = votes.findByParticipantIdAndRoomTitleId(participant.getId(), card.getId());
        boolean created = false;
        if (existing.isPresent()) {
            if (existing.get().isLiked() != request.isLiked()) {
                throw new ConflictException(ErrorCode.ALREADY_VOTED, "Saved vote cannot be changed");
            }
        } else {
            if (room.getStatus() != RoomStatus.VOTING) {
                throw new ConflictException(ErrorCode.ROOM_NOT_VOTING, "Room is not accepting votes");
            }
            votes.save(new Vote(lockedParticipant, card, request.isLiked()));
            created = true;
        }
        int votedCount = Math.toIntExact(
                votes.countByParticipantIdAndRoomTitleRoomId(participant.getId(), room.getId()));
        int totalCards = Math.toIntExact(roomTitles.countByRoomId(room.getId()));
        boolean finished = totalCards > 0 && votedCount == totalCards;
        if (created) {
            // only a NEW vote is a progress step: repeating a saved vote must not repeat the event
            recordProgress(room, lockedParticipant, votedCount, totalCards, finished);
        }
        if (finished) {
            results.finishIfReady(room);
        }
        return new VoteResponse(votedCount, totalCards, finished);
    }

    /** Events are published here and written after the commit (see AnalyticsBackendEventListener). */
    private void recordProgress(Room room, Participant participant, int votedCount, int totalCards,
                                boolean finished) {
        if (votedCount == 1) {
            analytics.swipeSessionStarted(room, participant, totalCards);
        }
        if (finished) {
            List<Vote> mine = votes.findAllByParticipantIdAndRoomTitleRoomId(participant.getId(), room.getId());
            long liked = mine.stream().filter(Vote::isLiked).count();
            Long durationSeconds = mine.stream().map(Vote::getCreatedAt).min(Comparator.naturalOrder())
                    .map(first -> Duration.between(first, Instant.now()).toSeconds())
                    .orElse(null);
            analytics.swipeSessionCompleted(room, participant, totalCards, liked, durationSeconds);
        }
    }
}