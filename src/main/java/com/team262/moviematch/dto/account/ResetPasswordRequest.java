package com.team262.moviematch.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        @Pattern(regexp = PasswordPolicy.LETTER_AND_DIGIT, message = PasswordPolicy.LETTER_AND_DIGIT_MESSAGE)
        String newPassword) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***, newPassword=***]";
    }
}
