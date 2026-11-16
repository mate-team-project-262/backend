package com.team262.moviematch.exception.ex;

public class ResetQueueFullException extends RuntimeException {
    public ResetQueueFullException() {
        super("Password reset is temporarily unavailable");
    }
}
