package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.integration.football.dto.FootballApiResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FootballApiClientTest {

    private MockRestServiceServer mockServer;
    private FootballApiClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new FootballApiClient(restTemplate, "test-api-key");
    }

    @Test
    void fetchMatches_returnsParsedMatches() {
        String json = """
            {
              "count": 1,
              "matches": [
                {
                  "id": 123,
                  "utcDate": "2026-06-11T19:00:00Z",
                  "status": "FINISHED",
                  "matchday": 1,
                  "stage": "GROUP_STAGE",
                  "group": "GROUP_A",
                  "homeTeam": { "id": 1, "name": "Mexico", "shortName": "Mexico", "tla": "MEX", "crest": "" },
                  "awayTeam": { "id": 2, "name": "Canada", "shortName": "Canada", "tla": "CAN", "crest": "" },
                  "score": {
                    "winner": "HOME_TEAM",
                    "duration": "REGULAR",
                    "fullTime": { "home": 2, "away": 1 },
                    "halfTime": { "home": 1, "away": 0 }
                  }
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/WC/matches"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Auth-Token", "test-api-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        FootballApiResponseDto response = client.fetchMatches();

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().get(0).status()).isEqualTo("FINISHED");
        assertThat(response.matches().get(0).score().fullTime().home()).isEqualTo(2);
        assertThat(response.matches().get(0).score().fullTime().away()).isEqualTo(1);
    }

    @Test
    void fetchMatches_onHttpError_returnsNull() {
        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/WC/matches"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        FootballApiResponseDto response = client.fetchMatches();

        mockServer.verify();
        assertThat(response).isNull();
    }

    @Test
    void fetchMatches_onEmptyApiKey_returnsNull() {
        RestTemplate restTemplate2 = new RestTemplate();
        FootballApiClient clientNoKey = new FootballApiClient(restTemplate2, "");

        FootballApiResponseDto response = clientNoKey.fetchMatches();

        assertThat(response).isNull();
    }
}
