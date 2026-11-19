package com.team262.moviematch.service.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team262.moviematch.dto.account.DeleteAccountRequest;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.BadRequestException;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    private static final Long USER_ID = 7L;

    @Mock UserRepository users;
    @Mock ParticipantRepository participants;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordResetService resets;

    @Test
    void deletesAccountAndDetachesActiveParticipants() {
        User user = user();
        Participant participant = new Participant(new Room("DEL123"), "Egor", true,
                "participant-token", LocalDateTime.now());
        participant.setUser(user);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "stored-hash")).thenReturn(true);
        when(participants.findAllByUserId(USER_ID)).thenReturn(List.of(participant));
        AccountDeletionService service = new AccountDeletionService(users, participants, passwordEncoder, resets);

        service.deleteAccount(USER_ID, new DeleteAccountRequest("password123"));

        assertThat(participant.getUser()).isNull();
        verify(participants).flush();
        verify(users).delete(user);
        verify(users).flush();
    }

    @Test
    void wrongPasswordDoesNotDetachOrDeleteAnything() {
        User user = user();
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword1", "stored-hash")).thenReturn(false);
        AccountDeletionService service = new AccountDeletionService(users, participants, passwordEncoder, resets);

        assertThatThrownBy(() -> service.deleteAccount(
                USER_ID, new DeleteAccountRequest("wrongPassword1")))
                .isInstanceOfSatisfying(BadRequestException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD));

        verifyNoInteractions(participants);
        verify(users, never()).delete(user);
        verify(users, never()).flush();
    }

    @Test
    void missingAccountRequiresSignInAgain() {
        when(users.findById(USER_ID)).thenReturn(Optional.empty());
        AccountDeletionService service = new AccountDeletionService(users, participants, passwordEncoder, resets);

        assertThatThrownBy(() -> service.deleteAccount(
                USER_ID, new DeleteAccountRequest("password123")))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));

        verifyNoInteractions(participants, passwordEncoder);
    }

    @Test
    void requestToStringDoesNotLeakPassword() {
        assertThat(new DeleteAccountRequest("password123").toString())
                .doesNotContain("password123").contains("***");
    }

    private User user() {
        User user = new User("egor@example.com", "stored-hash", "Egor");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
