package com.team262.moviematch.security;

import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.service.ParticipantTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentParticipantArgumentResolverTest {

    @Mock
    private ParticipantTokenService participantTokenService;

    @Mock
    private NativeWebRequest webRequest;

    @Mock
    private MethodParameter parameter;

    @InjectMocks
    private CurrentParticipantArgumentResolver resolver;

    @Test
    void resolveArgument_missingHeader_throwsException() {
        when(webRequest.getHeader(CurrentParticipantArgumentResolver.HEADER)).thenReturn(null);

        assertThrows(UnauthorizedException.class, () ->
                resolver.resolveArgument(parameter, null, webRequest, null)
        );

        verifyNoInteractions(participantTokenService);
    }

    @Test
    void resolveArgument_blankHeader_throwsException() {
        when(webRequest.getHeader(CurrentParticipantArgumentResolver.HEADER)).thenReturn("   ");

        assertThrows(UnauthorizedException.class, () ->
                resolver.resolveArgument(parameter, null, webRequest, null)
        );

        verifyNoInteractions(participantTokenService);
    }

    @Test
    void resolveArgument_invalidOrExpiredToken_throwsException() {
        String token = "invalid-token";

        when(webRequest.getHeader(CurrentParticipantArgumentResolver.HEADER)).thenReturn(token);
        when(participantTokenService.requireParticipant(token))
                .thenThrow(new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Invalid participant token"));

        assertThrows(UnauthorizedException.class, () ->
                resolver.resolveArgument(parameter, null, webRequest, null)
        );
    }

    @Test
    void resolveArgument_validToken_returnsParticipant() {
        String validToken = "c9bf9e57-1685-4c89-bafb-38afcb7b0a6d";
        Participant mockParticipant = mock(Participant.class);

        when(webRequest.getHeader(CurrentParticipantArgumentResolver.HEADER)).thenReturn(validToken);
        when(participantTokenService.requireParticipant(validToken)).thenReturn(mockParticipant);

        Object result = resolver.resolveArgument(parameter, null, webRequest, null);

        assertNotNull(result);
        assertEquals(mockParticipant, result);
    }
}