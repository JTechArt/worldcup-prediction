package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.service.LeaderboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaderboardControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LeaderboardService leaderboardService;

    @Test
    void getLeaderboard_returnsOkAndPopulatesModel() throws Exception {
        LeaderboardEntryDto entry = new LeaderboardEntryDto(
                1, 42L, "Alice Smith", "https://avatar.example.com/42",
                "br", 45, 5, 8, 2, true, 0
        );
        when(leaderboardService.getFullLeaderboard()).thenReturn(List.of(entry));

        mockMvc.perform(get("/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("leaderboard"))
                .andExpect(model().attributeExists("entries"))
                .andExpect(model().attributeExists("stages"))
                .andExpect(model().attribute("totalParticipants", 1));
    }

    @Test
    void getLeaderboard_isPublicNoAuthRequired() throws Exception {
        when(leaderboardService.getFullLeaderboard()).thenReturn(List.of());

        mockMvc.perform(get("/leaderboard"))
                .andExpect(status().isOk());
    }
}
