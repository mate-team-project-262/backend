package com.team262.moviematch.dto.error;

public record FieldErrorResponse(
        String field,
        String message
) {
}