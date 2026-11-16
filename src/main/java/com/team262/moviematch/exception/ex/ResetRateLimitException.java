package com.team262.moviematch.exception.ex;

public class ResetRateLimitException extends RuntimeException {
    private final long retryAfter;
    public ResetRateLimitException(long retryAfter) {
        super("Too many password reset requests");
        this.retryAfter = retryAfter;
    }
    public long getRetryAfter() {
        return retryAfter;
    }
}
