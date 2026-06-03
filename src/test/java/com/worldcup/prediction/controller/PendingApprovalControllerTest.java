package com.worldcup.prediction.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PendingApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pendingPage_redirectsToLogin_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/pending"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void pendingPage_returnsOk_whenAuthenticated() throws Exception {
        mockMvc.perform(get("/pending").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name("pending"));
    }
}
