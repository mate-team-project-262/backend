package com.team262.moviematch.exception;

import com.team262.moviematch.dto.ErrorResponse;
import com.team262.moviematch.dto.FieldErrorResponse;
import java.util.List;
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

    private ErrorResponse buildErrorResponse(
            String code,
            String message,
            List<FieldErrorResponse> fieldErrors) {

        return new ErrorResponse(code, message, fieldErrors);
    }
}
