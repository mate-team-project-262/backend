package com.team262.moviematch.controller;

import com.team262.moviematch.dto.room.request.CreateRoomRequest;
import com.team262.moviematch.dto.room.response.CreateRoomResponse;
import com.team262.moviematch.dto.room.response.ParticipantDto;
import com.team262.moviematch.dto.room.response.ParticipantWithTokenDto;
import com.team262.moviematch.dto.room.response.PreferencesResponse;
import com.team262.moviematch.dto.room.response.RoomDetailsResponse;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.GlobalExceptionHandler;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.security.CurrentParticipantArgumentResolver;
import com.team262.moviematch.service.ParticipantTokenService;
import com.team262.moviematch.service.RoomService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {
    @Mock private RoomService rooms;
    @Mock private ParticipantTokenService tokens;
    @Mock private Participant currentParticipant;
    private MockMvc mvc;
    private final PreferencesResponse defaults = new PreferencesResponse(ContentType.MOVIES, Set.of(), null);

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RoomController(rooms))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentParticipantArgumentResolver(tokens))
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"displayName\":\"Host\"}",
            "{\"displayName\":\"Host\",\"preferences\":null}"})
    void returnsCreatedAndExistingParticipantField(String request) throws Exception {
        when(rooms.createRoom(new CreateRoomRequest("Host", null))).thenReturn(
                new CreateRoomResponse("K7M2QX", RoomStatus.LOBBY, defaults,
                        new ParticipantWithTokenDto(7, "Host", true, "host-token")));
        mvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/rooms/K7M2QX"))
                .andExpect(jsonPath("$.participant.id").value(7))
                .andExpect(jsonPath("$.participant.token").value("host-token"))
                .andExpect(jsonPath("$.currentParticipant").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"displayName\":\"  \"}",
            "{\"displayName\":\"Host\",\"preferences\":{}}"})
    void validatesRequestBeforeCallingService(String request) throws Exception {
        mvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(rooms, tokens);
    }

    @Test
    void resolvesCurrentParticipantAndSerializesLobbyWithoutTokens() throws Exception {
        when(tokens.requireParticipant("host-token")).thenReturn(currentParticipant);
        when(rooms.getLobby("K7M2QX", currentParticipant)).thenReturn(
                new RoomDetailsResponse("K7M2QX", RoomStatus.LOBBY, defaults, 1, 5,
                        List.of(new ParticipantDto(7, "Host", true))));
        mvc.perform(get("/rooms/K7M2QX").header("X-Participant-Token", "host-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.maxParticipants").value(5))
                .andExpect(jsonPath("$.participants[0].displayName").value("Host"))
                .andExpect(jsonPath("$.participants[0].token").doesNotExist());
        verify(rooms).getLobby("K7M2QX", currentParticipant);
    }

    @Test
    void requiresTokenHeader() throws Exception {
        mvc.perform(get("/rooms/K7M2QX"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        verifyNoInteractions(rooms, tokens);
    }

    @Test
    void rejectsInvalidTokenBeforeCallingRoomService() throws Exception {
        when(tokens.requireParticipant("invalid")).thenThrow(
                new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Invalid participant token"));
        mvc.perform(get("/rooms/K7M2QX").header("X-Participant-Token", "invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        verifyNoInteractions(rooms);
    }

    @Test
    void returnsForbiddenForAnotherRoom() throws Exception {
        when(tokens.requireParticipant("host-token")).thenReturn(currentParticipant);
        when(rooms.getLobby("ABCD23", currentParticipant)).thenThrow(
                new ForbiddenException(ErrorCode.WRONG_ROOM, "Participant belongs to another room"));
        mvc.perform(get("/rooms/ABCD23").header("X-Participant-Token", "host-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WRONG_ROOM"));
    }
}
