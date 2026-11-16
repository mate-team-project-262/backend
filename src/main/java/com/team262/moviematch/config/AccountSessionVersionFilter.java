package com.team262.moviematch.config;

import com.team262.moviematch.repository.UserRepository;
import com.team262.moviematch.service.account.VersionedAccountDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class AccountSessionVersionFilter extends OncePerRequestFilter {
    private final UserRepository users;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof VersionedAccountDetails details) {
            Long id = Long.valueOf(details.getUsername());
            if (users.findById(id).map(user -> user.getSessionVersion() != details.getSessionVersion())
                    .orElse(true)) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
