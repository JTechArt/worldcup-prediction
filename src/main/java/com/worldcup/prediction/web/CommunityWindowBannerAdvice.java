package com.worldcup.prediction.web;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.dto.WindowBannerDto;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import com.worldcup.prediction.service.TournamentSettingsService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@ControllerAdvice(basePackages = "com.worldcup.prediction.controller.community")
@RequiredArgsConstructor
public class CommunityWindowBannerAdvice {

    private final RoundWindowService roundWindowService;
    private final RoundSubmissionService roundSubmissionService;
    private final TournamentSettingsService tournamentSettingsService;
    private final PredictionWindowService predictionWindowService;

    @Value("${app.timezone:UTC}")
    private String timezoneId;

    private ZoneId appZone;

    @PostConstruct
    void init() {
        appZone = ZoneId.of(timezoneId);
    }

    @ModelAttribute("windowBanner")
    public WindowBannerDto windowBanner(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        if (!(authentication.getPrincipal() instanceof CustomOAuth2User user)) return null;

        Community community = (Community) request.getAttribute("community");
        if (community == null) return null;

        LocalDateTime now = LocalDateTime.now();
        WindowMode mode = tournamentSettingsService.getEffectiveMode(community.getId());

        if (mode == WindowMode.DAILY) {
            return predictionWindowService.findAllGlobal().stream()
                    .filter(pw -> pw.getStatus() == PredictionWindowStatus.OPEN
                            || pw.getOverrideStatus() == RoundOverrideStatus.FORCE_OPEN)
                    .findFirst()
                    .map(pw -> {
                        String closesAtIso = pw.getEffectiveCloseAt() != null
                                ? pw.getEffectiveCloseAt().atZone(appZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                : null;
                        boolean submitted = user.getRole() != UserRole.SUPER_ADMIN
                                && roundSubmissionService.hasSubmittedForWindow(
                                        user.getUserId(), community.getId(), pw.getId());
                        return new WindowBannerDto(pw.getLabel(), closesAtIso, submitted);
                    })
                    .orElse(null);
        }

        // ROUND mode — existing logic
        return roundWindowService.findAll().stream()
                .filter(rw -> isOpen(rw, now))
                .findFirst()
                .map(rw -> {
                    String closesAtIso = rw.getAutoClosesAt() != null
                            ? rw.getAutoClosesAt().atZone(appZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            : null;
                    // SUPER_ADMIN has no submission — always show countdown (no submit link)
                    boolean submitted = user.getRole() != UserRole.SUPER_ADMIN
                            && roundSubmissionService.hasSubmitted(user.getUserId(), community.getId(), rw.getRoundLabel());
                    return new WindowBannerDto(rw.getRoundLabel(), closesAtIso, submitted);
                })
                .orElse(null);
    }

    private boolean isOpen(RoundWindow rw, LocalDateTime now) {
        if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_OPEN) return true;
        if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_CLOSED) return false;
        if (rw.getAutoOpensAt() == null || rw.getAutoClosesAt() == null) return false;
        return !now.isBefore(rw.getAutoOpensAt()) && now.isBefore(rw.getAutoClosesAt());
    }
}
