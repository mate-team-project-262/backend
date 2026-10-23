package com.team262.moviematch.dto;

public record FieldErrorResponse(
        String field,
        String message
) {
}