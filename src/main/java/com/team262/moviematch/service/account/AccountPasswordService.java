package com.team262.moviematch.service.account;

import com.team262.moviematch.dto.account.ChangePasswordRequest;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.BadRequestException;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountPasswordService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    /**
     * The new password's format is checked before this method (bean validation on the request: 400).
     * Here: 1. the current password matches (else 400 INVALID_CURRENT_PASSWORD),
     *       2. the new password differs from it (else 409 PASSWORD_NOT_CHANGED),
     *       3. the new hash is saved.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = users.findById(userId).orElseThrow(() ->
                new UnauthorizedException(ErrorCode.AUTHENTICATION_REQUIRED, "Sign in to continue"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect",
                    ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        // the current password is verified, so comparing the two strings is the same as comparing passwords
        // (and avoids hashing the new password just to find out it is the old one)
        if (request.newPassword().equals(request.currentPassword())) {
            throw new ConflictException(ErrorCode.PASSWORD_NOT_CHANGED,
                    "New password must differ from the current one");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        users.saveAndFlush(user);
    }
}