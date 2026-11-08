package com.team262.moviematch.dto.account;
import com.team262.moviematch.model.User;
public record UserResponse(Long id, String email, String displayName) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}

