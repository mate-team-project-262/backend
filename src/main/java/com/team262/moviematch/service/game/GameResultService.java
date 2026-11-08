package com.team262.moviematch.service.game;

import com.team262.moviematch.dto.game.GameResultsResponse;
import com.team262.moviematch.dto.game.ResultCardResponse;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.mapper.DeckCardMapper;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.repository.CardLikeCount;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.ParticipantVoteCount;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.VoteRepository;
import com.team262.moviematch.service.ParticipantAccessService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameResultService {
    private final ParticipantAccessService accessService;
    private final ParticipantRepository participants;
    private final RoomTitleRepository roomTitles;
    private final VoteRepository votes;
    private final DeckCardMapper mapper;

    // Callers hold the room lock through the vote or leave transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    public void finishIfReady(Room room) {
        if (room.getStatus() != RoomStatus.VOTING) {
            return;
        }
        long cardCount = roomTitles.countByRoomId(room.getId());
        long participantCount = participants.countByRoom(room);
        if (cardCount == 0 || participantCount == 0) {
            return;
        }
        List<ParticipantVoteCount> voteCounts = votes.countVotesByParticipantInRoom(room.getId());
        if (voteCounts.size() != participantCount
                || voteCounts.stream().anyMatch(count -> count.votes() != cardCount)) {
            return;
        }

        Map<Long, Long> likes = votes.countLikesByCardInRoom(room.getId()).stream()
                .collect(Collectors.toMap(CardLikeCount::roomTitleId, CardLikeCount::likesCount));
        for (RoomTitle card : roomTitles.findAllByRoomIdOrderByPositionAsc(room.getId())) {
            card.setResultLikesCount(Math.toIntExact(likes.getOrDefault(card.getId(), 0L)));
        }
        room.setResultParticipantCount(Math.toIntExact(participantCount));
        room.setStatus(RoomStatus.FINISHED);
    }

    @Transactional(readOnly = true)
    public GameResultsResponse getResults(String code, Participant participant) {
        Room room = accessService.requireRoomMembership(code, participant);
        if (room.getStatus() != RoomStatus.FINISHED || room.getResultParticipantCount() == null) {
            throw new ConflictException(ErrorCode.RESULT_NOT_READY, "Results are not ready");
        }
        List<RoomTitle> cards = roomTitles.findAllByRoomIdOrderByPositionAsc(room.getId());
        int mostLikes = cards.stream().mapToInt(RoomTitle::getResultLikesCount).max().orElse(0);
        List<ResultCardResponse> results = cards.stream()
                .sorted(Comparator.comparingInt(RoomTitle::getResultLikesCount).reversed()
                        .thenComparingInt(RoomTitle::getPosition))
                .map(card -> new ResultCardResponse(mapper.toDetails(card), card.getResultLikesCount(),
                        mostLikes > 0 && card.getResultLikesCount() == mostLikes))
                .toList();
        return new GameResultsResponse(room.getResultParticipantCount(), results);
    }
}
