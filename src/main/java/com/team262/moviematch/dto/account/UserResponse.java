package com.team262.moviematch.dto.account;
import com.team262.moviematch.model.User;
public record UserResponse(Long id, String email, String displayName, String avatarUrl) {
    public static UserResponse from(User user) {
        String avatarUrl = user.getAvatarVersion() == null ? null
                : "/me/avatar/image?v=" + user.getAvatarVersion();
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), avatarUrl);
    }
}

