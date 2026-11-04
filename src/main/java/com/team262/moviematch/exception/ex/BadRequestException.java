package com.team262.moviematch.exception.ex;

import com.team262.moviematch.exception.ErrorCode;

public class BadRequestException extends RuntimeException {

    private final ErrorCode errorCode;

    public BadRequestException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getCode() {
        return errorCode;
    }
}
