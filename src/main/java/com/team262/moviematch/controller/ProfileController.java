package com.team262.moviematch.controller;
import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.config.openapi.ProfileApiExamples;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.dto.history.ProfileResponse;
import com.team262.moviematch.service.account.AccountService;
import com.team262.moviematch.service.history.ProfileService;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

    @GetMapping({"/me/movie-identity"})
    @Operation(summary = "Get profile statistics (Movie Identity)",
            description = "Statistics of the current account over finished sessions. "
                    + "Genre and decade statistics count reactions (the same title in two sessions counts twice); "
                    + "favoriteActors counts different liked titles. "
                    + "leastLikedGenre is the genre with the highest SHARE of dislikes among genres with at least "
                    + "5 reactions (profile.min-genre-ratings); favoriteActors lists actors with at least 2 liked "
                    + "titles (profile.min-actor-titles), top 5 (profile.actors-limit). "
                    + "Titles without a release year are ignored in decadeMix. "
                    + "Ties are resolved deterministically. Nullable fields are null and lists are empty "
                    + "when there is not enough data.",
            security = @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION))
    @ApiResponse(responseCode = "200", description = "Profile statistics",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProfileResponse.class), examples = {
                    @ExampleObject(name = "With history", value = ProfileApiExamples.PROFILE_RESPONSE),
                    @ExampleObject(name = "No finished games", value = ProfileApiExamples.EMPTY_PROFILE_RESPONSE)}))
    @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED: no valid account session",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ProfileApiExamples.AUTHENTICATION_REQUIRED)))
    public ProfileResponse profile(Authentication authentication) {
        return profiles.profile(accounts.current(authentication));
    }
}

