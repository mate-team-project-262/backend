package com.team262.moviematch.service.account;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
@Service
public class AccountService {
    private final UserRepository users;

    public AccountService(UserRepository users) {
        this.users = users;
    }

    public User current(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException(ErrorCode.AUTHENTICATION_REQUIRED, "Sign in to continue");
        }
        try {
            return users.findById(Long.valueOf(authentication.getName())).orElseThrow(() ->
                    new UnauthorizedException(ErrorCode.AUTHENTICATION_REQUIRED, "Sign in to continue"));
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException(ErrorCode.AUTHENTICATION_REQUIRED, "Sign in to continue");
        }
    }
}

