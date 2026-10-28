package com.team262.moviematch.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.MySqlTestConfiguration;
import com.team262.moviematch.config.openapi.RoomApiExamples;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "tmdb.base-url=https://example.invalid", "tmdb.api-key=test-placeholder", "room.max-participants=5"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class RoomOpenApiIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private TestRestTemplate http;
    @Autowired private RoomRepository rooms;

    @BeforeEach
    void cleanDatabase() {
        rooms.deleteAllInBatch();
    }

    @Test
    void publishesAccurateOperationsSecurityAndErrors() throws Exception {
        JsonNode spec = json.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(spec.path("openapi").asText()).startsWith("3.0.");
        assertThat(spec.toString()).doesNotContainPattern("[\\p{IsCyrillic}]");
        JsonNode paths = spec.path("paths");
        assertThat(paths.size()).isEqualTo(3);
        JsonNode create = paths.path("/rooms").path("post");
        JsonNode lobby = paths.path("/rooms/{code}").path("get");
        JsonNode join = paths.path("/rooms/{code}/join").path("post");
        assertThat(create.path("responses").properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("201", "400", "415", "500", "503");
        assertThat(lobby.path("responses").properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("200", "401", "403", "404", "500");
        assertThat(join.path("responses").properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("201", "400", "404", "409", "415", "500");
        assertThat(create.path("security").isMissingNode() || create.path("security").isEmpty()).isTrue();
        assertThat(join.path("security").isMissingNode() || join.path("security").isEmpty()).isTrue();
        assertThat(lobby.path("security").get(0).has(OpenApiConfig.PARTICIPANT_TOKEN)).isTrue();
        assertThat(lobby.has("requestBody")).isFalse();
        assertThat(lobby.path("parameters")).hasSize(1);
        assertThat(lobby.path("parameters").get(0).path("name").asText()).isEqualTo("code");
        JsonNode auth = spec.path("components").path("securitySchemes").path(OpenApiConfig.PARTICIPANT_TOKEN);
        assertThat(auth.path("type").asText()).isEqualTo("apiKey");
        assertThat(auth.path("in").asText()).isEqualTo("header");
        assertThat(auth.path("name").asText()).isEqualTo("X-Participant-Token");
        JsonNode schemas = spec.path("components").path("schemas");
        assertThat(schemas.path("ParticipantDto").path("properties").has("token")).isFalse();
        assertThat(schemas.has("Participant")).isFalse();
        assertThat(schemas.path("CreateRoomResponse").path("properties").has("participant")).isTrue();
        assertThat(schemas.path("CreateRoomResponse").path("properties").has("currentParticipant")).isFalse();
        assertThat(schemas.path("JoinRoomResponse").path("properties").has("currentParticipant")).isTrue();
        JsonNode preferences = schemas.path("PreferencesRequest");
        assertThat(preferences.path("required")).contains(json.valueToTree("contentType"));
        JsonNode duration = preferences.path("properties").path("maxDurationMinutes");
        assertThat(duration.path("minimum").asInt()).isEqualTo(30);
        assertThat(duration.path("maximum").asInt()).isEqualTo(150);
        assertThat(duration.path("nullable").asBoolean()).isTrue();
        JsonNode conflicts = join.path("responses").path("409").path("content")
                .path("application/json").path("examples");
        assertThat(conflicts.toString()).contains("ROOM_FULL", "NAME_TAKEN", "ROOM_NOT_JOINABLE");
        assertThat(create.path("responses").path("201").path("headers").has("Location")).isTrue();
    }

    @Test
    void servesSwaggerUiAndYamlWithoutParticipantToken() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andExpect(content().string(containsString("X-Participant-Token")));
    }

    @Test
    void frontendCanCreateJoinAndReadLobbyWithDocumentedFields() {
        JsonNode created = post("/rooms", RoomApiExamples.CREATE_REQUEST, 201);
        String code = created.path("code").asText();
        String hostToken = created.path("participant").path("token").asText();
        JsonNode joined = post("/rooms/" + code + "/join", RoomApiExamples.JOIN_REQUEST, 201);
        String guestToken = joined.path("currentParticipant").path("token").asText();
        assertThat(guestToken).isNotBlank().isNotEqualTo(hostToken);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Participant-Token", guestToken);
        var lobby = http.exchange("/rooms/" + code.toLowerCase(java.util.Locale.ROOT), HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertThat(lobby.getStatusCode().value()).isEqualTo(200);
        assertThat(lobby.getBody().path("participantCount").asInt()).isEqualTo(2);
        assertThat(lobby.getBody().path("participants")).hasSize(2);
        assertThat(lobby.getBody().path("participants").findValues("token")).isEmpty();
        JsonNode nameTaken = post("/rooms/" + code + "/join", "{\"displayName\":\"aNnA\"}", 409);
        assertThat(nameTaken.path("code").asText()).isEqualTo("NAME_TAKEN");
    }

    @Test
    void documentsCapacityConflictAndMissingRoom() {
        JsonNode created = post("/rooms", RoomApiExamples.DEFAULT_REQUEST, 201);
        String code = created.path("code").asText();
        for (int i = 0; i < 4; i++) {
            post("/rooms/" + code + "/join", "{\"displayName\":\"Guest" + i + "\"}", 201);
        }
        JsonNode full = post("/rooms/" + code + "/join", "{\"displayName\":\"Extra\"}", 409);
        assertThat(full.path("code").asText()).isEqualTo("ROOM_FULL");
        String missingCode = code.equals("AAAAAA") ? "BBBBBB" : "AAAAAA";
        JsonNode missing = post("/rooms/" + missingCode + "/join", RoomApiExamples.JOIN_REQUEST, 404);
        assertThat(missing.path("code").asText()).isEqualTo("ROOM_NOT_FOUND");
    }

    @ParameterizedTest
    @EnumSource(value = RoomStatus.class, names = {"VOTING", "FINISHED"})
    void documentsNonJoinableStatus(RoomStatus roomStatus) {
        String code = post("/rooms", RoomApiExamples.DEFAULT_REQUEST, 201).path("code").asText();
        Room room = rooms.findByCode(code).orElseThrow();
        room.setStatus(roomStatus);
        rooms.save(room);
        JsonNode error = post("/rooms/" + code + "/join", RoomApiExamples.JOIN_REQUEST, 409);
        assertThat(error.path("code").asText()).isEqualTo("ROOM_NOT_JOINABLE");
    }

    @Test
    void distinguishesValidationFromFrameworkJsonErrors() {
        JsonNode invalid = post("/rooms", "{\"displayName\":\"ab\"}", 400);
        assertThat(invalid.path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(invalid.path("fieldErrors")).isNotEmpty();
        JsonNode brokenJson = post("/rooms", "{broken", 400);
        assertThat(brokenJson.path("status").asInt()).isEqualTo(400);
        assertThat(brokenJson.has("code")).isFalse();
        assertThat(brokenJson.path("error").asText()).isEqualTo("Bad Request");
        JsonNode invalidEnum = post("/rooms", """
                {"displayName":"Anna","preferences":{"contentType":"INVALID"}}
                """, 400);
        assertThat(invalidEnum.path("status").asInt()).isEqualTo(400);
        HttpHeaders textHeaders = new HttpHeaders();
        textHeaders.setContentType(MediaType.TEXT_PLAIN);
        var unsupported = http.postForEntity("/rooms", new HttpEntity<>("body", textHeaders), JsonNode.class);
        assertThat(unsupported.getStatusCode().value()).isEqualTo(415);
    }

    @Test
    void durationMatchesDocumentedRangeRatherThanButtonEnum() {
        post("/rooms", """
                {"displayName":"Anna","preferences":{"contentType":"MOVIES","maxDurationMinutes":31}}
                """, 201);
        for (int duration : new int[]{29, 151}) {
            JsonNode invalid = post("/rooms", "{\"displayName\":\"Anna\",\"preferences\":{\"contentType\":\"MOVIES\","
                    + "\"maxDurationMinutes\":" + duration + "}}", 400);
            assertThat(invalid.path("code").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(invalid.path("fieldErrors").get(0).path("field").asText())
                    .isEqualTo("preferences.maxDurationMinutes");
        }
    }

    private JsonNode post(String path, String body, int expectedStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = http.postForEntity(path, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode().value()).as("POST %s: %s", path, response.getBody()).isEqualTo(expectedStatus);
        return response.getBody();
    }
}
