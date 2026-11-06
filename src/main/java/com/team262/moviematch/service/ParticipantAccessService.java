package com.team262.moviematch.service;

import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;

public interface ParticipantAccessService {

    Room requireRoomMembership(String code, Participant participant);

    Participant requireRoomMembershipForUpdate(String code, Participant participant);
}
