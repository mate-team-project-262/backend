package com.team262.moviematch.controller;
import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.dto.account.*;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.service.account.AccountService;
import com.team262.moviematch.service.account.AccountSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account login, logout and current account")
public class AccountController {
    private final AccountService accounts;
    private final AccountSessionService sessions;
    @PostMapping("/auth/login")
    @Operation(summary = "Sign in by display name",
            description = "Authenticates with Spring Security, replaces any existing session and returns an HttpOnly session cookie.",
            security = @SecurityRequirement(name = OpenApiConfig.CSRF_TOKEN))
    @ApiResponse(responseCode = "200", description = "Signed in",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request fields",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Missing or invalid CSRF token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UserResponse login(@Valid @RequestBody LoginRequest input,
                              HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = sessions.signIn(
                input.displayName(), input.password(), request, response);
        return UserResponse.from(accounts.current(authentication));
    }
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sign out",
            description = "Invalidates the account session and clears the security context.", security = {
                @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION),
                @SecurityRequirement(name = OpenApiConfig.CSRF_TOKEN)})
    @ApiResponse(responseCode = "204", description = "Signed out", content = @Content)
    @ApiResponse(responseCode = "403", description = "Missing or invalid CSRF token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
    @GetMapping("/me")
    @Operation(summary = "Get the current account",
            security = @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION))
    @ApiResponse(responseCode = "200", description = "Current account",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(accounts.current(authentication));
    }
}

