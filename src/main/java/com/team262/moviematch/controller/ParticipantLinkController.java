package com.team262.moviematch.controller;
import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.service.account.AccountService;
import com.team262.moviematch.service.account.ParticipantLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Connect guest game activity to an account")
public class ParticipantLinkController {
    private final AccountService accounts;
    private final ParticipantLinkService linking;
    @PostMapping("/me/participants/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Link a guest participant to the current account",
            description = "Use after creating or joining a room, including on the results screen. "
                    + "The participant token must still be valid.", security = {
                @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION),
                @SecurityRequirement(name = OpenApiConfig.CSRF_TOKEN),
                @SecurityRequirement(name = OpenApiConfig.PARTICIPANT_TOKEN)})
    @ApiResponse(responseCode = "204", description = "Participant linked", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing account session or invalid participant token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Participant is already linked",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void link(Authentication authentication,
            @Parameter(hidden = true) @RequestHeader("X-Participant-Token") String token) {
        linking.link(accounts.current(authentication), token);
    }
}
