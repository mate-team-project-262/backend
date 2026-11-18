package com.team262.moviematch.service.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team262.moviematch.dto.account.ChangePasswordRequest;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.BadRequestException;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountPasswordServiceTest {

    private static final long ID = 7L;

    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AccountPasswordService service;

    private User existingUser() {
        User user = new User("egor@example.com", "stored-hash", "Egor");
        ReflectionTestUtils.setField(user, "id", ID);
        when(users.findById(ID)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void savesTheEncodedNewPasswordWhenTheCurrentOneMatches() {
        User user = existingUser();
        when(passwordEncoder.matches("oldPassword123", "stored-hash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword456")).thenReturn("new-hash");

        service.changePassword(ID, new ChangePasswordRequest("oldPassword123", "newPassword456"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(users).saveAndFlush(user);
    }

    @Test
    void wrongCurrentPasswordIsRejectedAndNothingIsSaved() {
        User user = existingUser();
        when(passwordEncoder.matches("wrongPassword1", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(ID, new ChangePasswordRequest("wrongPassword1", "newPassword456")))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD));

        assertThat(user.getPasswordHash()).isEqualTo("stored-hash");
        verify(passwordEncoder, never()).encode(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void theSamePasswordIsAConflictAndIsNotHashedAgain() {
        User user = existingUser();
        when(passwordEncoder.matches("oldPassword123", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(ID, new ChangePasswordRequest("oldPassword123", "oldPassword123")))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PASSWORD_NOT_CHANGED));

        assertThat(user.getPasswordHash()).isEqualTo("stored-hash");
        verify(passwordEncoder, never()).encode(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void wrongCurrentPasswordIsReportedEvenWhenBothValuesAreEqual() {
        // "wrong" + "wrong": the current password is checked first, so this is NOT "password not changed"
        existingUser();
        when(passwordEncoder.matches("wrongPassword1", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(ID, new ChangePasswordRequest("wrongPassword1", "wrongPassword1")))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD));
    }

    @Test
    void unknownAccountMeansSignInAgain() {
        when(users.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(ID, new ChangePasswordRequest("oldPassword123", "newPassword456")))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));
    }

    @Test
    void requestToStringDoesNotLeakPasswords() {
        assertThat(new ChangePasswordRequest("oldPassword123", "newPassword456").toString())
                .doesNotContain("oldPassword123", "newPassword456");
    }
}