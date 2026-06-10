package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import com.worldcup.prediction.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
class AdminUserControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean UserService     userService;
    @MockBean AuditLogService auditLogService;
    @MockBean EmailService    emailService;
    @MockBean UserRepository  userRepository; // required by AccountStatusFilter
    @MockBean CommunityRepository communityRepository; // required by CommunityInterceptor
    @MockBean CommunityMembershipRepository communityMembershipRepository; // required by CommunityInterceptor
    @MockBean RoundWindowService roundWindowService;       // required by CommunityWindowBannerAdvice
    @MockBean RoundSubmissionService roundSubmissionService; // required by CommunityWindowBannerAdvice

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void listUsers_returnsUsersPage() throws Exception {
        when(userService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/users"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/users"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void approveUser_setsStatusActiveAndReturnsFragment() throws Exception {
        User approved = buildUser(1L, UserStatus.ACTIVE);
        when(userService.approveUser(1L)).thenReturn(approved);

        mockMvc.perform(post("/admin/users/1/approve").with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/users :: userRow"));

        verify(auditLogService).log(any(), any(), eq("USER"), eq(1L), anyString());
        verify(emailService).sendApprovalEmail(approved);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void rejectUser_setsStatusDisabledAndReturnsFragment() throws Exception {
        User rejected = buildUser(2L, UserStatus.DISABLED);
        when(userService.rejectUser(2L)).thenReturn(rejected);

        mockMvc.perform(post("/admin/users/2/reject").with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/users :: userRow"));

        verify(auditLogService).log(any(), any(), eq("USER"), eq(2L), anyString());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void disableUser_setsStatusDisabled() throws Exception {
        User disabled = buildUser(3L, UserStatus.DISABLED);
        when(userService.disableUser(3L)).thenReturn(disabled);

        mockMvc.perform(post("/admin/users/3/disable").with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/users :: userRow"));
    }

    private User buildUser(Long id, UserStatus status) {
        User u = new User();
        u.setId(id);
        u.setStatus(status);
        u.setEmail("test" + id + "@example.com");
        u.setFirstName("Test");
        u.setLastName("User");
        return u;
    }
}
