package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.enums.AuditAction;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.*;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminMatchController.class)
class AdminMatchControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean MatchAdminService matchAdminService;
    @MockBean AuditLogService   auditLogService;
    @MockBean EmailService      emailService;
    @MockBean UserService       userService;
    @MockBean UserRepository    userRepository; // required by AccountStatusFilter
    @MockBean CommunityRepository communityRepository; // required by CommunityInterceptor
    @MockBean CommunityMembershipRepository communityMembershipRepository; // required by CommunityInterceptor
    @MockBean RoundWindowService roundWindowService;
    @MockBean RoundSubmissionService roundSubmissionService; // required by CommunityWindowBannerAdvice
    @MockBean TournamentSettingsService tournamentSettingsService; // required by AdminWindowBannerAdvice
    @MockBean PredictionWindowService predictionWindowService; // required by AdminWindowBannerAdvice

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void listMatches_returnsMatchesPage() throws Exception {
        when(matchAdminService.findAllOrderByKickoffAsc()).thenReturn(List.of());
        when(roundWindowService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/matches"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/matches"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void enterResult_scoresAndRedirects() throws Exception {
        Match match = buildMatch(10L, "Brazil", "Argentina");
        when(matchAdminService.setResult(10L, 2, 1)).thenReturn(match);

        mockMvc.perform(post("/admin/matches/10/result")
                       .param("homeScore", "2")
                       .param("awayScore", "1")
                       .with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/matches"));

        verify(matchAdminService).scoreAllPredictions(10L);
        verify(auditLogService).log(any(), any(), eq("MATCH"), eq(10L), anyString());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void openRound_opensAndRedirects() throws Exception {
        when(roundWindowService.openRound("Group Stage MD1"))
                .thenReturn(RoundWindow.builder().roundLabel("Group Stage MD1").build());

        mockMvc.perform(post("/admin/matches/rounds/Group Stage MD1/open").with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/matches"));

        verify(auditLogService).log(any(), eq(AuditAction.ROUND_WINDOW_OPENED), eq("ROUND"), isNull(), anyString());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void closeRound_closesAndRedirects() throws Exception {
        when(roundWindowService.closeRound("Group Stage MD1"))
                .thenReturn(RoundWindow.builder().roundLabel("Group Stage MD1").build());

        mockMvc.perform(post("/admin/matches/rounds/Group Stage MD1/close").with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/matches"));

        verify(auditLogService).log(any(), eq(AuditAction.ROUND_WINDOW_CLOSED), eq("ROUND"), isNull(), anyString());
    }

    private Match buildMatch(Long id, String homeName, String awayName) {
        Team home = Team.builder().name(homeName).flagCode("br").fifaCode("BRA").build();
        Team away = Team.builder().name(awayName).flagCode("ar").fifaCode("ARG").build();
        return Match.builder()
                .id(id)
                .homeTeam(home)
                .awayTeam(away)
                .kickoffTime(LocalDateTime.now().plusDays(1))
                .stage(MatchStage.GROUP)
                .matchNumber(1)
                .roundLabel("Group Stage MD1")
                .build();
    }
}
