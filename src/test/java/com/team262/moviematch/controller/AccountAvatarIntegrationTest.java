package com.team262.moviematch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"title.source=stub",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class AccountAvatarIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void uploadsServesAndDeletesAvatar() throws Exception {
        String name = "avatar" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        MvcResult firstCsrf = mvc.perform(get("/auth/csrf")).andReturn();
        MvcResult registration = mvc.perform(post("/auth/register")
                        .session((MockHttpSession) firstCsrf.getRequest().getSession())
                        .header("X-CSRF-TOKEN", token(firstCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Registration(name + "@example.com",
                                name, "password123"))))
                .andExpect(status().isCreated()).andReturn();
        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession();
        MvcResult renewed = mvc.perform(get("/auth/csrf").session(session)).andReturn();
        String csrf = token(renewed);

        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile image = new MockMultipartFile("file", "avatar.png", "image/png", png);
        mvc.perform(multipart("/me/avatar").file(image).with(request -> {
                    request.setMethod("PUT");
                    return request;
                }).session(session).header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.startsWith(
                        "/me/avatar/image?v=")));
        mvc.perform(get("/me").session(session))
                .andExpect(jsonPath("$.avatarUrl").isNotEmpty());
        MvcResult served = mvc.perform(get("/me/avatar/image").session(session))
                .andExpect(status().isOk()).andReturn();
        assertThat(served.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(served.getResponse().getHeader("Cache-Control"))
                .isEqualTo("max-age=3600, private");
        assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(png);

        MockMultipartFile invalid = new MockMultipartFile("file", "bad.png", "image/png",
                "not an image".getBytes());
        mvc.perform(multipart("/me/avatar").file(invalid).with(request -> {
                    request.setMethod("PUT");
                    return request;
                }).session(session).header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("file"));

        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(png, 0, oversized, 0, png.length);
        mvc.perform(multipart("/me/avatar").file(new MockMultipartFile(
                        "file", "large.png", "image/png", oversized)).with(request -> {
                    request.setMethod("PUT");
                    return request;
                }).session(session).header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("file"));

        mvc.perform(delete("/me/avatar").session(session).header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isNoContent());
        mvc.perform(get("/me").session(session))
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/me/avatar/image").session(session))
                .andExpect(status().isNotFound());
    }

    private String token(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private record Registration(String email, String displayName, String password) {}
}
