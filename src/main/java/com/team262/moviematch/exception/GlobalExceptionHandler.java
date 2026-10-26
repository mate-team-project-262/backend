package com.team262.moviematch.exception;

import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.dto.error.FieldErrorResponse;
import com.team262.moviematch.exception.ex.BadRequestException;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
    private static final String VALIDATION_ERROR_MESSAGE = "Validation failed";
    private static final String INVALID_VALUE = "Invalid value";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        List<FieldErrorResponse> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> new FieldErrorResponse(
                        e.getField(),
                        e.getDefaultMessage() != null ?
                                e.getDefaultMessage() : INVALID_VALUE
                ))
                .toList();
        ErrorResponse response = new ErrorResponse(
                VALIDATION_ERROR_CODE,
                VALIDATION_ERROR_MESSAGE,
                fieldErrors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildErrorResponse(
                e.getCode().name(),
                e.getMessage(),
                List.of()
        ));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(buildErrorResponse(
                e.getCode().name(),
                e.getMessage(),
                List.of()
        ));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestException e) {
        return ResponseEntity.badRequest().body(buildErrorResponse(
                e.getCode().name(),
                e.getMessage(),
                List.of()
        ));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildErrorResponse(
                e.getCode().name(),
                e.getMessage(),
                List.of()
        ));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handeUnauthorizedException(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildErrorResponse(
                e.getCode().name(),
                e.getMessage(),
                List.of()
        ));
    }

    private ErrorResponse buildErrorResponse(
            String code,
            String message,
            List<FieldErrorResponse> fieldErrors) {
        return new ErrorResponse(code, message, fieldErrors);
    }
}
