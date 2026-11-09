package com.team262.moviematch.controller;
import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.dto.history.HistoryDtos.*;
import com.team262.moviematch.service.account.AccountService;
import com.team262.moviematch.service.history.HistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@Tag(name = "History", description = "Finished game sessions owned by the current account")
public class HistoryController {
    private final AccountService accounts;
    private final HistoryService history;
    @GetMapping("/me/history")
    @Operation(summary = "List the current account's game history",
            description = "Returns newest sessions first. Only sessions linked to the current account are visible.",
            security = @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION))
    @ApiResponse(responseCode = "200", description = "History page",
            content = @Content(schema = @Schema(implementation = HistoryPage.class)))
    @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public HistoryPage list(Authentication authentication,
                            @Parameter(description = "Zero-based page", example = "0")
                            @RequestParam(defaultValue = "0") @Min(0) int page,
                            @Parameter(description = "Page size from 1 to 50", example = "10")
                            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return history.history(accounts.current(authentication).getId(), page, size);
    }
    @GetMapping("/me/history/{sessionId}")
    @Operation(summary = "Get one finished game",
            description = "Returns participants, all cards, final like counts and the current account's reactions.",
            security = @SecurityRequirement(name = OpenApiConfig.ACCOUNT_SESSION))
    @ApiResponse(responseCode = "200", description = "Finished session details",
            content = @Content(schema = @Schema(implementation = Details.class)))
    @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "HISTORY_NOT_FOUND or session belongs to another account",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Details details(Authentication authentication,
            @Parameter(description = "Archived session ID", example = "42") @PathVariable Long sessionId) {
        return history.details(accounts.current(authentication).getId(), sessionId);
    }
}

