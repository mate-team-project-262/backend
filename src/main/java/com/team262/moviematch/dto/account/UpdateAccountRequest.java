package com.team262.moviematch.dto.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * PATCH /me. Both fields are optional, but at least one must be sent (checked in the service).
 * A field that is sent must be valid: "" or spaces are rejected, not silently ignored.
 */
public record UpdateAccountRequest(
        @Schema(description = "New display name (3-20 characters, unique ignoring case). It is used for sign-in",
                example = "NewEgor", nullable = true)
        @Size(min = 3, max = 20) String displayName,
        @Schema(description = "New email (valid, unique, stored in lowercase). Requires currentPassword",
                example = "new-email@example.com", nullable = true)
        @Email @Size(min = 1, max = 254) String email,
        @Schema(description = "Current password. Required only when the email changes",
                example = "oldPassword123", nullable = true)
        @Size(max = 72) String currentPassword) {

    public UpdateAccountRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        displayName = displayName == null ? null : displayName.trim();
        // the password is never trimmed or changed
    }

    /** The password must not end up in logs. */
    @Override
    public String toString() {
        return "UpdateAccountRequest[displayName=" + displayName + ", email=" + email
                + ", currentPassword=" + (currentPassword == null ? "null" : "***") + "]";
    }
}