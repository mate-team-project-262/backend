package com.team262.moviematch.controller;
import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.dto.history.ProfileResponse;
import com.team262.moviematch.service.account.AccountService;
import com.team262.moviematch.service.history.ProfileService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Account statistics and movie identity")
public class ProfileController {
    private final AccountService accounts;
    private final ProfileService profiles;
    @GetMapping({"/me/profile", "/me/movie-identity"})
    @Operation(summary = "Get profile statistics",
            description = "Returns finished session counters, personal reactions and liked genre distribution.",
            security = @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION))
    @ApiResponse(responseCode = "200", description = "Profile statistics",
            content = @Content(schema = @Schema(implementation = ProfileResponse.class)))
    @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ProfileResponse profile(Authentication authentication) {
        return profiles.profile(accounts.current(authentication));
    }
}

