package com.team262.moviematch.service.account;

import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    @Override
    public UserDetails loadUserByUsername(String displayName) throws UsernameNotFoundException {
        User user = users.findByDisplayNameIgnoreCase(displayName).orElseThrow(() ->
                new UsernameNotFoundException("Invalid name or password"));
        return org.springframework.security.core.userdetails.User.withUsername(user.getId().toString())
                .password(user.getPasswordHash())
                .roles("USER")
                .build();
    }
}
