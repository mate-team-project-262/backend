package com.team262.moviematch.service.account;

import com.team262.moviematch.dto.account.UpdateAccountRequest;
import com.team262.moviematch.dto.error.FieldErrorResponse;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.BadRequestException;
import com.team262.moviematch.exception.ex.RequestValidationException;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountProfileService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    /**
     * Order of checks (it is part of the contract):
     * 1. at least one field sent                        -> 400 VALIDATION_ERROR
     * 2. email changes and no currentPassword           -> 400 VALIDATION_ERROR (field currentPassword)
     * 3. email changes and currentPassword is wrong     -> 403 INVALID_CURRENT_PASSWORD
     * 4. display name taken by another account          -> 409 USERNAME_TAKEN
     * 5. email taken by another account                 -> 409 EMAIL_TAKEN
     * The password is checked BEFORE the email conflict, so nobody can find out which emails are
     * registered without knowing the password of an account.
     * Values equal to the current ones are not changes (no password needed, nothing is written).
     */
    @Transactional
    public User update(Long userId, UpdateAccountRequest request) {
        if (request.displayName() == null && request.email() == null) {
            throw new RequestValidationException(List.of(new FieldErrorResponse(
                    "request", "Provide displayName and/or email")));
        }
        User user = users.findById(userId).orElseThrow(() ->
                new UnauthorizedException(ErrorCode.AUTHENTICATION_REQUIRED, "Sign in to continue"));

        String newName = request.displayName();
        String newEmail = request.email();
        boolean nameChanges = newName != null && !newName.equals(user.getDisplayName());
        boolean emailChanges = newEmail != null && !newEmail.equals(user.getEmail());

        if (emailChanges) {
            requireCurrentPassword(user, request.currentPassword());
        }
        if (nameChanges && users.existsByDisplayNameIgnoreCaseAndIdNot(newName, userId)) {
            throw new ConflictException(ErrorCode.USERNAME_TAKEN, "Display name is already taken");
        }
        if (emailChanges && users.existsByEmailAndIdNot(newEmail, userId)) {
            throw new ConflictException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
        }
        if (!nameChanges && !emailChanges) {
            return user;
        }

        if (nameChanges) {
            user.changeDisplayName(newName);
        }
        if (emailChanges) {
            user.changeEmail(newEmail);
        }
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
    }

    private void requireCurrentPassword(User user, String currentPassword) {
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new RequestValidationException(List.of(new FieldErrorResponse(
                    "currentPassword", "Current password is required to change the email")));
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect", ErrorCode.INVALID_CURRENT_PASSWORD);
        }
    }

    /** Two requests took the same name/email at once: answer with the same 409 as the normal check. */
    private RuntimeException translate(DataIntegrityViolationException exception) {
        StringBuilder details = new StringBuilder();
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            details.append(cause.getMessage()).append(' ');
        }
        String text = details.toString().toLowerCase(Locale.ROOT);
        if (text.contains("uk_users_email")) {
            return new ConflictException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
        }
        if (text.contains("uk_users_display_name")) {
            return new ConflictException(ErrorCode.USERNAME_TAKEN, "Display name is already taken");
        }
        return exception;
    }
}