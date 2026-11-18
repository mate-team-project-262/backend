package com.team262.moviematch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.UserRepository;
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

@SpringBootTest(properties = {"title.source=stub",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class AccountPasswordIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String NEW_PASSWORD = "newPassword456";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private record Account(String name, MockHttpSession session, String csrf) {
    }

    private Account register() throws Exception {
        int n = SEQUENCE.incrementAndGet();
        String name = "pwd" + n;                 // another prefix than the other account tests (shared H2 database)
        MvcResult csrfResult = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession anonymous = (MockHttpSession) csrfResult.getRequest().getSession();
        MvcResult registered = mvc.perform(post("/auth/register").session(anonymous)
                        .header("X-CSRF-TOKEN", token(csrfResult))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + name + "@example.com\",\"displayName\":\"" + name
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession();
        MvcResult renewed = mvc.perform(get("/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        return new Account(name, session, token(renewed));
    }

    private String token(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String body(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    private ResultActions changePassword(Account account, String body) throws Exception {
        return mvc.perform(put("/me/password").session(account.session())
                .header("X-CSRF-TOKEN", account.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String name, String password) throws Exception {
        MvcResult csrfResult = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        return mvc.perform(post("/auth/login")
                .session((MockHttpSession) csrfResult.getRequest().getSession())
                .header("X-CSRF-TOKEN", token(csrfResult))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"" + name + "\",\"password\":\"" + password + "\"}"));
    }

    private void assertStillSignedInWithOldPassword(Account account) throws Exception {
        mvc.perform(get("/me").session(account.session())).andExpect(status().isOk());
        login(account.name(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changesThePassword_dropsTheSession_andSignInUsesTheNewPassword() throws Exception {
        Account account = register();

        changePassword(account, body(PASSWORD, NEW_PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(account.session().isInvalid()).isTrue();             // the current session is gone
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());   // ... so the client must sign in again
        User stored = users.findByDisplayNameIgnoreCase(account.name()).orElseThrow();
        assertThat(encoder.matches(NEW_PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(encoder.matches(PASSWORD, stored.getPasswordHash())).isFalse();
        login(account.name(), PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login(account.name(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void passwordsAreNotTrimmed() throws Exception {
        Account account = register();
        String withSpaces = "  newPassword456  ";

        changePassword(account, body(PASSWORD, withSpaces)).andExpect(status().isNoContent());

        login(account.name(), withSpaces).andExpect(status().isOk());
        login(account.name(), "newPassword456").andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCurrentPasswordIsRejected_andNothingChanges() throws Exception {
        Account account = register();

        changePassword(account, body("wrongPassword1", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        assertStillSignedInWithOldPassword(account);
    }

    @Test
    void weakOrInvalidNewPasswordsAreValidationErrors() throws Exception {
        Account account = register();
        String tooLong = "a1" + "x".repeat(71);                           // 73 characters

        for (String weak : new String[]{"short1", "onlyletters", "12345678", tooLong, "        "}) {
            changePassword(account, body(PASSWORD, weak))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field=='newPassword')]").isNotEmpty());
        }
        changePassword(account, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='currentPassword')]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='newPassword')]").isNotEmpty());

        assertStillSignedInWithOldPassword(account);
    }

    @Test
    void passwordRulesAreCheckedBeforeTheCurrentPassword() throws Exception {
        Account account = register();

        changePassword(account, body("wrongPassword1", "weak"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void theSamePasswordIsAConflict_andTheSessionSurvives() throws Exception {
        Account account = register();

        changePassword(account, body(PASSWORD, PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_NOT_CHANGED"));

        assertStillSignedInWithOldPassword(account);
    }

    @Test
    void requiresSessionAndCsrfToken() throws Exception {
        Account account = register();

        // signed in but no CSRF header
        mvc.perform(put("/me/password").session(account.session())
                        .contentType(MediaType.APPLICATION_JSON).content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertStillSignedInWithOldPassword(account);

        // valid CSRF token of an anonymous session, but nobody is signed in
        MvcResult csrfResult = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        mvc.perform(put("/me/password").session((MockHttpSession) csrfResult.getRequest().getSession())
                        .header("X-CSRF-TOKEN", token(csrfResult))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void corsPreflightAllowsPutFromTheFrontend() throws Exception {
        String frontend = "http://localhost:5173";
        mvc.perform(options("/me/password")
                        .header("Origin", frontend)
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", frontend))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void openApiDocumentsPutMePassword() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode api = json.readTree(result.getResponse().getContentAsString());
        assertThat(api.at("/paths/~1me~1password/put/summary").asText()).isEqualTo("Change the password");
        assertThat(api.at("/paths/~1me~1password/put/responses/204").isMissingNode()).isFalse();
        assertThat(api.at("/paths/~1me~1password/put/responses/409").isMissingNode()).isFalse();
    }
}