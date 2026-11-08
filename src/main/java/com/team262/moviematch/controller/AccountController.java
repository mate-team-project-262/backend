package com.team262.moviematch.controller;
import com.team262.moviematch.dto.account.*;
import com.team262.moviematch.service.account.AccountService;
import com.team262.moviematch.service.account.AccountSessionService;
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
public class AccountController {
    private final AccountService accounts;
    private final AccountSessionService sessions;
    @PostMapping("/auth/login")
    public UserResponse login(@Valid @RequestBody LoginRequest input,
                              HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = sessions.signIn(
                input.displayName(), input.password(), request, response);
        return UserResponse.from(accounts.current(authentication));
    }
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(accounts.current(authentication));
    }
}

