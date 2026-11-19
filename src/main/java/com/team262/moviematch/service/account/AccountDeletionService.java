package com.team262.moviematch.service.account;

import com.team262.moviematch.dto.account.DeleteAccountRequest;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.BadRequestException;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository users;
    private final ParticipantRepository participants;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService resets;

    /**
     * Removes the account while preserving shared game data. Active participants become guests;
     * archived participants are anonymized by the database FK with ON DELETE SET NULL.
     */
    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = users.findById(userId).orElseThrow(() ->
                new UnauthorizedException(ErrorCode.AUTHENTICATION_REQUIRED, "Sign in to continue"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect",
                    ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        List<Participant> linkedParticipants = participants.findAllByUserId(userId);
        resets.cancel(userId);
        linkedParticipants.forEach(participant -> participant.setUser(null));
        participants.flush();

        users.delete(user);
        users.flush();
    }
}
