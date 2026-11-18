package com.team262.moviematch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.analytics.AnalyticsEvent;
import com.team262.moviematch.model.analytics.EventSource;
import com.team262.moviematch.repository.AnalyticsEventRepository;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {"title.source=stub",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class AnalyticsEventIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AnalyticsEventRepository events;
    @Autowired UserRepository users;
    @Autowired ParticipantRepository participants;

    @BeforeEach
    void clean() {
        events.deleteAllInBatch();
    }

    private String sid() {
        return UUID.randomUUID().toString();
    }

    /** One event as JSON; extra is more fields, for example ,"params":{"a":1}. */
    private String ev(String name, String sessionId, String extra) {
        return "{\"name\":\"" + name + "\",\"occurredAt\":\"" + Instant.now() + "\",\"sessionId\":\""
                + sessionId + "\"" + extra + "}";
    }

    private ResultActions send(String body) throws Exception {
        return mvc.perform(post("/event").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<AnalyticsEvent> storedFor(String sessionId) {
        return events.findAll().stream().filter(e -> sessionId.equals(e.getClientSessionId())).toList();
    }

    @Test
    void storesABatchWithoutCsrfOrSession() throws Exception {
        String session = sid();

        send("{\"events\":[" + ev("genres_selected", session, ",\"page\":\"/create\",\"params\":{\"genres\":[28,35]}")
                + "," + ev("room_invite_link_copied", session, "") + "]}")
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        List<AnalyticsEvent> stored = storedFor(session);
        assertThat(stored).hasSize(2);
        AnalyticsEvent first = stored.stream().filter(e -> e.getName().equals("genres_selected")).findFirst().orElseThrow();
        assertThat(first.getSource()).isEqualTo(EventSource.FRONTEND);
        assertThat(first.getPage()).isEqualTo("/create");
        assertThat(first.getPropertiesJson()).isEqualTo("{\"genres\":[28,35]}");
        assertThat(first.getReceivedAt()).isNotNull();
        assertThat(first.getUserId()).isNull();
        assertThat(first.getParticipantId()).isNull();
        assertThat(first.getRoomId()).isNull();
    }

    @Test
    void acceptsASingleEventObject() throws Exception {
        String session = sid();

        send(ev("like_button", session, "")).andExpect(status().isAccepted());

        assertThat(storedFor(session)).hasSize(1);
    }

    @Test
    void acceptsTextPlainBodiesLikeNavigatorSendBeacon() throws Exception {
        String session = sid();

        mvc.perform(post("/event").contentType(MediaType.TEXT_PLAIN)
                        .content("{\"events\":[" + ev("settings_clicked", session, "") + "]}"))
                .andExpect(status().isAccepted());

        assertThat(storedFor(session)).hasSize(1);
    }

    @Test
    void invalidEventsAreDroppedButTheValidOnesAreKept() throws Exception {
        String session = sid();

        send("{\"events\":[" + ev("settings_clicked", session, "") + ","
                + ev("movie_swiped", session, "") + ","                       // not in the specification
                + ev("movie_won", session, "") + ","                          // server-only event
                + "{\"name\":\"like_button\",\"sessionId\":\"nope\",\"occurredAt\":\"" + Instant.now() + "\"},"
                + ev("sign_in_clicked", session, "") + "]}")
                .andExpect(status().isAccepted());

        assertThat(storedFor(session)).extracting(AnalyticsEvent::getName)
                .containsExactlyInAnyOrder("settings_clicked", "sign_in_clicked");
    }

    @Test
    void anEmptyEventListIsAccepted() throws Exception {
        send("{\"events\":[]}").andExpect(status().isAccepted());

        assertThat(events.count()).isZero();
    }

    @Test
    void malformedRequestsAreRejected() throws Exception {
        send("not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_BATCH"));
        send("").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_BATCH"));
        send("[1,2]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_BATCH"));
        send("{\"something\":1}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_BATCH"));
        send("{\"events\":\"x\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_BATCH"));

        String session = sid();
        StringBuilder tooMany = new StringBuilder("{\"events\":[");
        for (int i = 0; i < 51; i++) {
            tooMany.append(i == 0 ? "" : ",").append(ev("settings_clicked", session, ""));
        }
        send(tooMany.append("]}").toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_BATCH"));
        assertThat(events.count()).isZero();
    }

    @Test
    void aTooLargeBodyIsRejected() throws Exception {
        send("{\"events\":[" + ev("settings_clicked", sid(), ",\"pad\":\"" + "x".repeat(70_000) + "\"") + "]}")
                .andExpect(status().isPayloadTooLarge());
        assertThat(events.count()).isZero();
    }

    @Test
    void personalDataNeverReachesTheDatabase() throws Exception {
        String session = sid();

        send(ev("guest_name_entered", session, ",\"page\":\"/room/K7M2QX?ref=1\","
                + "\"params\":{\"name\":\"Alex\",\"email\":\"a@b.c\",\"roomCode\":\"K7M2QX\",\"length\":4}"))
                .andExpect(status().isAccepted());

        AnalyticsEvent stored = storedFor(session).getFirst();
        assertThat(stored.getPage()).isEqualTo("/room/:code");
        assertThat(stored.getPropertiesJson()).isEqualTo("{\"length\":4}");
    }

    @Test
    void aRetriedEventWithTheSameEventIdIsStoredOnce() throws Exception {
        String session = sid();
        String eventId = UUID.randomUUID().toString();
        String body = "{\"events\":[" + ev("settings_clicked", session, ",\"eventId\":\"" + eventId + "\"") + "]}";

        send(body).andExpect(status().isAccepted());
        send(body).andExpect(status().isAccepted());
        send("{\"events\":[" + ev("settings_clicked", session, ",\"eventId\":\"" + eventId + "\"") + ","
                + ev("settings_clicked", session, ",\"eventId\":\"" + eventId + "\"") + "]}")
                .andExpect(status().isAccepted());

        assertThat(storedFor(session)).hasSize(1);
    }

    @Test
    void recognizesTheSignedInUserFromTheSessionCookie() throws Exception {
        int n = SEQUENCE.incrementAndGet();
        String name = "evt" + n;
        MvcResult csrfResult = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession anonymous = (MockHttpSession) csrfResult.getRequest().getSession();
        MvcResult registered = mvc.perform(post("/auth/register").session(anonymous)
                        .header("X-CSRF-TOKEN", json.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + name + "@example.com\",\"displayName\":\"" + name
                                + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated()).andReturn();
        MockHttpSession signedIn = (MockHttpSession) registered.getRequest().getSession();
        String session = sid();

        mvc.perform(post("/event").session(signedIn).contentType(MediaType.APPLICATION_JSON)
                        .content(ev("settings_clicked", session, "")))
                .andExpect(status().isAccepted());                      // no CSRF header

        assertThat(storedFor(session).getFirst().getUserId())
                .isEqualTo(users.findByDisplayNameIgnoreCase(name).orElseThrow().getId());
    }

    @Test
    void attachesParticipantAndRoomFromTheTokenHeader_andIgnoresAWrongToken() throws Exception {
        MvcResult created = mvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Anna\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode participant = json.readTree(created.getResponse().getContentAsString()).get("participant");
        String token = participant.get("token").asText();
        String session = sid();

        mvc.perform(post("/event").header("X-Participant-Token", token).contentType(MediaType.APPLICATION_JSON)
                        .content(ev("start_session_clicked", session, "")))
                .andExpect(status().isAccepted());
        String otherSession = sid();
        mvc.perform(post("/event").header("X-Participant-Token", "unknown-token").contentType(MediaType.APPLICATION_JSON)
                        .content(ev("start_session_clicked", otherSession, "")))
                .andExpect(status().isAccepted());

        Participant stored = participants.findByToken(token).orElseThrow();
        AnalyticsEvent withToken = storedFor(session).getFirst();
        assertThat(withToken.getParticipantId()).isEqualTo(stored.getId());
        assertThat(withToken.getRoomId()).isEqualTo(stored.getRoom().getId());
        AnalyticsEvent withWrongToken = storedFor(otherSession).getFirst();
        assertThat(withWrongToken.getParticipantId()).isNull();
        assertThat(withWrongToken.getRoomId()).isNull();
    }

    @Test
    void corsPreflightAllowsThePostFromTheFrontend() throws Exception {
        String frontend = "http://localhost:5173";
        mvc.perform(options("/event")
                        .header("Origin", frontend)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-participant-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", frontend))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void openApiDocumentsTheEndpoint() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode api = json.readTree(result.getResponse().getContentAsString());
        assertThat(api.at("/paths/~1event/post/summary").asText()).isEqualTo("Collect analytics events");
        assertThat(api.at("/paths/~1event/post/responses/202").isMissingNode()).isFalse();
        assertThat(api.at("/paths/~1event/post/responses/400").isMissingNode()).isFalse();
    }
}
