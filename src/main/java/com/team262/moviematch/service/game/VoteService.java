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
        if (existing.isPresent()) {
            if (existing.get().isLiked() != request.isLiked()) {
                throw new ConflictException(ErrorCode.ALREADY_VOTED, "Saved vote cannot be changed");
            }
        } else {
            if (room.getStatus() != RoomStatus.VOTING) {
                throw new ConflictException(ErrorCode.ROOM_NOT_VOTING, "Room is not accepting votes");
            }
            votes.save(new Vote(lockedParticipant, card, request.isLiked()));
        }
        int votedCount = Math.toIntExact(
                votes.countByParticipantIdAndRoomTitleRoomId(participant.getId(), room.getId()));
        int totalCards = Math.toIntExact(roomTitles.countByRoomId(room.getId()));
        boolean finished = totalCards > 0 && votedCount == totalCards;
        if (finished) {
            results.finishIfReady(room);
        }
        return new VoteResponse(votedCount, totalCards, finished);
    }
}
