package com.team262.moviematch.exception.ex;

import com.team262.moviematch.exception.ErrorCode;

public class ExternalServiceException extends RuntimeException {
    public ExternalServiceException() {
        super("TMDB is unavailable");
    }

    public ExternalServiceException(Throwable cause) {
        super("TMDB is unavailable", cause);
    }

    public ErrorCode getCode() {
        return ErrorCode.TMDB_UNAVAILABLE;
    }
}
