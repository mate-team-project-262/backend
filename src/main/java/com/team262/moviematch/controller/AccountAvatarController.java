package com.team262.moviematch.controller;

import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.dto.account.UserResponse;
import com.team262.moviematch.model.User;
import com.team262.moviematch.model.UserAvatar;
import com.team262.moviematch.service.account.AccountAvatarService;
import com.team262.moviematch.service.account.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/me/avatar")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account avatar")
public class AccountAvatarController {
    private final AccountService accounts;
    private final AccountAvatarService avatars;

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload account avatar (JPG, PNG or WebP, up to 2 MB)", security = {
            @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION),
            @SecurityRequirement(name = OpenApiConfig.CSRF_TOKEN)})
    public UserResponse upload(@RequestPart(value = "file", required = false) MultipartFile file,
                               Authentication authentication) {
        User user = accounts.current(authentication);
        return UserResponse.from(avatars.upload(user, file));
    }

    @DeleteMapping
    @Operation(summary = "Remove account avatar", security = {
            @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION),
            @SecurityRequirement(name = OpenApiConfig.CSRF_TOKEN)})
    public ResponseEntity<Void> delete(Authentication authentication) {
        avatars.delete(accounts.current(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/image", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp"})
    @Operation(summary = "Get current account avatar",
            security = @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION))
    public ResponseEntity<byte[]> image(Authentication authentication) {
        UserAvatar avatar = avatars.get(accounts.current(authentication));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(avatar.getContentType()))
                .body(avatar.getImageBytes());
    }
}
