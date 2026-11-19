package com.team262.moviematch.service.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRateLimiter {
    private final Clock clock = Clock.systemUTC();
    private final Map<String, Instant> nextAllowed = new ConcurrentHashMap<>();

    public synchronized long require(String key, int seconds) {
        Instant now = clock.instant();
        Instant next = nextAllowed.get(key);
        if (next != null && next.isAfter(now)) {
            return Math.max(1, java.time.Duration.between(now, next).toSeconds() + 1);
        }
        if (nextAllowed.size() > 10000) {
            nextAllowed.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        }
        nextAllowed.put(key, now.plusSeconds(seconds));
        return 0;
    }
}
