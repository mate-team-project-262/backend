package com.team262.moviematch.service.account;

import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.BadRequestException;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.model.PasswordResetToken;
import com.team262.moviematch.repository.PasswordResetTokenRepository;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;

    @Transactional
    public void request(String email) {
        User found = users.findByEmail(email).orElse(null);
        if (found == null) {
            return;
        }
        User user = users.findLockedById(found.getId()).orElse(null);
        if (user == null || !user.getEmail().equals(email)) {
            return;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.deleteByUserId(user.getId());
        tokens.flush();
        Instant now = Instant.now();
        tokens.save(new PasswordResetToken(user.getId(), hash(token), now, now.plusSeconds(1200)));
        events.publishEvent(new PasswordResetMailService.ResetMailRequested(
                user.getEmail(), user.getDisplayName(), token));
    }

    @Transactional(readOnly = true)
    public void check(String token) {
        requireToken(token);
    }

    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken resetToken = requireToken(token);
        Long userId = resetToken.getUserId();
        User user = users.findLockedById(userId).orElseThrow(PasswordResetService::invalidToken);
        if (resetToken.getExpiresAt().isBefore(Instant.now())
                || tokens.findByTokenHashAndExpiresAtAfter(resetToken.getTokenHash(), Instant.now()).isEmpty()) {
            throw invalidToken();
        }
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw new ConflictException(ErrorCode.PASSWORD_NOT_CHANGED,
                    "New password must differ from the current one");
        }
        user.changePasswordHash(encoder.encode(newPassword));
        user.revokeSessions();
        users.saveAndFlush(user);
        tokens.deleteByUserId(userId);
    }

    @Transactional
    public void cancel(Long userId) {
        tokens.deleteByUserId(userId);
    }

    private PasswordResetToken requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }
        return tokens.findByTokenHashAndExpiresAtAfter(hash(token), Instant.now())
                .orElseThrow(PasswordResetService::invalidToken);
    }

    private static BadRequestException invalidToken() {
        return new BadRequestException("Reset link is invalid or expired", ErrorCode.RESET_TOKEN_INVALID);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
