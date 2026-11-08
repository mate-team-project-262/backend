package com.team262.moviematch.dto.account;
import jakarta.validation.constraints.*;
import java.util.Locale;
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 3, max = 20) String displayName,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9]).*") String password) {
    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        displayName = displayName == null ? null : displayName.trim();
    }
}

