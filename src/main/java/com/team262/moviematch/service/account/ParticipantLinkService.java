package com.team262.moviematch.service.account;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.User;
import com.team262.moviematch.model.history.ArchivedParticipant;
import com.team262.moviematch.model.history.GameSessionArchive;
import com.team262.moviematch.repository.GameSessionArchiveRepository;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.service.ParticipantAccessService;
import com.team262.moviematch.service.ParticipantTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class ParticipantLinkService {
    private final ParticipantTokenService tokens;
    private final ParticipantAccessService access;
    private final ParticipantRepository participants;
    private final GameSessionArchiveRepository archives;
    @Transactional
    public void link(User user, String token) {
        Participant participant = tokens.requireParticipant(token);
        Participant current = access.requireRoomMembershipForUpdate(
                participant.getRoom().getCode(), participant);
        if (current.getUser() != null) {
            if (!current.getUser().getId().equals(user.getId())) {
                throw new ConflictException(ErrorCode.PARTICIPANT_ALREADY_LINKED,
                        "Participant belongs to another account");
            }
            return;
        }
        if (participants.existsByRoomIdAndUserId(current.getRoom().getId(), user.getId())) {
            throw new ConflictException(ErrorCode.PARTICIPANT_ALREADY_LINKED,
                    "Account already has a participant in this room");
        }
        current.setUser(user);
        if (current.getRoom().getStatus() == RoomStatus.FINISHED) {
            Long roomId = current.getRoom().getId();
            if (archives.existsBySourceRoomIdAndParticipants_UserId(roomId, user.getId())) {
                throw new ConflictException(ErrorCode.PARTICIPANT_ALREADY_LINKED,
                        "Account already owns a participant in this archived session");
            }
            GameSessionArchive archive = archives.findBySourceRoomId(roomId).orElseThrow(() ->
                    new IllegalStateException("Finished session archive is missing"));
            ArchivedParticipant archivedParticipant = archive.getParticipants().stream()
                    .filter(member -> member.getSourceParticipantId().equals(current.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Participant is missing from finished session archive"));
            if (archivedParticipant.getUserId() != null) {
                throw new ConflictException(ErrorCode.PARTICIPANT_ALREADY_LINKED,
                        "Archived participant belongs to another account");
            }
            archivedParticipant.setUserId(user.getId());
        }
    }
}
