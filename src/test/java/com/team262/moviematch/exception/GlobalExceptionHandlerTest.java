package com.team262.moviematch.exception;

import com.team262.moviematch.dto.ErrorResponse;
import com.team262.moviematch.dto.FieldErrorResponse;
import com.team262.moviematch.exception.ex.BadRequestException;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void shouldReturnBadRequestForValidationErrors() {
        var bindingResult = new BeanPropertyBindingResult(
                new Object(), "request");

        bindingResult.addError(new FieldError(
                "request",
                "email",
                "Invalid email"));

        var exception = new MethodArgumentNotValidException(
                null, bindingResult);

        ResponseEntity<ErrorResponse> response =
                handler.handleValidationErrors(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals("VALIDATION_ERROR", body.code());
        assertEquals("Validation failed", body.message());

        assertEquals(
                List.of(new FieldErrorResponse("email", "Invalid email")),
                body.fieldErrors()
        );
    }

    @Test
    void shouldReturnNotFoundForNotFoundException() {

        NotFoundException exception = mock(NotFoundException.class);
        when(exception.getCode()).thenReturn(ErrorCode.ROOM_NOT_FOUND);
        when(exception.getMessage()).thenReturn("Room not found");

        ResponseEntity<ErrorResponse> response =
                handler.handleNotFoundException(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals("ROOM_NOT_FOUND", body.code());
        assertEquals("Room not found", body.message());
        assertTrue(body.fieldErrors().isEmpty());
    }

    @Test
    void shouldReturnConflictForConflictException() {
        ConflictException exception = mock(ConflictException.class);
        when(exception.getCode()).thenReturn(ErrorCode.ROOM_FULL);
        when(exception.getMessage()).thenReturn("Room is full");

        ResponseEntity<ErrorResponse> response =
                handler.handleConflictException(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals("ROOM_FULL", body.code());
        assertEquals("Room is full", body.message());
        assertTrue(body.fieldErrors().isEmpty());
    }

    @Test
    void shouldReturnBadRequestForBadRequestException() {

        BadRequestException exception = mock(BadRequestException.class);
        when(exception.getCode()).thenReturn(ErrorCode.INVALID_PREFERENCES);
        when(exception.getMessage()).thenReturn("Invalid request");

        ResponseEntity<ErrorResponse> response =
                handler.handleBadRequestException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals("INVALID_PREFERENCES", body.code());
        assertEquals("Invalid request", body.message());
        assertTrue(body.fieldErrors().isEmpty());
    }

    @Test
    void shouldReturnForbiddenForForbiddenException() {
        ForbiddenException exception = mock(ForbiddenException.class);
        when(exception.getCode()).thenReturn(ErrorCode.NOT_HOST);
        when(exception.getMessage()).thenReturn("Access denied");

        ResponseEntity<ErrorResponse> response =
                handler.handleForbiddenException(exception);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals("NOT_HOST", body.code());
        assertEquals("Access denied", body.message());
        assertTrue(body.fieldErrors().isEmpty());
    }
}