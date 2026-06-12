package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import com.worldcup.prediction.service.SchedulerLogService;
import com.worldcup.prediction.service.SchedulerRunnerService;
import com.worldcup.prediction.service.TournamentSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminSchedulerController.class)
class AdminSchedulerControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SchedulerLogService logService;
    @MockBean SchedulerRunnerService runnerService;
    @MockBean UserRepository userRepository;
    @MockBean CommunityRepository communityRepository;
    @MockBean CommunityMembershipRepository communityMembershipRepository;
    @MockBean RoundWindowService roundWindowService;
    @MockBean RoundSubmissionService roundSubmissionService;
    @MockBean TournamentSettingsService tournamentSettingsService;
    @MockBean PredictionWindowService predictionWindowService;

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void schedulerPage_returnsView() throws Exception {
        when(logService.buildCards()).thenReturn(List.of());
        when(logService.findAll(null, null)).thenReturn(List.of());
        mockMvc.perform(get("/admin/schedulers"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/schedulers"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void schedulerPage_withFilters_passesFiltersToService() throws Exception {
        when(logService.buildCards()).thenReturn(List.of());
        when(logService.findAll("MATCH_RESULT", SchedulerJobStatus.FAILED)).thenReturn(List.of());
        mockMvc.perform(get("/admin/schedulers").param("job", "MATCH_RESULT").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/schedulers"));
        verify(logService).findAll("MATCH_RESULT", SchedulerJobStatus.FAILED);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void runJob_triggersRunnerAndRedirects() throws Exception {
        when(runnerService.run(SchedulerJobType.MATCH_RESULT)).thenReturn("SUCCESS: 2 match(es) scored");
        mockMvc.perform(post("/admin/schedulers/run/MATCH_RESULT").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/schedulers"))
                .andExpect(flash().attributeExists("successMessage"));
        verify(runnerService).run(SchedulerJobType.MATCH_RESULT);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void runJob_unknownJobName_redirectsWithError() throws Exception {
        mockMvc.perform(post("/admin/schedulers/run/UNKNOWN_JOB").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/schedulers"))
                .andExpect(flash().attributeExists("errorMessage"));
        verify(runnerService, never()).run(any());
    }

    @Test
    void schedulerPage_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/schedulers"))
                .andExpect(status().is3xxRedirection());
    }
}
