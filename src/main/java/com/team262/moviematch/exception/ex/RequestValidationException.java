package com.team262.moviematch.exception.ex;

import com.team262.moviematch.dto.error.FieldErrorResponse;
import java.util.List;

/*
 * Validation that cannot be expressed with annotations on a DTO
 */
public class RequestValidationException extends RuntimeException {
    private final List<FieldErrorResponse> fieldErrors;

    public RequestValidationException(List<FieldErrorResponse> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldErrorResponse> getFieldErrors() {
        return fieldErrors;
    }
}