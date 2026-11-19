package com.team262.moviematch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.model.PasswordResetToken;
import com.team262.moviematch.repository.PasswordResetTokenRepository;
import com.team262.moviematch.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"title.source=stub",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "management.health.mail.enabled=false"})
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository resetTokens;
    @MockitoBean JavaMailSender mailSender;

    @Test
    void resetsPasswordWithEmailedLinkAndRevokesOldSession() throws Exception {
        String name = "reset" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = name + "@example.com";
        String oldPassword = "password123";
        String newPassword = "newPassword456";

        MvcResult registrationCsrf = mvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        MvcResult registration = mvc.perform(post("/auth/register")
                        .session((MockHttpSession) registrationCsrf.getRequest().getSession())
                        .header("X-CSRF-TOKEN", csrf(registrationCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterBody(email, name, oldPassword))))
                .andExpect(status().isCreated()).andReturn();
        MockHttpSession oldSession = (MockHttpSession) registration.getRequest().getSession();

        MvcResult resetCsrf = mvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession anonymousSession = (MockHttpSession) resetCsrf.getRequest().getSession();
        String csrf = csrf(resetCsrf);
        mvc.perform(post("/auth/forgot-password").session(anonymousSession)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new EmailBody(email))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000)).send(mail.capture());
        String body = mail.getValue().getText();
        assertThat(body).isNotNull();
        String token = body.split("\\?token=", 2)[1].split("\\s", 2)[0];

        Long userId = users.findByEmail(email).orElseThrow().getId();
        List<PasswordResetToken> storedTokens = resetTokens.findAll();
        PasswordResetToken storedToken = storedTokens.stream()
                .filter(candidate -> candidate.getUserId().equals(userId))
                .findFirst().orElseThrow();
        assertThat(storedToken.getTokenHash()).isNotEqualTo(token);

        mvc.perform(get("/auth/reset-password/check").param("token", token))
                .andExpect(status().isNoContent());
        mvc.perform(post("/auth/reset-password").session(anonymousSession)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ResetBody(token, newPassword))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/me").session(oldSession)).andExpect(status().isUnauthorized());
        assertThat(resetTokens.findById(storedToken.getId())).isEmpty();
        login(name, oldPassword).andExpect(status().isUnauthorized());
        login(name, newPassword).andExpect(status().isOk());
    }

    private String csrf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private org.springframework.test.web.servlet.ResultActions login(String name, String password)
            throws Exception {
        MvcResult csrfResult = mvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        return mvc.perform(post("/auth/login")
                .session((MockHttpSession) csrfResult.getRequest().getSession())
                .header("X-CSRF-TOKEN", csrf(csrfResult))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginBody(name, password))));
    }

    private record RegisterBody(String email, String displayName, String password) {}
    private record EmailBody(String email) {}
    private record ResetBody(String token, String newPassword) {}
    private record LoginBody(String displayName, String password) {}
}
