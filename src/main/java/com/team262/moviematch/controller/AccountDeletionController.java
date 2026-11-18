package com.team262.moviematch.controller;

import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.config.openapi.AccountDeletionApiExamples;
import com.team262.moviematch.dto.account.DeleteAccountRequest;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.service.account.AccountDeletionService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account login, logout and current account")
public class AccountDeletionController {

    private final AccountService accounts;
    private final AccountDeletionService deletion;

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteMe", summary = "Permanently delete the current account",
            description = "Requires the current password and X-CSRF-TOKEN. Deletes the account, invalidates the "
                    + "current account session and detaches active room participants so their participant tokens "
                    + "continue to work as guests. Shared finished-session archives remain available to other "
                    + "accounts; the deleted account is anonymized in those archives.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION),
                    @SecurityRequirement(name = OpenApiConfig.CSRF_TOKEN)})
    @ApiResponse(responseCode = "204", description = "Account deleted and current session invalidated",
            content = @Content)
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or INVALID_CURRENT_PASSWORD",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = AccountDeletionApiExamples.INVALID_CURRENT_PASSWORD)))
    @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED: no valid account session",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = AccountDeletionApiExamples.AUTHENTICATION_REQUIRED)))
    @ApiResponse(responseCode = "403", description = "ACCESS_DENIED: missing or invalid CSRF token",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = AccountDeletionApiExamples.CSRF_DENIED)))
    public void deleteMe(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteAccountRequest.class),
                            examples = @ExampleObject(value = AccountDeletionApiExamples.REQUEST)))
            @Valid @RequestBody DeleteAccountRequest request,
            Authentication authentication, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Long userId = accounts.current(authentication).getId();
        deletion.deleteAccount(userId, request);
        new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
    }
}
