package com.team262.moviematch.exception.ex;

import com.team262.moviematch.exception.ErrorCode;

public class NotFoundException extends RuntimeException {

    private final ErrorCode code;

    public NotFoundException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
