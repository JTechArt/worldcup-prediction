package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.*;
import com.worldcup.prediction.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionServiceCreateOnBehalfTest {

    @Mock PredictionRepository predictionRepository;
    @Mock UserRepository userRepository;
    @Mock MatchRepository matchRepository;
    @Mock CommunityRepository communityRepository;
    @Mock RoundWindowService roundWindowService;
    @Mock TournamentSettingsService tournamentSettingsService;
    @Mock PredictionWindowService predictionWindowService;
    @Mock ScoringService scoringService;
    @InjectMocks PredictionService predictionService;

    private User user;
    private Match scheduledMatch;
    private Match completedMatch;
    private Community community;

    @BeforeEach
    void setUp() {
        user = User.builder().id(10L).email("user@test.com")
                .firstName("Joe").lastName("Doe")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();

        scheduledMatch = new Match();
        scheduledMatch.setId(1L);
        scheduledMatch.setMatchNumber(1);
        scheduledMatch.setStatus(MatchStatus.SCHEDULED);
        scheduledMatch.setKickoffTime(LocalDateTime.now().plusDays(1));

        completedMatch = new Match();
        completedMatch.setId(2L);
        completedMatch.setMatchNumber(2);
        completedMatch.setStage(MatchStage.GROUP);
        completedMatch.setStatus(MatchStatus.COMPLETED);
        completedMatch.setHomeScore(3);
        completedMatch.setAwayScore(1);
        completedMatch.setKickoffTime(LocalDateTime.now().minusDays(1));

        community = Community.builder().id(5L).name("Test").slug("test").build();
    }

    @Test
    void createOnBehalfOf_savesWithAdminFlag() {
        when(predictionRepository.existsByUserIdAndMatchIdAndCommunityId(10L, 1L, 5L))
                .thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(matchRepository.findById(1L)).thenReturn(Optional.of(scheduledMatch));
        when(communityRepository.findById(5L)).thenReturn(Optional.of(community));
        when(predictionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Prediction result = predictionService.createOnBehalfOf(10L, 1L, 5L, 2, 0, scoringService);

        assertThat(result.isEditedByAdmin()).isTrue();
        assertThat(result.getAdminEditNote()).isNotBlank();
        assertThat(result.getPredictedHome()).isEqualTo(2);
        assertThat(result.getPredictedAway()).isEqualTo(0);
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getCommunity()).isEqualTo(community);
    }

    @Test
    void createOnBehalfOf_throwsIfAlreadyExists() {
        when(predictionRepository.existsByUserIdAndMatchIdAndCommunityId(10L, 1L, 5L))
                .thenReturn(true);

        assertThatThrownBy(() ->
                predictionService.createOnBehalfOf(10L, 1L, 5L, 2, 0, scoringService))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createOnBehalfOf_scoresImmediatelyIfMatchCompleted() {
        when(predictionRepository.existsByUserIdAndMatchIdAndCommunityId(10L, 2L, 5L))
                .thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(matchRepository.findById(2L)).thenReturn(Optional.of(completedMatch));
        when(communityRepository.findById(5L)).thenReturn(Optional.of(community));
        when(predictionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(scoringService.calculatePoints(3, 1, 3, 1)).thenReturn(3);
        when(scoringService.determineScoreResult(3, 1, 3, 1)).thenReturn(PredictionScore.EXACT);

        Prediction result = predictionService.createOnBehalfOf(10L, 2L, 5L, 3, 1, scoringService);

        assertThat(result.getPointsAwarded()).isEqualTo(3);
        assertThat(result.getScoreResult()).isEqualTo(PredictionScore.EXACT);
        verify(scoringService).calculatePoints(3, 1, 3, 1);
    }
}
