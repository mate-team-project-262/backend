package com.team262.moviematch.controller;

import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.dto.account.ForgotPasswordRequest;
import com.team262.moviematch.dto.account.ResetPasswordRequest;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.exception.ex.ResetRateLimitException;
import com.team262.moviematch.service.account.PasswordResetRateLimiter;
import com.team262.moviematch.service.account.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account registration and password recovery")
public class PasswordResetController {
    private final PasswordResetService resets;
    private final PasswordResetRateLimiter limits;

    public record Accepted(String message) {}

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a password reset email",
            description = "Always returns the same answer for known and unknown emails. "
                    + "Obtain CSRF via GET /auth/csrf; includes Retry-After on 429.",
            security = @SecurityRequirement(name = OpenApiConfig.CSRF_TOKEN))
    @ApiResponse(responseCode = "202", description = "Request accepted",
            content = @Content(schema = @Schema(implementation = Accepted.class)))
    @ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS with Retry-After",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Accepted forgot(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest servletRequest) {
        limit("forgot:email:" + request.email(), 60);
        limit("forgot:ip:" + servletRequest.getRemoteAddr(), 10);
        resets.request(request.email());
        return new Accepted("If an account with this email exists, we sent instructions.");
    }

    @GetMapping("/reset-password/check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Check a password reset link", description = "Does not consume the link or sign in.")
    @ApiResponse(responseCode = "204", description = "Valid link", content = @Content)
    @ApiResponse(responseCode = "400", description = "RESET_TOKEN_INVALID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void check(@RequestParam String token, HttpServletRequest request) {
        limit("check:ip:" + request.getRemoteAddr(), 1);
        resets.check(token);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a new password using a reset link",
            description = "Consumes the link, revokes old account sessions and requires a new sign-in.",
            security = @SecurityRequirement(name = OpenApiConfig.CSRF_TOKEN))
    @ApiResponse(responseCode = "204", description = "Password reset", content = @Content)
    @ApiResponse(responseCode = "400", description = "RESET_TOKEN_INVALID or VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "PASSWORD_NOT_CHANGED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void reset(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
        limit("reset:ip:" + servletRequest.getRemoteAddr(), 1);
        resets.reset(request.token(), request.newPassword());
    }

    private void limit(String key, int seconds) {
        long retry = limits.require(key, seconds);
        if (retry > 0) {
            throw new ResetRateLimitException(retry);
        }
    }
}
