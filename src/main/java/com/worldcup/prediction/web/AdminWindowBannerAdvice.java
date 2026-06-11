package com.worldcup.prediction.web;

import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.dto.WindowBannerDto;
import com.worldcup.prediction.service.RoundWindowService;
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
