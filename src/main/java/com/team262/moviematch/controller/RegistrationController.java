package com.team262.moviematch.controller;
import com.team262.moviematch.dto.account.RegisterRequest;
import com.team262.moviematch.dto.account.UserResponse;
import com.team262.moviematch.service.account.RegistrationService;
import com.team262.moviematch.service.account.AccountSessionService;
import com.team262.moviematch.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class RegistrationController {
    private final RegistrationService registration;
    private final AccountSessionService sessions;
    @GetMapping("/auth/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request,
                                 HttpServletRequest servletRequest,
                                 HttpServletResponse servletResponse) {
        User user = registration.register(request);
        sessions.signIn(request.displayName(), request.password(), servletRequest, servletResponse);
        return UserResponse.from(user);
    }
    public record CsrfResponse(String headerName, String token) {}
}

