package com.team262.moviematch.service;

import com.team262.moviematch.model.Participant;

public interface ParticipantTokenService {
    String issueToken(Participant participant);

    Participant requireParticipant(String token);
}