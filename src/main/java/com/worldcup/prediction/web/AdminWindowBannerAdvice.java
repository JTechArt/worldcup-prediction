package com.worldcup.prediction.web;

import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.dto.WindowBannerDto;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.RoundWindowService;
import com.worldcup.prediction.service.TournamentSettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@ControllerAdvice(basePackages = "com.worldcup.prediction.controller.admin")
@RequiredArgsConstructor
public class AdminWindowBannerAdvice {

    private final RoundWindowService roundWindowService;
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
    public WindowBannerDto windowBanner() {
        LocalDateTime now = LocalDateTime.now();
        WindowMode mode = tournamentSettingsService.getEffectiveMode(null);

        if (mode == WindowMode.DAILY) {
            return predictionWindowService.findAllGlobal().stream()
                    .filter(pw -> pw.getStatus() == PredictionWindowStatus.OPEN
                            || pw.getOverrideStatus() == RoundOverrideStatus.FORCE_OPEN)
                    .findFirst()
                    .map(pw -> {
                        String closesAtIso = pw.getEffectiveCloseAt() != null
                                ? pw.getEffectiveCloseAt().atZone(appZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                : null;
                        return new WindowBannerDto(pw.getLabel(), closesAtIso, false);
                    })
                    .orElse(null);
        }

        return roundWindowService.findAll().stream()
                .filter(rw -> isOpen(rw, now))
                .findFirst()
                .map(rw -> {
                    String closesAtIso = rw.getAutoClosesAt() != null
                            ? rw.getAutoClosesAt().atZone(appZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            : null;
                    return new WindowBannerDto(rw.getRoundLabel(), closesAtIso, false);
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
