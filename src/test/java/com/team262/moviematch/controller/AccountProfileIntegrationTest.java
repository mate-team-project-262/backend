package com.team262.moviematch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.repository.UserRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(properties = {"title.source=stub",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class AccountProfileIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    /** A signed-in account: its session and a CSRF token that belongs to that session. */
    private record Account(String name, String email, MockHttpSession session, String csrf) {
    }

    private Account register() throws Exception {
        int n = SEQUENCE.incrementAndGet();
        String name = "usr" + n;
        String email = "usr" + n + "@example.com";
        MvcResult csrfResult = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession anonymous = (MockHttpSession) csrfResult.getRequest().getSession();
        MvcResult registered = mvc.perform(post("/auth/register").session(anonymous)
                        .header("X-CSRF-TOKEN", token(csrfResult))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"displayName\":\"" + name
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession();
        MvcResult renewed = mvc.perform(get("/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        return new Account(name, email, session, token(renewed));
    }

    private String token(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions patch(Account account, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch("/me").session(account.session())
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

    @Test
    void changesNameAndEmail_sessionSurvives_andSignInUsesTheNewName() throws Exception {
        Account account = register();
        String newName = account.name() + "x";
        String newEmail = "changed" + account.name() + "@example.com";

        MvcResult result = patch(account, "{\"displayName\":\"  " + newName + " \",\"email\":\" "
                + newEmail.toUpperCase() + " \",\"currentPassword\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(newName))
                .andExpect(jsonPath("$.email").value(newEmail))       // trimmed and lowercased
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(users.findById(id).orElseThrow().getEmail()).isEqualTo(newEmail);
        // the same session still works: it is bound to the account id, not to the name
        mvc.perform(get("/me").session(account.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(newName));
        // sign-in: new name works (any letter case), old name does not
        login(newName.toUpperCase(), PASSWORD).andExpect(status().isOk());
        login(account.name(), PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void nameOnlyChangeNeedsNoPassword_andCaseOnlyRenameIsAllowed() throws Exception {
        Account account = register();

        patch(account, "{\"displayName\":\"" + account.name().toUpperCase() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(account.name().toUpperCase()))
                .andExpect(jsonPath("$.email").value(account.email()));
    }

    @Test
    void sameValuesAreAcceptedAndChangeNothing() throws Exception {
        Account account = register();

        patch(account, "{\"displayName\":\"" + account.name() + "\",\"email\":\"" + account.email() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(account.name()))
                .andExpect(jsonPath("$.email").value(account.email()));
    }

    @Test
    void emptyOrNullBodyIsAValidationError() throws Exception {
        Account account = register();

        for (String body : new String[]{"{}", "{\"displayName\":null,\"email\":null}"}) {
            patch(account, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("request"));
        }
    }

    @Test
    void invalidFieldsAreRejectedWithFieldErrors() throws Exception {
        Account account = register();

        patch(account, "{\"displayName\":\"ab\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("displayName"));
        patch(account, "{\"displayName\":\"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("displayName"));
        patch(account, "{\"displayName\":\"123456789012345678901\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("displayName"));
        patch(account, "{\"email\":\"not-an-email\",\"currentPassword\":\"" + PASSWORD + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
        patch(account, "{\"email\":\"\",\"currentPassword\":\"" + PASSWORD + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void emailChangeNeedsTheCurrentPassword() throws Exception {
        Account account = register();
        String newEmail = "other" + account.name() + "@example.com";

        patch(account, "{\"email\":\"" + newEmail + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
        patch(account, "{\"email\":\"" + newEmail + "\",\"currentPassword\":\"wrongPassword1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        // nothing was changed
        mvc.perform(get("/me").session(account.session()))
                .andExpect(jsonPath("$.email").value(account.email()));
    }

    @Test
    void takenNameAndEmailAreConflicts_butTheWrongPasswordIsReportedFirst() throws Exception {
        Account first = register();
        Account second = register();

        patch(second, "{\"displayName\":\"" + first.name().toUpperCase() + "\"}")      // ignoring case
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        patch(second, "{\"email\":\"" + first.email() + "\",\"currentPassword\":\"" + PASSWORD + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        patch(second, "{\"email\":\"" + first.email() + "\",\"currentPassword\":\"wrongPassword1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));            // no email enumeration
    }

    @Test
    void requiresSessionAndCsrfToken() throws Exception {
        Account account = register();

        // Authenticated session without CSRF token -> 403
        mvc.perform(MockMvcRequestBuilders.patch("/me")
                        .session(account.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "displayName": "Nobody"
                            }
                            """))
                .andExpect(status().isForbidden());

        // Anonymous session with valid CSRF token -> 401
        MvcResult csrfResult = mvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession anonymousSession =
                (MockHttpSession) csrfResult.getRequest().getSession(false);

        assertThat(anonymousSession).isNotNull();

        mvc.perform(MockMvcRequestBuilders.patch("/me")
                        .session(anonymousSession)
                        .header("X-CSRF-TOKEN", token(csrfResult))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "displayName": "Nobody"
                            }
                            """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPreflightAllowsPatchFromTheFrontend() throws Exception {
        String frontend = "http://localhost:5173";
        mvc.perform(options("/me")
                        .header("Origin", frontend)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", frontend))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void openApiDocumentsPatchMe() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode api = json.readTree(result.getResponse().getContentAsString());
        assertThat(api.at("/paths/~1me/patch/summary").asText())
                .isEqualTo("Update the display name and/or email");
        assertThat(api.at("/paths/~1me/patch/responses/409").isMissingNode()).isFalse();
    }
}