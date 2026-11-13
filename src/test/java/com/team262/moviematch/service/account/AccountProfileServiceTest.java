package com.team262.moviematch.service.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team262.moviematch.dto.account.UpdateAccountRequest;
import com.team262.moviematch.dto.error.FieldErrorResponse;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.RequestValidationException;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountProfileServiceTest {

    private static final long ID = 7L;

    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AccountProfileService service;

    private User user() {
        User user = new User("egor@example.com", "stored-hash", "Egor");
        ReflectionTestUtils.setField(user, "id", ID);
        return user;
    }

    private void existingUser(User user) {
        when(users.findById(ID)).thenReturn(Optional.of(user));
    }

    private UpdateAccountRequest request(String name, String email, String password) {
        return new UpdateAccountRequest(name, email, password);
    }

    // ---- nothing to change ----

    @Test
    void emptyRequestIsAValidationError() {
        assertThatThrownBy(() -> service.update(ID, request(null, null, null)))
                .isInstanceOfSatisfying(RequestValidationException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::field)
                                .containsExactly("request"));
        verifyNoInteractions(users);
    }

    @Test
    void sameValuesChangeNothingAndNeedNoPassword() {
        User user = user();
        existingUser(user);

        User result = service.update(ID, request("Egor", "EGOR@example.com", null));

        assertThat(result).isSameAs(user);
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(passwordEncoder);
    }

    // ---- display name ----

    @Test
    void changesOnlyTheNameWithoutAPassword() {
        User user = user();
        existingUser(user);
        when(users.existsByDisplayNameIgnoreCaseAndIdNot("NewEgor", ID)).thenReturn(false);
        when(users.saveAndFlush(user)).thenReturn(user);

        User result = service.update(ID, request("NewEgor", null, null));

        assertThat(result.getDisplayName()).isEqualTo("NewEgor");
        assertThat(result.getEmail()).isEqualTo("egor@example.com");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void renamingOnlyTheLetterCaseOfOwnNameIsAllowed() {
        User user = user();
        existingUser(user);
        when(users.existsByDisplayNameIgnoreCaseAndIdNot("EGOR", ID)).thenReturn(false);   // own row is excluded
        when(users.saveAndFlush(user)).thenReturn(user);

        assertThat(service.update(ID, request("EGOR", null, null)).getDisplayName()).isEqualTo("EGOR");
    }

    @Test
    void nameTakenByAnotherAccountIsAConflict() {
        existingUser(user());
        when(users.existsByDisplayNameIgnoreCaseAndIdNot("Anna", ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(ID, request("Anna", null, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.USERNAME_TAKEN));
        verify(users, never()).saveAndFlush(any());
    }

    // ---- email ----

    @Test
    void emailChangeWithoutPasswordIsAValidationError() {
        existingUser(user());

        assertThatThrownBy(() -> service.update(ID, request(null, "new@example.com", null)))
                .isInstanceOfSatisfying(RequestValidationException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::field)
                                .containsExactly("currentPassword"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void wrongPasswordIsForbiddenAndIsCheckedBeforeEmailConflict() {
        existingUser(user());
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.update(ID, request(null, "taken@example.com", "wrong")))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_PASSWORD));
        // nobody can learn which emails are registered without the password
        verify(users, never()).existsByEmailAndIdNot(any(), any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void changesTheEmailWithTheCorrectPassword() {
        User user = user();
        existingUser(user);
        when(passwordEncoder.matches("oldPassword123", "stored-hash")).thenReturn(true);
        when(users.existsByEmailAndIdNot("new@example.com", ID)).thenReturn(false);
        when(users.saveAndFlush(user)).thenReturn(user);

        User result = service.update(ID, request(null, " NEW@Example.com ", "oldPassword123"));

        assertThat(result.getEmail()).isEqualTo("new@example.com");     // trimmed and lowercased by the DTO
        assertThat(result.getDisplayName()).isEqualTo("Egor");
    }

    @Test
    void emailTakenByAnotherAccountIsAConflict() {
        existingUser(user());
        when(passwordEncoder.matches("oldPassword123", "stored-hash")).thenReturn(true);
        when(users.existsByEmailAndIdNot("anna@example.com", ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(ID, request(null, "anna@example.com", "oldPassword123")))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_TAKEN));
    }

    // ---- concurrent requests (the unique constraint decides) ----

    @Test
    void concurrentNameRaceIsTranslatedToUsernameTaken() {
        User user = user();
        existingUser(user);
        when(users.existsByDisplayNameIgnoreCaseAndIdNot("Anna", ID)).thenReturn(false);
        when(users.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException(
                "Duplicate entry 'Anna' for key 'users.uk_users_display_name'"));

        assertThatThrownBy(() -> service.update(ID, request("Anna", null, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.USERNAME_TAKEN));
    }

    @Test
    void concurrentEmailRaceIsTranslatedToEmailTaken() {
        User user = user();
        existingUser(user);
        when(passwordEncoder.matches("oldPassword123", "stored-hash")).thenReturn(true);
        when(users.existsByEmailAndIdNot("anna@example.com", ID)).thenReturn(false);
        when(users.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException(
                "Unique index or primary key violation: UK_USERS_EMAIL"));

        assertThatThrownBy(() -> service.update(ID, request(null, "anna@example.com", "oldPassword123")))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_TAKEN));
    }

    @Test
    void unrelatedIntegrityErrorsAreNotHidden() {
        User user = user();
        existingUser(user);
        when(users.existsByDisplayNameIgnoreCaseAndIdNot("Anna", ID)).thenReturn(false);
        DataIntegrityViolationException other = new DataIntegrityViolationException("some_other_constraint");
        when(users.saveAndFlush(user)).thenThrow(other);

        assertThatThrownBy(() -> service.update(ID, request("Anna", null, null))).isSameAs(other);
    }

    @Test
    void requestToStringDoesNotLeakThePassword() {
        assertThat(request("Egor", null, "oldPassword123").toString())
                .doesNotContain("oldPassword123").contains("***");
    }
}