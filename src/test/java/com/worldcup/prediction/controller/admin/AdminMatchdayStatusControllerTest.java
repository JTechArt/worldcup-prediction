package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.*;
import com.worldcup.prediction.dto.MemberSubmissionStatusDto;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMatchdayStatusControllerTest {

    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMembershipRepository membershipRepository;
    @Mock private RoundWindowService roundWindowService;
    @Mock private RoundSubmissionService roundSubmissionService;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    private AdminMatchdayStatusController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminMatchdayStatusController(communityRepository, membershipRepository,
                roundWindowService, roundSubmissionService, userRepository, emailService);
        ReflectionTestUtils.setField(controller, "timezoneId", "UTC");
        controller.init(); // simulate @PostConstruct — initialises appZone
    }

    @Test
    void statusPage_returnsCorrectModelAttributes() {
        Long communityId = 1L;
        String round = "Matchday 1";

        Community community = new Community();
        community.setId(communityId);
        community.setName("Test League");

        User submittedUser = User.builder().id(10L).firstName("Alice").lastName("Smith")
                .email("alice@example.com").build();
        User pendingUser = User.builder().id(20L).firstName("Bob").lastName("Jones")
                .email("bob@example.com").build();

        CommunityMembership m1 = new CommunityMembership();
        m1.setUser(submittedUser);
        CommunityMembership m2 = new CommunityMembership();
        m2.setUser(pendingUser);

        RoundWindow rw = RoundWindow.builder().roundLabel(round)
                .autoOpensAt(LocalDateTime.now().minusHours(1))
                .autoClosesAt(LocalDateTime.now().plusHours(3)).build();

        when(communityRepository.findById(communityId)).thenReturn(Optional.of(community));
        when(roundWindowService.findAll()).thenReturn(List.of(rw));
        when(membershipRepository.findByCommunityIdAndStatusWithUser(communityId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(m1, m2));

        RoundSubmission rs = RoundSubmission.builder()
                .userId(10L).communityId(communityId).roundLabel(round)
                .submittedAt(LocalDateTime.now().minusMinutes(30)).build();
        when(roundSubmissionService.findStatusesForCommunityRound(communityId, round))
                .thenReturn(Map.of(10L, rs));

        Model model = new ExtendedModelMap();
        String view = controller.statusPage(communityId, round, model);

        assertThat(view).isEqualTo("admin/matchday-status");

        @SuppressWarnings("unchecked")
        List<MemberSubmissionStatusDto> statuses = (List<MemberSubmissionStatusDto>) model.getAttribute("statuses");
        assertThat(statuses).hasSize(2);

        MemberSubmissionStatusDto alice = statuses.stream().filter(s -> s.userId().equals(10L)).findFirst().orElseThrow();
        assertThat(alice.submitted()).isTrue();

        MemberSubmissionStatusDto bob = statuses.stream().filter(s -> s.userId().equals(20L)).findFirst().orElseThrow();
        assertThat(bob.submitted()).isFalse();
    }

    @Test
    void remind_sendsEmailAndRedirects() {
        Long communityId = 1L;
        String round = "Matchday 1";
        Long userId = 10L;

        User user = User.builder().id(userId).firstName("Alice").lastName("Smith")
                .email("alice@example.com").build();
        CommunityMembership membership = new CommunityMembership();
        membership.setUser(user);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByCommunityIdAndUserId(communityId, userId))
                .thenReturn(Optional.of(membership));

        var redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.remind(communityId, round, userId, redirectAttributes);

        verify(emailService).sendPredictionReminder(user, round);
        assertThat(view).isEqualTo("redirect:/admin/communities/" + communityId + "/matchday-status?round=Matchday%201");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
    }

    @Test
    void remind_throwsNotFound_whenUserNotMemberOfCommunity() {
        Long communityId = 1L;
        String round = "Matchday 1";
        Long userId = 99L;

        User user = User.builder().id(userId).firstName("Eve").lastName("Hacker")
                .email("eve@example.com").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByCommunityIdAndUserId(communityId, userId))
                .thenReturn(Optional.empty());

        var redirectAttributes = new RedirectAttributesModelMap();
        assertThatThrownBy(() -> controller.remind(communityId, round, userId, redirectAttributes))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User is not a member of this community");

        verifyNoInteractions(emailService);
    }
}
