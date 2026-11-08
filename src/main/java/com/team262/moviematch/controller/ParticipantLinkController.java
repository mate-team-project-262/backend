package com.team262.moviematch.controller;
import com.team262.moviematch.service.account.AccountService;
import com.team262.moviematch.service.account.ParticipantLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class ParticipantLinkController {
    private final AccountService accounts;
    private final ParticipantLinkService linking;
    @PostMapping("/me/participants/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void link(Authentication authentication, @RequestHeader("X-Participant-Token") String token) {
        linking.link(accounts.current(authentication), token);
    }
}
