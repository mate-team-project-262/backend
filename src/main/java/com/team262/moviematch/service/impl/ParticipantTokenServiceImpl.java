package com.team262.moviematch.service.impl;

import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.service.ParticipantTokenService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParticipantTokenServiceImpl implements ParticipantTokenService {

    private final ParticipantRepository participantRepository;
    private static final long TOKEN_VALIDITY_HOURS = 24;

    @Override
    public String generateToken() {
        return UUID.randomUUID().toString();
    }

    @Override
    @Transactional
    public String issueToken(Participant participant) {
        String token = UUID.randomUUID().toString();
        participant.setToken(token);
        participant.setTokenCreatedAt(LocalDateTime.now());
        participantRepository.save(participant);
        return token;
    }

    @Override
    public Participant requireParticipant(String token) {
        Participant participant = participantRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_TOKEN,
                        "Invalid participant token"));

        if (participant.getTokenCreatedAt() != null &&
                participant.getTokenCreatedAt().plusHours(TOKEN_VALIDITY_HOURS)
                        .isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Participant token has expired");
        }
        return participant;
    }
}