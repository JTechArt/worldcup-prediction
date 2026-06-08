package com.worldcup.prediction.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

@ControllerAdvice
public class TimezoneControllerAdvice {

    private final String timezoneId;
    private final String timezoneLabel;

    public TimezoneControllerAdvice(@Value("${app.timezone}") String timezoneId) {
        this.timezoneId = timezoneId;
        this.timezoneLabel = buildLabel(timezoneId);
    }

    @ModelAttribute
    public void addTimezoneAttributes(Model model) {
        model.addAttribute("appTimezone", timezoneId);
        model.addAttribute("timezoneLabel", timezoneLabel);
    }

    private static String buildLabel(String zoneId) {
        ZoneOffset offset = ZoneId.of(zoneId).getRules().getOffset(Instant.now());
        int totalSeconds = offset.getTotalSeconds();
        int hours = totalSeconds / 3600;
        int minutes = Math.abs((totalSeconds % 3600) / 60);
        String sign = hours >= 0 ? "+" : "-";
        String gmtPart = minutes == 0
            ? "GMT" + sign + Math.abs(hours)
            : String.format("GMT%s%d:%02d", sign, Math.abs(hours), minutes);
        String[] parts = zoneId.split("/");
        String city = parts[parts.length - 1].replace("_", " ");
        return gmtPart + " · " + city;
    }
}
