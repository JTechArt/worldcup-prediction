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
 * The old LeaderboardController was removed (leaderboard is now community-scoped).
 * The /leaderboard endpoint is still permitted but may return a 404 or static page.
 * These tests verify that no 500 server error occurs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaderboardControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void getLeaderboard_publicEndpoint_doesNotReturn500() throws Exception {
        // The old /leaderboard endpoint's controller is removed.
        // It may 404 if there's no handler, or Thymeleaf may render the static template if one exists.
        // The important thing is no 500 error.
        mockMvc.perform(get("/leaderboard"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Accept 200 (template still exists) or 404 (controller removed)
                    assert status == 200 || status == 404 :
                            "Expected 200 or 404 but got " + status;
                });
    }
}
