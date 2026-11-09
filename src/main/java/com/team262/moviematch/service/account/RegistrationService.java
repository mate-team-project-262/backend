package com.team262.moviematch.service.account;
import com.team262.moviematch.dto.account.RegisterRequest;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class RegistrationService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    @Transactional
    public User register(RegisterRequest request) {
        if (users.findByEmail(request.email()).isPresent()) {
            throw new ConflictException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
        }
        if (users.existsByDisplayNameIgnoreCase(request.displayName())) {
            throw new ConflictException(ErrorCode.USERNAME_TAKEN, "Name is already registered");
        }
        try {
            return users.saveAndFlush(new User(request.email(),
                    encoder.encode(request.password()), request.displayName()));
        } catch (DataIntegrityViolationException exception) {
            String message = exception.getMostSpecificCause().getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT)
                    .contains("uk_users_display_name")) {
                throw new ConflictException(ErrorCode.USERNAME_TAKEN, "Name is already registered");
            }
            if (message == null || !message.toLowerCase(java.util.Locale.ROOT).contains("uk_users_email")) {
                throw exception;
            }
            throw new ConflictException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
        }
    }
}

