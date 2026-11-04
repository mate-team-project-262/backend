package com.team262.moviematch.service.game;

import com.team262.moviematch.dto.game.DeckCardResponse;
import com.team262.moviematch.dto.game.DeckResponse;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.mapper.DeckCardMapper;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.Vote;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.VoteRepository;
import com.team262.moviematch.service.ParticipantAccessService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeckReadService {
    private final ParticipantAccessService accessService;
    private final RoomTitleRepository roomTitles;
    private final VoteRepository votes;
    private final DeckCardMapper mapper;

    @Transactional(readOnly = true)
    public DeckResponse getDeck(String code, Participant participant) {
        Room room = accessService.requireRoomMembership(code, participant);
        if (room.getStatus() == RoomStatus.LOBBY) {
            throw new ConflictException(ErrorCode.ROOM_NOT_VOTING, "Game has not started");
        }
        List<RoomTitle> deck = roomTitles.findAllByRoomIdOrderByPositionAsc(room.getId());
        Map<Long, Boolean> myVotes = new HashMap<>();
        for (Vote vote : votes.findAllByParticipantIdAndRoomTitleRoomId(participant.getId(), room.getId())) {
            myVotes.put(vote.getRoomTitle().getId(), vote.isLiked());
        }
        List<DeckCardResponse> cards = new ArrayList<>();
        for (RoomTitle card : deck) {
            cards.add(mapper.toResponse(card, myVotes.get(card.getId())));
        }
        return new DeckResponse(room.getCode(), room.getStatus(), room.getContentType(),
                cards.size(), myVotes.size(), cards);
    }
}
