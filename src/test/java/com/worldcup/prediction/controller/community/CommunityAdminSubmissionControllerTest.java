package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.*;
import com.worldcup.prediction.dto.MemberSubmissionStatusDto;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityAdminSubmissionControllerTest {

    @Mock private CommunityMembershipRepository communityMembershipRepository;
    @Mock private RoundWindowService roundWindowService;
    @Mock private RoundSubmissionService roundSubmissionService;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    private CommunityAdminSubmissionController controller;

    private Community testCommunity;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new CommunityAdminSubmissionController(
                roundWindowService, roundSubmissionService,
                communityMembershipRepository, userRepository, emailService);
        ReflectionTestUtils.setField(controller, "timezoneId", "UTC");
        controller.init();

        testCommunity = new Community();
        testCommunity.setId(1L);
        testCommunity.setName("Test League");
        testCommunity.setSlug("test-league");

        request = new MockHttpServletRequest();
        request.setAttribute("community", testCommunity);
    }

    @Test
    void statusPage_returnsOk_forCommunityAdmin() {
        String round = "Matchday 1";

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

        when(roundWindowService.findAll()).thenReturn(List.of(rw));
        when(communityMembershipRepository.findByCommunityIdAndStatus(1L, MembershipStatus.ACTIVE))
                .thenReturn(List.of(m1, m2));

        RoundSubmission rs = RoundSubmission.builder()
                .userId(10L).communityId(1L).roundLabel(round)
                .submittedAt(LocalDateTime.now().minusMinutes(30)).build();
        when(roundSubmissionService.findStatusesForCommunityRound(1L, round))
                .thenReturn(Map.of(10L, rs));

        Model model = new ExtendedModelMap();
        String view = controller.statusPage("test-league", round, request, model);

        assertThat(view).isEqualTo("community/admin/submission-status");

        @SuppressWarnings("unchecked")
        List<MemberSubmissionStatusDto> statuses = (List<MemberSubmissionStatusDto>) model.getAttribute("statuses");
        assertThat(statuses).hasSize(2);

        MemberSubmissionStatusDto alice = statuses.stream()
                .filter(s -> s.userId().equals(10L)).findFirst().orElseThrow();
        assertThat(alice.submitted()).isTrue();

        MemberSubmissionStatusDto bob = statuses.stream()
                .filter(s -> s.userId().equals(20L)).findFirst().orElseThrow();
        assertThat(bob.submitted()).isFalse();

        assertThat(model.getAttribute("community")).isEqualTo(testCommunity);
        assertThat(model.getAttribute("slug")).isEqualTo("test-league");
        assertThat(model.getAttribute("selectedRound")).isEqualTo(round);
        assertThat(model.getAttribute("closesAtIso")).isNotNull();
    }

    @Test
    void statusPage_defaultsToOpenRound_whenNoRoundParam() {
        String round = "Matchday 2";
        RoundWindow rw = RoundWindow.builder().roundLabel(round)
                .autoOpensAt(LocalDateTime.now().minusHours(1))
                .autoClosesAt(LocalDateTime.now().plusHours(3)).build();

        when(roundWindowService.findAll()).thenReturn(List.of(rw));
        when(communityMembershipRepository.findByCommunityIdAndStatus(1L, MembershipStatus.ACTIVE))
                .thenReturn(List.of());
        when(roundSubmissionService.findStatusesForCommunityRound(1L, round))
                .thenReturn(Map.of());

        Model model = new ExtendedModelMap();
        controller.statusPage("test-league", null, request, model);

        assertThat(model.getAttribute("selectedRound")).isEqualTo(round);
    }

    @Test
    void remind_sendsEmailAndRedirects() {
        String round = "Matchday 1";
        Long userId = 10L;

        User user = User.builder().id(userId).firstName("Alice").lastName("Smith")
                .email("alice@example.com").build();
        CommunityMembership membership = new CommunityMembership();
        membership.setUser(user);

        when(communityMembershipRepository.findByCommunityIdAndUserId(1L, userId))
                .thenReturn(Optional.of(membership));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.remind("test-league", userId, round, request, redirectAttributes);

        verify(emailService).sendPredictionReminder(user, round);
        assertThat(view).startsWith("redirect:/c/test-league/admin/submission-status");
        assertThat(view).contains("round=");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
    }

    @Test
    void remind_throwsNotFound_whenUserNotMemberOfCommunity() {
        String round = "Matchday 1";
        Long userId = 99L;

        when(communityMembershipRepository.findByCommunityIdAndUserId(1L, userId))
                .thenReturn(Optional.empty());

        var redirectAttributes = new RedirectAttributesModelMap();
        assertThatThrownBy(() -> controller.remind("test-league", userId, round, request, redirectAttributes))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User is not a member of this community");

        verifyNoInteractions(emailService);
    }
}
