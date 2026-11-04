package com.team262.moviematch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(MySqlTestConfiguration.class)
@AutoConfigureMockMvc
class MovieMatchApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousApiRequestsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/movies")).andExpect(status().isNotFound());
    }
}
