package com.team262.moviematch.controller;
import com.team262.moviematch.dto.history.ProfileResponse;
import com.team262.moviematch.service.account.AccountService;
import com.team262.moviematch.service.history.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class ProfileController {
    private final AccountService accounts;
    private final ProfileService profiles;
    @GetMapping({"/me/profile", "/me/movie-identity"})
    public ProfileResponse profile(Authentication authentication) {
        return profiles.profile(accounts.current(authentication));
    }
}

