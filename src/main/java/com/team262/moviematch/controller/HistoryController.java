package com.team262.moviematch.controller;
import com.team262.moviematch.dto.history.HistoryDtos.*;
import com.team262.moviematch.service.account.AccountService;
import com.team262.moviematch.service.history.HistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class HistoryController {
    private final AccountService accounts;
    private final HistoryService history;
    @GetMapping("/me/history")
    public HistoryPage list(Authentication authentication,
                            @RequestParam(defaultValue = "0") @Min(0) int page,
                            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return history.history(accounts.current(authentication).getId(), page, size);
    }
    @GetMapping("/me/history/{sessionId}")
    public Details details(Authentication authentication, @PathVariable Long sessionId) {
        return history.details(accounts.current(authentication).getId(), sessionId);
    }
}

