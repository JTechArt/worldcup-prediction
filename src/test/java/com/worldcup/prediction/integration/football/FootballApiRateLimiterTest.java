package com.worldcup.prediction.integration.football;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FootballApiRateLimiterTest {

    @Test
    void call_executesAndReturnsResult() {
        FootballApiRateLimiter limiter = new FootballApiRateLimiter();
        String result = limiter.call(() -> "hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void call_propagatesRuntimeException() {
        FootballApiRateLimiter limiter = new FootballApiRateLimiter();
        assertThatThrownBy(() -> limiter.call(() -> { throw new RuntimeException("boom"); }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }
}
