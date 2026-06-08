package com.worldcup.prediction.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionViewServiceTimezoneTest {

    @Test
    void kickoffIsoContainsOffset() {
        ZoneId zone = ZoneId.of("Asia/Yerevan");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(zone);

        LocalDateTime kickoff = LocalDateTime.of(2026, 6, 11, 19, 0, 0);
        String iso = kickoff.atZone(zone).format(fmt);

        assertThat(iso).isEqualTo("2026-06-11T19:00:00+04:00");
    }

    @Test
    void lockTimeIsoContainsOffset() {
        ZoneId zone = ZoneId.of("Asia/Yerevan");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(zone);

        LocalDateTime lockTime = LocalDateTime.of(2026, 6, 11, 18, 0, 0);
        String iso = lockTime.atZone(zone).format(fmt);

        assertThat(iso).isEqualTo("2026-06-11T18:00:00+04:00");
    }
}
