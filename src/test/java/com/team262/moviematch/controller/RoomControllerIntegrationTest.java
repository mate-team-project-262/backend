package com.team262.moviematch.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.MySqlTestConfiguration;
import com.team262.moviematch.dto.room.request.CreateRoomRequest;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.service.ParticipantTokenService;
import com.team262.moviematch.service.RoomService;
import com.team262.moviematch.util.RoomCodeGenerator;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "tmdb.base-url=https://example.invalid", "tmdb.api-key=test-placeholder",
        "room.max-participants=5"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class RoomControllerIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private RoomRepository rooms;
    @Autowired private ParticipantRepository participants;
    @Autowired private RoomService roomService;
    @MockitoBean private RoomCodeGenerator codeGenerator;
    @MockitoSpyBean private ParticipantTokenService tokenService;

    @BeforeEach
    void setUp() {
        // Database cascades remove participants and genres from previous committed requests.
        rooms.deleteAllInBatch();
        when(codeGenerator.generateCode()).thenReturn("K7M2QX");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"displayName\":\"Host\"}",
            "{\"displayName\":\"Host\",\"preferences\":null}"})
    void createsHostWithTokenAndModelDefaults(String request) throws Exception {
        doAnswer(invocation -> {
            Participant host = invocation.getArgument(0);
            assertThat(host.getId()).isNull();
            assertThat(host.getToken()).isNull();
            assertThat(participants.count()).isZero();
            return invocation.callRealMethod();
        }).when(tokenService).issueToken(any());

        JsonNode response = create(request);

        assertThat(response.path("status").asText()).isEqualTo("LOBBY");
        assertThat(response.at("/preferences/contentType").asText()).isEqualTo("MOVIES");
        assertThat(response.at("/preferences/genreIds")).isEmpty();
        assertThat(response.at("/preferences/maxDurationMinutes").isNull()).isTrue();
        assertThat(response.has("currentParticipant")).isFalse();
        assertThat(response.at("/participant/id").asLong()).isPositive();
        assertThat(response.at("/participant/displayName").asText()).isEqualTo("Host");
        assertThat(response.at("/participant/host").asBoolean()).isTrue();
        assertThat(response.at("/participant/token").asText()).isNotBlank();
        Participant stored = participants.findByToken(token(response)).orElseThrow();
        assertThat(stored.getId()).isEqualTo(response.at("/participant/id").asLong());
        assertThat(stored.getTokenCreatedAt()).isNotNull();
        assertThat(stored.getRoom().getCode()).isEqualTo("K7M2QX");
        assertThat(rooms.count()).isEqualTo(1);
        assertThat(participants.count()).isEqualTo(1);
        verify(tokenService).issueToken(any());
    }

    @Test
    void lobbyReturnsPreferencesStatusAndParticipantsWithoutTokens() throws Exception {
        JsonNode created = create("""
                {"displayName":"Host","preferences":{"contentType":"TV_SERIES",
                 "genreIds":[28,35],"maxDurationMinutes":120}}
                """);
        Room room = rooms.findByCode("K7M2QX").orElseThrow();
        Participant guest = new Participant(room, "Guest", false, null, null);
        String guestToken = tokenService.issueToken(guest);
        room.setStatus(RoomStatus.VOTING);
        rooms.save(room);

        String body = mvc.perform(get("/rooms/k7m2qx").header("X-Participant-Token", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("K7M2QX"))
                .andExpect(jsonPath("$.status").value("VOTING"))
                .andExpect(jsonPath("$.preferences.contentType").value("TV_SERIES"))
                .andExpect(jsonPath("$.preferences.maxDurationMinutes").value(120))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.maxParticipants").value(5))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].displayName").value("Host"))
                .andExpect(jsonPath("$.participants[0].host").value(true))
                .andExpect(jsonPath("$.participants[1].id").value(guest.getId()))
                .andExpect(jsonPath("$.participants[1].host").value(false))
                .andReturn().getResponse().getContentAsString();

        JsonNode details = json.readTree(body);
        assertThat(details.at("/preferences/genreIds")).containsExactlyInAnyOrder(
                json.valueToTree(28), json.valueToTree(35));
        assertThat(details.findValues("token")).isEmpty();
        assertThat(body).doesNotContain(token(created), guestToken, "tokenCreatedAt");
    }

    @Test
    void nullGenresBecomeEmpty() throws Exception {
        JsonNode response = create("""
                {"displayName":"Host","preferences":{"contentType":"MOVIES","genreIds":null}}
                """);
        assertThat(response.at("/preferences/genreIds")).isEmpty();
        mvc.perform(get("/rooms/K7M2QX").header("X-Participant-Token", token(response)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.preferences.genreIds").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"displayName\":\"  \"}",
            "{\"displayName\":\"ab\"}", "{\"displayName\":\"123456789012345678901\"}",
            "{\"displayName\":\"Host\",\"preferences\":{}}",
            "{\"displayName\":\"Host\",\"preferences\":{\"contentType\":\"MOVIES\",\"genreIds\":[null]}}"})
    void rejectsInvalidRequestsWithoutWrites(String request) throws Exception {
        mvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(rooms.count()).isZero();
        assertThat(participants.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "unknown-token"})
    void rejectsMissingBlankAndUnknownTokens(String token) throws Exception {
        mvc.perform(get("/rooms/K7M2QX").header("X-Participant-Token", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void rejectsAbsentTokenHeader() throws Exception {
        mvc.perform(get("/rooms/K7M2QX"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        JsonNode response = create("{\"displayName\":\"Host\"}");
        Participant host = participants.findByToken(token(response)).orElseThrow();
        host.setTokenCreatedAt(LocalDateTime.now().minusHours(25));
        participants.save(host);
        mvc.perform(get("/rooms/K7M2QX").header("X-Participant-Token", token(response)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void rejectsValidTokenForDifferentRoom() throws Exception {
        JsonNode first = create("{\"displayName\":\"Host\"}");
        when(codeGenerator.generateCode()).thenReturn("ABCD23");
        create("{\"displayName\":\"Other\"}", "ABCD23");
        mvc.perform(get("/rooms/ABCD23").header("X-Participant-Token", token(first)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WRONG_ROOM"))
                .andExpect(jsonPath("$.participants").doesNotExist());
    }

    @Test
    void returnsNotFoundWithValidToken() throws Exception {
        JsonNode response = create("{\"displayName\":\"Host\"}");
        mvc.perform(get("/rooms/ABCD23").header("X-Participant-Token", token(response)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void retriesCodeCollisionInFreshTransaction() throws Exception {
        rooms.saveAndFlush(new Room("K7M2QX"));
        when(codeGenerator.generateCode()).thenReturn("K7M2QX", "ABCD23");
        JsonNode response = create("{\"displayName\":\"Host\"}", "ABCD23");
        assertThat(response.path("code").asText()).isEqualTo("ABCD23");
        assertThat(rooms.count()).isEqualTo(2);
        assertThat(participants.count()).isEqualTo(1);
        verify(codeGenerator, times(2)).generateCode();
        verify(tokenService).issueToken(any());
    }

    @Test
    void stopsAfterFiveCollisionsWithoutCreatingHost() throws Exception {
        rooms.saveAndFlush(new Room("K7M2QX"));
        mvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Host\"}"))
                .andExpect(status().isServiceUnavailable());
        verify(codeGenerator, times(5)).generateCode();
        verify(tokenService, times(0)).issueToken(any());
        assertThat(rooms.count()).isEqualTo(1);
        assertThat(participants.count()).isZero();
    }

    @Test
    void rollsBackRoomAndHostWhenTokenIssuanceFailsAfterSaving() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Token issuance failed");
        }).when(tokenService).issueToken(any());

        assertThrows(IllegalStateException.class,
                () -> roomService.createRoom(new CreateRoomRequest("Host", null)));
        assertThat(rooms.count()).isZero();
        assertThat(participants.count()).isZero();
    }

    @Test
    void doesNotRetryOtherIntegrityErrors() {
        doThrow(new DataIntegrityViolationException("Unrelated constraint"))
                .when(tokenService).issueToken(any());
        assertThrows(DataIntegrityViolationException.class,
                () -> roomService.createRoom(new CreateRoomRequest("Host", null)));
        verify(codeGenerator).generateCode();
        assertThat(rooms.count()).isZero();
        assertThat(participants.count()).isZero();
    }

    private JsonNode create(String request) throws Exception {
        return create(request, "K7M2QX");
    }

    private JsonNode create(String request, String expectedCode) throws Exception {
        String response = mvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/rooms/" + expectedCode))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String token(JsonNode response) {
        return response.at("/participant/token").asText();
    }
}
