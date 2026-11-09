package com.team262.moviematch.service.history;

import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.User;
import com.team262.moviematch.model.history.ArchivedCard;
import com.team262.moviematch.model.history.ArchivedParticipant;
import com.team262.moviematch.model.history.ArchivedVote;
import com.team262.moviematch.model.history.GameSessionArchive;
import com.team262.moviematch.repository.GameSessionArchiveRepository;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.VoteRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameSessionArchiveService {
    private final GameSessionArchiveRepository archives;
    private final ParticipantRepository participants;
    private final RoomTitleRepository roomTitles;
    private final VoteRepository votes;

    @Transactional(propagation = Propagation.MANDATORY)
    public void archiveFinishedRoom(Room room) {
        if (room.getStatus() != RoomStatus.FINISHED || room.getResultParticipantCount() == null) {
            throw new IllegalStateException("Only a completed round can be archived");
        }
        if (archives.existsBySourceRoomId(room.getId())) {
            return;
        }
        List<Participant> members = participants.findAllByRoomIdOrderByIdAsc(room.getId());
        List<RoomTitle> cards = roomTitles.findAllByRoomIdOrderByPositionAsc(room.getId());
        if (members.size() != room.getResultParticipantCount() || cards.isEmpty()) {
            throw new IllegalStateException("Completed round data is incomplete");
        }
        int mostLikes = cards.stream().mapToInt(RoomTitle::getResultLikesCount).max().orElse(0);
        GameSessionArchive archive = new GameSessionArchive(
                room.getId(), room.getCode(), room.getContentType(),
                room.getStartedAt(), Instant.now(),
                members.stream().map(member -> new ArchivedParticipant(
                        member.getId(), member.getName(), member.isHost())).toList(),
                cards.stream().map(card -> new ArchivedCard(card.getId(), card.getTitle(),
                        card.getPosition(), card.getResultLikesCount(),
                        mostLikes > 0 && card.getResultLikesCount() == mostLikes)).toList());
        for (int index = 0; index < members.size(); index++) {
            User user = members.get(index).getUser();
            archive.getParticipants().get(index).setUserId(user == null ? null : user.getId());
        }
        votes.findAllByRoomTitleRoomId(room.getId()).forEach(vote ->
                archive.getVotes().add(new ArchivedVote(
                        vote.getParticipant().getId(), vote.getRoomTitle().getId(), vote.isLiked())));
        archives.save(archive);
    }
}

