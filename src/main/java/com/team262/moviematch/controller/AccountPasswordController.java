package com.team262.moviematch.controller;

import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.config.openapi.PasswordApiExamples;
import com.team262.moviematch.dto.account.ChangePasswordRequest;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.service.account.AccountPasswordService;
import com.team262.moviematch.service.account.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account login, logout and current account")
public class AccountPasswordController {
    private final AccountService accounts;
    private final AccountPasswordService passwords;

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "changePassword", summary = "Change the password",
            description = "Requires the current password. The new password: 8-72 characters, at least one letter "
                    + "and one digit, different from the current one; passwords are not trimmed. "
                    + "On success the CURRENT session is invalidated: the client must forget its session and CSRF "
                    + "token, call GET /auth/csrf again and sign in with the new password. "
                    + "Other sessions of the account (other devices) stay valid. Games in progress are not "
                    + "affected: participant tokens do not depend on the password. "
                    + "Requires the X-CSRF-TOKEN header. Checks run in order: session, request fields and "
                    + "password rules (400 VALIDATION_ERROR), current password (400 INVALID_CURRENT_PASSWORD), "
                    + "new password differs from the current one (409 PASSWORD_NOT_CHANGED).",
            security = {
                @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION),
                @SecurityRequirement(name = OpenApiConfig.CSRF_TOKEN)})
    @ApiResponse(responseCode = "204", description = "Password changed, the session is invalidated", content = @Content)
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR (missing field or password rules, see "
            + "fieldErrors) or INVALID_CURRENT_PASSWORD",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class), examples = {
                        @ExampleObject(name = "Weak password", value = PasswordApiExamples.WEAK_PASSWORD),
                        @ExampleObject(name = "Too short", value = PasswordApiExamples.TOO_SHORT),
                        @ExampleObject(name = "Wrong current password", value = PasswordApiExamples.INVALID_CURRENT_PASSWORD)}))
    @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED: no valid account session",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = PasswordApiExamples.AUTHENTICATION_REQUIRED)))
    @ApiResponse(responseCode = "403", description = "ACCESS_DENIED: missing or invalid CSRF token",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = PasswordApiExamples.CSRF_DENIED)))
    @ApiResponse(responseCode = "409", description = "PASSWORD_NOT_CHANGED: the new password equals the current one",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = PasswordApiExamples.PASSWORD_NOT_CHANGED)))
    public void changePassword(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ChangePasswordRequest.class),
                            examples = @ExampleObject(value = PasswordApiExamples.REQUEST)))
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Long userId = accounts.current(authentication).getId();
        passwords.changePassword(userId, request);      // commits here (service transaction)
        // only after a successful save: same call as POST /auth/logout
        new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
    }
}