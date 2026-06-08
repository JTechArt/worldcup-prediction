package com.worldcup.prediction.config;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneControllerAdviceTest {

    @Test
    void addsTimezoneLabelForYerevan() {
        TimezoneControllerAdvice advice = new TimezoneControllerAdvice("Asia/Yerevan");
        Model model = new ExtendedModelMap();
        advice.addTimezoneAttributes(model);
        assertThat(model.getAttribute("appTimezone")).isEqualTo("Asia/Yerevan");
        assertThat(model.getAttribute("timezoneLabel")).isEqualTo("GMT+4 · Yerevan");
    }

    @Test
    void addsTimezoneLabelForUtc() {
        TimezoneControllerAdvice advice = new TimezoneControllerAdvice("UTC");
        Model model = new ExtendedModelMap();
        advice.addTimezoneAttributes(model);
        assertThat(model.getAttribute("appTimezone")).isEqualTo("UTC");
        assertThat(model.getAttribute("timezoneLabel")).isEqualTo("GMT+0 · UTC");
    }

    @Test
    void addsTimezoneLabelForLondon() {
        TimezoneControllerAdvice advice = new TimezoneControllerAdvice("Europe/London");
        Model model = new ExtendedModelMap();
        advice.addTimezoneAttributes(model);
        assertThat(model.getAttribute("appTimezone")).isEqualTo("Europe/London");
        assertThat((String) model.getAttribute("timezoneLabel")).startsWith("GMT");
    }
}
