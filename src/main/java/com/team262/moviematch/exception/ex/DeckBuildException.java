package com.team262.moviematch.exception.ex;

import com.team262.moviematch.exception.ErrorCode;

public class DeckBuildException extends RuntimeException {
    private final ErrorCode code;

    public DeckBuildException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
