package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.integration.football.dto.FootballApiResponseDto;
import com.worldcup.prediction.integration.football.dto.FootballApiTeamsResponseDto;
import com.worldcup.prediction.integration.football.dto.FootballApiMatchDetailDto;
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
        client = new FootballApiClient(restTemplate, true, "test-api-key");
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

        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/WC/matches?season=2026"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Auth-Token", "test-api-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        FootballApiResponseDto response = client.fetchAllMatches();

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
        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/WC/matches?season=2026"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        FootballApiResponseDto response = client.fetchAllMatches();

        mockServer.verify();
        assertThat(response).isNull();
    }

    @Test
    void fetchMatches_onEmptyApiKey_returnsNull() {
        RestTemplate restTemplate2 = new RestTemplate();
        FootballApiClient clientNoKey = new FootballApiClient(restTemplate2, true, "");

        FootballApiResponseDto response = clientNoKey.fetchAllMatches();

        assertThat(response).isNull();
    }

    @Test
    void fetchMatches_whenDisabled_returnsNull() {
        RestTemplate restTemplate2 = new RestTemplate();
        FootballApiClient clientDisabled = new FootballApiClient(restTemplate2, false, "test-api-key");

        FootballApiResponseDto response = clientDisabled.fetchAllMatches();

        assertThat(response).isNull();
    }

    @Test
    void fetchTeamsWithSquads_returnsParsedTeams() {
        String json = """
            { "count": 1, "teams": [{
              "id": 773, "name": "Germany", "shortName": "Germany", "tla": "GER",
              "squad": [{ "id": 3359, "name": "Neuer", "position": "Goalkeeper",
                          "dateOfBirth": "1986-03-27", "nationality": "Germany", "shirtNumber": 1 }]
            }]}
            """;
        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/WC/teams?season=2026"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        FootballApiTeamsResponseDto resp = client.fetchTeamsWithSquads();

        mockServer.verify();
        assertThat(resp.teams()).hasSize(1);
        assertThat(resp.teams().get(0).tla()).isEqualTo("GER");
        assertThat(resp.teams().get(0).squad()).hasSize(1);
        assertThat(resp.teams().get(0).squad().get(0).name()).isEqualTo("Neuer");
    }

    @Test
    void fetchMatchDetail_returnsParsedDetail() {
        String json = """
            { "id": 436780, "status": "FINISHED", "matchday": 1, "stage": "GROUP_STAGE",
              "group": "GROUP_A",
              "homeTeam": { "id": 773, "name": "Germany", "tla": "GER", "formation": "4-3-3",
                "lineup": [{ "id": 3359, "name": "Neuer", "position": "Goalkeeper", "shirtNumber": 1 }],
                "bench": [] },
              "awayTeam": { "id": 9, "name": "Australia", "tla": "AUS", "formation": "4-4-2",
                "lineup": [], "bench": [] },
              "score": { "fullTime": { "home": 2, "away": 1 } },
              "goals": [{ "minute": 23, "type": "REGULAR",
                          "team": { "id": 773, "tla": "GER" },
                          "scorer": { "id": 3359, "name": "M\\u00fcller" } }]
            }
            """;
        mockServer.expect(requestTo("https://api.football-data.org/v4/matches/436780"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        FootballApiMatchDetailDto detail = client.fetchMatchDetail(436780L);

        mockServer.verify();
        assertThat(detail).isNotNull();
        assertThat(detail.goals()).hasSize(1);
        assertThat(detail.goals().get(0).scorer().name()).isEqualTo("Müller");
        assertThat(detail.homeTeam().lineup()).hasSize(1);
        assertThat(detail.homeTeam().lineup().get(0).name()).isEqualTo("Neuer");
        assertThat(detail.homeTeam().formation()).isEqualTo("4-3-3");
    }
}
