package com.team262.moviematch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"title.source=stub",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class AccountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ParticipantRepository participants;
    @Autowired PasswordEncoder encoder;

    @Test
    void corsAllowsConfiguredFrontendForAccountAndGameEndpoints() throws Exception {
        String frontend = "http://localhost:5173";
        mvc.perform(options("/auth/login")
                        .header("Origin", frontend)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", frontend))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/rooms")
                        .header("Origin", frontend)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", frontend))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void openApiDocumentsAccountProfileAndHistoryEndpoints() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode api = json.readTree(result.getResponse().getContentAsString());
        assertThat(api.at("/paths/~1auth~1register/post/summary").asText())
                .isEqualTo("Register an account");
        assertThat(api.at("/paths/~1auth~1login/post/summary").asText())
                .isEqualTo("Sign in by display name");
        assertThat(api.at("/paths/~1me~1history/get/summary").asText())
                .isEqualTo("List the current account's game history");
        assertThat(api.at("/components/securitySchemes/AccountSession").isMissingNode()).isFalse();
        assertThat(api.at("/components/securitySchemes/CsrfToken").isMissingNode()).isFalse();
        assertThat(api.at("/components/securitySchemes/ParticipantToken").isMissingNode()).isFalse();
    }

    @Test
    void guestRegistersLogsInLinksAndLogsOut() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        MvcResult guest = mvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Anna\"}")).andExpect(status().isCreated()).andReturn();
        JsonNode participant = json.readTree(guest.getResponse().getContentAsString()).get("participant");
        MvcResult csrfResult = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession anonymousSession = (MockHttpSession) csrfResult.getRequest().getSession();
        String csrf = json.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
        String registration = "{\"email\":\" ANNA@example.com \",\"displayName\":\"Anna\",\"password\":\"password123\"}";
        MvcResult registrationResult = mvc.perform(
                post("/auth/register").session(anonymousSession).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        MockHttpSession registeredSession =
                (MockHttpSession) registrationResult.getRequest().getSession();
        assertThat(registeredSession.getId()).isNotEqualTo(anonymousSession.getId());
        mvc.perform(get("/me").session(registeredSession)).andExpect(status().isOk());
        assertThat(encoder.matches("password123", users.findByEmail("anna@example.com").orElseThrow()
                .getPasswordHash())).isTrue();
        MvcResult registeredCsrf = mvc.perform(get("/auth/csrf").session(registeredSession))
                .andExpect(status().isOk()).andReturn();
        String registeredToken = json.readTree(registeredCsrf.getResponse().getContentAsString())
                .get("token").asText();
        mvc.perform(post("/me/participants/link").session(registeredSession)
                .header("X-CSRF-TOKEN", registeredToken)
                .header("X-Participant-Token", participant.get("token").asText()))
                .andExpect(status().isNoContent());
        assertThat(participants.findById(participant.get("id").asLong()).orElseThrow().getUser())
                .isNotNull();
        mvc.perform(post("/auth/logout").session(registeredSession)
                .header("X-CSRF-TOKEN", registeredToken)).andExpect(status().isNoContent());
        assertThat(registeredSession.isInvalid()).isTrue();

        MvcResult newCsrfResult = mvc.perform(get("/auth/csrf")).andReturn();
        MockHttpSession newAnonymousSession =
                (MockHttpSession) newCsrfResult.getRequest().getSession();
        String newCsrf = json.readTree(newCsrfResult.getResponse().getContentAsString())
                .get("token").asText();
        mvc.perform(post("/auth/register").session(newAnonymousSession)
                .header("X-CSRF-TOKEN", newCsrf)
                .contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isConflict());
        mvc.perform(post("/auth/login").session(newAnonymousSession)
                .header("X-CSRF-TOKEN", newCsrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Anna\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid name or password"));
        MvcResult login = mvc.perform(post("/auth/login").session(newAnonymousSession)
                .header("X-CSRF-TOKEN", newCsrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"aNNa\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession();
        assertThat(authenticated.getId()).isNotEqualTo(newAnonymousSession.getId());
        mvc.perform(get("/me").session(authenticated)).andExpect(status().isOk());
        MvcResult renewed = mvc.perform(get("/auth/csrf").session(authenticated)).andReturn();
        String renewedToken = json.readTree(renewed.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/auth/logout").session(authenticated).header("X-CSRF-TOKEN", renewedToken))
                .andExpect(status().isNoContent());
        assertThat(authenticated.isInvalid()).isTrue();
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
    }
}
