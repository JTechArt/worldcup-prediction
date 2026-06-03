package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.integration.football.dto.FootballApiResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class FootballApiClient {

    private static final String API_URL = "https://api.football-data.org/v4/competitions/WC/matches";

    private final RestTemplate restTemplate;
    private final String apiKey;

    private final boolean enabled;

    public FootballApiClient(RestTemplate restTemplate,
                             @Value("${app.football.api.enabled:false}") boolean enabled,
                             @Value("${football.api.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.apiKey = apiKey;
        if (!enabled) {
            log.info("FootballApiClient: FOOTBALL_API_ENABLED=false — live sync disabled");
        }
    }

    /**
     * Fetches all WC matches from football-data.org.
     * Returns null if the integration is disabled, the API key is missing, or the request fails.
     */
    public FootballApiResponseDto fetchMatches() {
        if (!enabled) {
            log.debug("Football API integration disabled — skipping fetch");
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("football.api.key not configured — skipping API fetch");
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", apiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<FootballApiResponseDto> response =
                    restTemplate.exchange(API_URL, HttpMethod.GET, request, FootballApiResponseDto.class);
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("Failed to fetch matches from football-data.org: {}", e.getMessage());
            return null;
        }
    }
}
