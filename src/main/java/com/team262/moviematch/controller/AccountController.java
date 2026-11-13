package com.team262.moviematch.controller;
import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.config.openapi.AccountApiExamples;
import com.team262.moviematch.dto.account.*;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.service.account.AccountProfileService;
import com.team262.moviematch.service.account.AccountService;
import com.team262.moviematch.service.account.AccountSessionService;
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
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account login, logout and current account")
public class AccountController {
    private final AccountService accounts;
    private final AccountSessionService sessions;
    private final AccountProfileService profileUpdates;
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

    @PatchMapping("/me")
    @Operation(operationId = "updateMe", summary = "Update the display name and/or email",
            description = "Send displayName, email or both (at least one). displayName: 3-20 characters, unique "
                    + "ignoring case; email: valid and unique, stored in lowercase. Spaces around values are "
                    + "trimmed; an empty value is an error. Changing the email requires currentPassword "
                    + "(403 INVALID_PASSWORD if it is wrong); changing only the name does not. "
                    + "Values equal to the current ones change nothing. "
                    + "Sign-in uses the display name, so the new name is used from the next sign-in; the current "
                    + "session stays valid. Finished games in the history keep the name used at the time. "
                    + "Requires the X-CSRF-TOKEN header. Checks run in order: session, request fields, "
                    + "at least one field, password, name conflict, email conflict.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION),
                    @SecurityRequirement(name = OpenApiConfig.CSRF_TOKEN)})
    @ApiResponse(responseCode = "200", description = "Account updated (or nothing to change)",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class),
                    examples = @ExampleObject(value = AccountApiExamples.UPDATE_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: invalid field, no field sent, "
            + "or currentPassword missing when the email changes. fieldErrors names the fields; "
            + "field 'request' means the whole body.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class), examples = {
                    @ExampleObject(name = "Invalid field", value = AccountApiExamples.VALIDATION_ERROR_FIELD),
                    @ExampleObject(name = "Empty request", value = AccountApiExamples.EMPTY_REQUEST),
                    @ExampleObject(name = "Password required", value = AccountApiExamples.PASSWORD_REQUIRED)}))
    @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED: no valid account session",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = AccountApiExamples.AUTHENTICATION_REQUIRED)))
    @ApiResponse(responseCode = "403", description = "INVALID_PASSWORD (wrong currentPassword) "
            + "or ACCESS_DENIED (missing or invalid CSRF token)",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class), examples = {
                    @ExampleObject(name = "Wrong password", value = AccountApiExamples.INVALID_PASSWORD),
                    @ExampleObject(name = "CSRF", value = AccountApiExamples.CSRF_DENIED)}))
    @ApiResponse(responseCode = "409", description = "USERNAME_TAKEN or EMAIL_TAKEN (used by another account)",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class), examples = {
                    @ExampleObject(name = "Name taken", value = AccountApiExamples.USERNAME_TAKEN),
                    @ExampleObject(name = "Email taken", value = AccountApiExamples.EMAIL_TAKEN)}))
    public UserResponse updateMe(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UpdateAccountRequest.class), examples = {
                            @ExampleObject(name = "Name and email", value = AccountApiExamples.UPDATE_REQUEST),
                            @ExampleObject(name = "Name only", value = AccountApiExamples.UPDATE_NAME_ONLY_REQUEST)}))
            @Valid @RequestBody UpdateAccountRequest request, Authentication authentication) {
        Long userId = accounts.current(authentication).getId();
        return UserResponse.from(profileUpdates.update(userId, request));
    }
}