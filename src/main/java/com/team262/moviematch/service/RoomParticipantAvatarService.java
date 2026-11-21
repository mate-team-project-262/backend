package com.team262.moviematch.service;

import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.User;
import com.team262.moviematch.model.UserAvatar;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.UserAvatarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomParticipantAvatarService {
    private final ParticipantAccessService access;
    private final ParticipantRepository participants;
    private final UserAvatarRepository avatars;

    public static String url(Participant participant) {
        User user = participant.getUser();
        return user == null || user.getAvatarVersion() == null ? null
                : "/rooms/" + participant.getRoom().getCode() + "/participants/"
                        + participant.getId() + "/avatar?v=" + user.getAvatarVersion();
    }

    @Transactional(readOnly = true)
    public UserAvatar get(String code, Participant viewer, Long participantId) {
        Room room = access.requireRoomMembership(code, viewer);
        Participant owner = participants.findById(participantId)
                .filter(participant -> participant.getRoom().getId().equals(room.getId()))
                .orElseThrow(RoomParticipantAvatarService::notFound);
        User user = owner.getUser();
        if (user == null || user.getAvatarVersion() == null) {
            throw notFound();
        }
        return avatars.findById(user.getId()).orElseThrow(RoomParticipantAvatarService::notFound);
    }

    private static NotFoundException notFound() {
        return new NotFoundException(ErrorCode.AVATAR_NOT_FOUND, "Participant avatar not found");
    }
}
