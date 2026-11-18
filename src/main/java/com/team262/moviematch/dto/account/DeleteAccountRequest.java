package com.team262.moviematch.dto.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DELETE /me. The password is never trimmed or written to logs. */
public record DeleteAccountRequest(
        @Schema(description = "Current password used to confirm permanent account deletion",
                example = "password123")
        @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String currentPassword) {

    @Override
    public String toString() {
        return "DeleteAccountRequest[currentPassword=***]";
    }
}
