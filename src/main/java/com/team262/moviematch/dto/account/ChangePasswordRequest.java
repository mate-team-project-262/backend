package com.team262.moviematch.dto.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT /me/password. Passwords are never trimmed or changed. */
public record ChangePasswordRequest(
        @Schema(description = "The current password", example = "oldPassword123")
        @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String currentPassword,
        @Schema(description = "The new password: 8-72 characters, at least one letter and one digit, "
                + "different from the current one", example = "newPassword456")
        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        @Pattern(regexp = PasswordPolicy.LETTER_AND_DIGIT, message = PasswordPolicy.LETTER_AND_DIGIT_MESSAGE)
        String newPassword) {

    /** Passwords must not end up in logs. */
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}