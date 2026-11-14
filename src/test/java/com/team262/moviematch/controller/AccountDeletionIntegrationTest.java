package com.team262.moviematch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.User;
import com.team262.moviematch.model.history.ArchivedParticipant;
import com.team262.moviematch.model.history.GameSessionArchive;
import com.team262.moviematch.repository.GameSessionArchiveRepository;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.repository.UserRepository;
import com.team262.moviematch.service.history.HistoryService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"title.source=stub",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class AccountDeletionIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired ParticipantRepository participants;
    @Autowired GameSessionArchiveRepository archives;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @Autowired HistoryService history;

    private record Account(Long id, String name, MockHttpSession session, String csrf) {
    }

    @Test
    void deletesAccountDetachesParticipantAndAnonymizesSharedArchive() throws Exception {
        Account account = register();
        User owner = users.findById(account.id()).orElseThrow();
        int number = SEQUENCE.incrementAndGet();
        User other = users.save(new User("other-delete-" + number + "@example.com",
                encoder.encode(PASSWORD), "otherDelete" + number));
        Room room = rooms.save(new Room(String.format("D%05d", number % 100000)));
        Participant active = new Participant(room, account.name(), true,
                "delete-token-" + number, LocalDateTime.now());
        active.setUser(owner);
        active = participants.save(active);
        Long activeParticipantId = active.getId();

        ArchivedParticipant archivedOwner = new ArchivedParticipant(activeParticipantId, account.name(), true);
        archivedOwner.setUserId(owner.getId());
        ArchivedParticipant archivedOther = new ArchivedParticipant(activeParticipantId + 1000,
                other.getDisplayName(), false);
        archivedOther.setUserId(other.getId());
        GameSessionArchive archive = archives.saveAndFlush(new GameSessionArchive(
                room.getId(), room.getCode(), ContentType.MOVIES, Instant.now().minusSeconds(60),
                Instant.now(), List.of(archivedOwner, archivedOther), List.of()));

        deleteAccount(account, PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(account.session().isInvalid()).isTrue();
        assertThat(users.findById(account.id())).isEmpty();
        assertThat(participants.findById(activeParticipantId).orElseThrow().getUser()).isNull();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            GameSessionArchive storedArchive = archives.findById(archive.getId()).orElseThrow();
            assertThat(storedArchive.getParticipants())
                    .filteredOn(member -> member.getSourceParticipantId().equals(activeParticipantId))
                    .extracting(ArchivedParticipant::getUserId)
                    .containsExactly((Long) null);
            assertThat(storedArchive.getParticipants())
                    .filteredOn(member -> member.getDisplayName().equals(other.getDisplayName()))
                    .extracting(ArchivedParticipant::getUserId)
                    .containsExactly(other.getId());
        });
        assertThat(history.history(other.getId(), 0, 10).items())
                .extracting(item -> item.sessionId())
                .contains(archive.getId());

        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
        login(account.name(), PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void wrongPasswordKeepsAccountAndSession() throws Exception {
        Account account = register();

        deleteAccount(account, "wrongPassword1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(users.findById(account.id())).isPresent();
        mvc.perform(get("/me").session(account.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.id()));
    }

    @Test
    void requiresSessionCsrfAndNonBlankPassword() throws Exception {
        Account account = register();

        mvc.perform(delete("/me").session(account.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        MvcResult csrfResult = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        mvc.perform(delete("/me")
                        .session((MockHttpSession) csrfResult.getRequest().getSession())
                        .header("X-CSRF-TOKEN", token(csrfResult))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD)))
                .andExpect(status().isUnauthorized());

        deleteAccount(account, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='currentPassword')]").isNotEmpty());
    }

    @Test
    void corsPreflightAllowsDeleteFromFrontend() throws Exception {
        String frontend = "http://localhost:5173";
        mvc.perform(options("/me")
                        .header("Origin", frontend)
                        .header("Access-Control-Request-Method", "DELETE")
                        .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", frontend))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void openApiDocumentsDeleteMe() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode api = json.readTree(result.getResponse().getContentAsString());
        assertThat(api.at("/paths/~1me/delete/summary").asText())
                .isEqualTo("Permanently delete the current account");
        assertThat(api.at("/paths/~1me/delete/responses/204").isMissingNode()).isFalse();
    }

    private Account register() throws Exception {
        int number = SEQUENCE.incrementAndGet();
        String name = "deleteUser" + number;
        MvcResult csrfResult = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession anonymous = (MockHttpSession) csrfResult.getRequest().getSession();
        MvcResult registered = mvc.perform(post("/auth/register").session(anonymous)
                        .header("X-CSRF-TOKEN", token(csrfResult))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + name + "@example.com\",\"displayName\":\"" + name
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode response = json.readTree(registered.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession();
        MvcResult renewed = mvc.perform(get("/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        return new Account(response.get("id").asLong(), name, session, token(renewed));
    }

    private ResultActions deleteAccount(Account account, String password) throws Exception {
        return mvc.perform(delete("/me").session(account.session())
                .header("X-CSRF-TOKEN", account.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(password)));
    }

    private ResultActions login(String name, String password) throws Exception {
        MvcResult csrfResult = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        return mvc.perform(post("/auth/login")
                .session((MockHttpSession) csrfResult.getRequest().getSession())
                .header("X-CSRF-TOKEN", token(csrfResult))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"" + name + "\",\"password\":\"" + password + "\"}"));
    }

    private String body(String password) {
        return "{\"currentPassword\":\"" + password + "\"}";
    }

    private String token(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
