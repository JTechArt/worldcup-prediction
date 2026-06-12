package com.worldcup.prediction.integration.football;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldcup.prediction.integration.football.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class FootballApiClient {

    private static final String BASE = "https://api.football-data.org/v4";
    private static final String MATCHES_URL   = BASE + "/competitions/WC/matches?season=2026";
    private static final String TEAMS_URL     = BASE + "/competitions/WC/teams?season=2026";
    private static final String STANDINGS_URL = BASE + "/competitions/WC/standings?season=2026";
    private static final String MATCH_URL     = BASE + "/matches/{id}";
    private static final String SCORERS_URL   = BASE + "/competitions/WC/scorers?limit=20&season=2026";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final boolean enabled;

    public FootballApiClient(RestTemplate restTemplate,
                             ObjectMapper objectMapper,
                             @Value("${app.football.api.enabled:false}") boolean enabled,
                             @Value("${football.api.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.apiKey = apiKey;
        if (!enabled) log.info("FootballApiClient: disabled — live sync off");
    }

    public FootballApiResponseDto fetchAllMatches() {
        return get(MATCHES_URL, FootballApiResponseDto.class);
    }

    public FootballApiTeamsResponseDto fetchTeamsWithSquads() {
        return get(TEAMS_URL, FootballApiTeamsResponseDto.class);
    }

    public FootballApiStandingsResponseDto fetchStandings() {
        return get(STANDINGS_URL, FootballApiStandingsResponseDto.class);
    }

    public FootballApiMatchDetailDto fetchMatchDetail(long matchId) {
        if (!enabled || apiKey.isBlank()) return null;
        HttpEntity<Void> req = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<String> raw = restTemplate.exchange(MATCH_URL, HttpMethod.GET, req, String.class, matchId);
            String body = raw.getBody();
            log.info("Match detail raw [id={}]: {}", matchId, body);
            if (body == null) return null;
            return objectMapper.readValue(body, FootballApiMatchDetailDto.class);
        } catch (RestClientException e) {
            log.warn("Failed to fetch match detail id={}: {}", matchId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to deserialize match detail id={}: {}", matchId, e.getMessage());
            return null;
        }
    }

    public FootballApiScorersResponseDto fetchTopScorers() {
        return get(SCORERS_URL, FootballApiScorersResponseDto.class);
    }

    public boolean isEnabled() {
        return enabled && !apiKey.isBlank();
    }

    private <T> T get(String url, Class<T> type) {
        if (!enabled) {
            log.warn("Football API call skipped — FOOTBALL_API_ENABLED=false. Set it to true and restart.");
            return null;
        }
        if (apiKey.isBlank()) {
            log.warn("Football API call skipped — FOOTBALL_API_KEY not configured.");
            return null;
        }
        try {
            ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), type);
            log.debug("Football API [{}] → HTTP {}", url, response.getStatusCode());
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Football API call failed [{}]: {}", url, e.getMessage());
            return null;
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Auth-Token", apiKey);
        return h;
    }
}
