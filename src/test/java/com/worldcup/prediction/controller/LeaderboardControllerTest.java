package com.worldcup.prediction.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The old LeaderboardController was removed (leaderboard is now community-scoped at /c/{slug}/leaderboard).
 * The /leaderboard endpoint requires authentication and redirects anonymous users to login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaderboardControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void getLeaderboard_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/leaderboard"))
                .andExpect(status().is3xxRedirection());
    }
}
