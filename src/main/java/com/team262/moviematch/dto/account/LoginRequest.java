package com.team262.moviematch.dto.account;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record LoginRequest(@NotBlank @Size(min = 3, max = 20) String displayName,
                           @NotBlank @Size(max = 72) String password) {
    public LoginRequest {
        displayName = displayName == null ? null : displayName.trim();
    }
}

