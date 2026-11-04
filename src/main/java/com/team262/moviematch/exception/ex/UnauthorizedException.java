package com.team262.moviematch.exception.ex;

import com.team262.moviematch.exception.ErrorCode;

public class UnauthorizedException extends RuntimeException {
    private final ErrorCode code;

    public UnauthorizedException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}