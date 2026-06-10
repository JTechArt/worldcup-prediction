package com.worldcup.prediction.web;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.dto.WindowBannerDto;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityWindowBannerAdviceTest {

    @Mock private RoundWindowService roundWindowService;
    @Mock private RoundSubmissionService roundSubmissionService;
    @Mock private HttpServletRequest request;
    @Mock private Authentication authentication;
    @Mock private CustomOAuth2User principal;

    private CommunityWindowBannerAdvice advice;

    private static final Long USER_ID = 7L;
    private static final Long COMMUNITY_ID = 3L;

    @BeforeEach
    void setUp() {
        advice = new CommunityWindowBannerAdvice(roundWindowService, roundSubmissionService);

        Community community = new Community();
        community.setId(COMMUNITY_ID);
        lenient().when(request.getAttribute("community")).thenReturn(community);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getPrincipal()).thenReturn(principal);
        lenient().when(principal.getRole()).thenReturn(UserRole.USER);
        lenient().when(principal.getUserId()).thenReturn(USER_ID);
    }

    @Test
    void returnsNull_whenNoOpenWindow() {
        when(roundWindowService.findAll()).thenReturn(List.of());

        WindowBannerDto result = advice.windowBanner(request, authentication);

        assertThat(result).isNull();
    }

    @Test
    void returnsNull_forSuperAdmin() {
        when(principal.getRole()).thenReturn(UserRole.SUPER_ADMIN);

        WindowBannerDto result = advice.windowBanner(request, authentication);

        assertThat(result).isNull();
        verifyNoInteractions(roundWindowService);
    }

    @Test
    void returnsBannerWithSubmittedTrue_whenUserHasSubmitted() {
        LocalDateTime now = LocalDateTime.now();
        RoundWindow rw = RoundWindow.builder()
                .roundLabel("Matchday 2")
                .autoOpensAt(now.minusHours(1))
                .autoClosesAt(now.plusHours(2))
                .build();
        when(roundWindowService.findAll()).thenReturn(List.of(rw));
        when(roundSubmissionService.hasSubmitted(USER_ID, COMMUNITY_ID, "Matchday 2")).thenReturn(true);

        WindowBannerDto result = advice.windowBanner(request, authentication);

        assertThat(result).isNotNull();
        assertThat(result.roundLabel()).isEqualTo("Matchday 2");
        assertThat(result.submitted()).isTrue();
        assertThat(result.closesAtIso()).isNotNull();
    }

    @Test
    void returnsBannerWithSubmittedFalse_whenUserHasNotSubmitted() {
        LocalDateTime now = LocalDateTime.now();
        RoundWindow rw = RoundWindow.builder()
                .roundLabel("Matchday 2")
                .autoOpensAt(now.minusHours(1))
                .autoClosesAt(now.plusHours(2))
                .build();
        when(roundWindowService.findAll()).thenReturn(List.of(rw));
        when(roundSubmissionService.hasSubmitted(USER_ID, COMMUNITY_ID, "Matchday 2")).thenReturn(false);

        WindowBannerDto result = advice.windowBanner(request, authentication);

        assertThat(result).isNotNull();
        assertThat(result.submitted()).isFalse();
    }

    @Test
    void returnsNullClosesAtIso_forForceOpenWindowWithNoAutoCloseTime() {
        RoundWindow rw = RoundWindow.builder()
                .roundLabel("Matchday 3")
                .overrideStatus(RoundOverrideStatus.FORCE_OPEN)
                .autoClosesAt(null)
                .build();
        when(roundWindowService.findAll()).thenReturn(List.of(rw));
        when(roundSubmissionService.hasSubmitted(anyLong(), anyLong(), anyString())).thenReturn(false);

        WindowBannerDto result = advice.windowBanner(request, authentication);

        assertThat(result).isNotNull();
        assertThat(result.closesAtIso()).isNull();
    }
}
