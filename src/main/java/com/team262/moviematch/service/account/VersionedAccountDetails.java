package com.team262.moviematch.service.account;

import com.team262.moviematch.model.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class VersionedAccountDetails extends org.springframework.security.core.userdetails.User {
    private final long sessionVersion;

    public VersionedAccountDetails(User user) {
        super(user.getId().toString(), user.getPasswordHash(),
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        sessionVersion = user.getSessionVersion();
    }

    public long getSessionVersion() {
        return sessionVersion;
    }
}
