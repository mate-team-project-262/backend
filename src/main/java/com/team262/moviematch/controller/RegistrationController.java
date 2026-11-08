package com.team262.moviematch.controller;
import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.dto.account.RegisterRequest;
import com.team262.moviematch.dto.account.UserResponse;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.service.account.RegistrationService;
import com.team262.moviematch.service.account.AccountSessionService;
import com.team262.moviematch.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account registration and CSRF bootstrap")
public class RegistrationController {
    private final RegistrationService registration;
    private final AccountSessionService sessions;
    @GetMapping("/auth/csrf")
    @Operation(summary = "Get a CSRF token",
            description = "Call before POST requests under /auth or /me. Send token in the returned headerName and include credentials.")
    @ApiResponse(responseCode = "200", description = "CSRF token issued",
            content = @Content(schema = @Schema(implementation = CsrfResponse.class)))
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an account",
            description = "Creates an account, signs it in and returns an HttpOnly session cookie.",
            security = @SecurityRequirement(name = OpenApiConfig.CSRF_TOKEN))
    @ApiResponse(responseCode = "201", description = "Account created and signed in",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid email, display name or password",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "EMAIL_TAKEN or USERNAME_TAKEN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Missing or invalid CSRF token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UserResponse register(@Valid @RequestBody RegisterRequest request,
                                 HttpServletRequest servletRequest,
                                 HttpServletResponse servletResponse) {
        User user = registration.register(request);
        sessions.signIn(request.displayName(), request.password(), servletRequest, servletResponse);
        return UserResponse.from(user);
    }
    public record CsrfResponse(String headerName, String token) {}
}

