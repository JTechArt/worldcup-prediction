package com.worldcup.prediction.integration.football;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@Slf4j
public class FootballApiRateLimiter {

    private final RateLimiter rateLimiter = RateLimiter.create(10.0 / 60.0);

    public <T> T call(Supplier<T> apiCall) {
        double waited = rateLimiter.acquire();
        if (waited > 0.5) {
            log.debug("Rate limiter: waited {}s before API call", String.format("%.1f", waited));
        }
        return apiCall.get();
    }
}
