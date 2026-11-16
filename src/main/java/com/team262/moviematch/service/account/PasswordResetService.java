package com.team262.moviematch.service.account;

import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.BadRequestException;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ResetQueueFullException;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ThreadPoolExecutor QUEUE = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100),
            task -> {
                Thread thread = new Thread(task, "password-reset-mail");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PasswordEncoder encoder;
    private final PasswordResetMailService mail;

    public void request(String email) {
        try {
            QUEUE.execute(() -> {
                try {
                    String token = transactions.execute(status -> prepare(email));
                    if (token != null) {
                        User user = users.findByEmail(email).orElse(null);
                        if (user != null) {
                            mail.send(user.getEmail(), user.getDisplayName(), token);
                        }
                    }
                } catch (Exception exception) {
                    LOGGER.error("Password reset delivery failed; check mail configuration", exception.getClass().getName());
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            throw new ResetQueueFullException();
        }
    }

    private String prepare(String email) {
        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            return null;
        }
        User user = users.findLockedById(found.get().getId()).orElse(null);
        if (user == null || !user.getEmail().equals(email)) {
            return null;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", user.getId());
        jdbc.update("INSERT INTO password_reset_tokens (user_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?)",
                user.getId(), hash(token), java.sql.Timestamp.from(Instant.now()),
                java.sql.Timestamp.from(Instant.now().plusSeconds(1200)));
        return token;
    }

    public void check(String token) {
        requireToken(token);
    }

    public void reset(String token, String newPassword) {
        transactions.executeWithoutResult(status -> {
            Long userId = requireToken(token);
            User user = users.findLockedById(userId).orElseThrow(PasswordResetService::invalidToken);
            if (!requireToken(token).equals(user.getId())) {
                throw invalidToken();
            }
            if (encoder.matches(newPassword, user.getPasswordHash())) {
                throw new ConflictException(ErrorCode.PASSWORD_NOT_CHANGED,
                        "New password must differ from the current one");
            }
            user.changePasswordHash(encoder.encode(newPassword));
            user.revokeSessions();
            users.saveAndFlush(user);
            jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
        });
    }

    public void cancel(Long userId) {
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
    }

    private Long requireToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidToken();
        }
        Long userId = jdbc.query("SELECT user_id FROM password_reset_tokens WHERE token_hash = ? AND expires_at > ?",
                result -> result.next() ? result.getLong(1) : null,
                hash(token), java.sql.Timestamp.from(Instant.now()));
        if (userId == null) {
            throw invalidToken();
        }
        return userId;
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
