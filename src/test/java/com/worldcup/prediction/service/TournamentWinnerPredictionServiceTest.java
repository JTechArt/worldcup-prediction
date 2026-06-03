package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.TournamentWinnerPrediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.dto.TournamentWinnerPredictionDto;
import com.worldcup.prediction.repository.TeamRepository;
import com.worldcup.prediction.repository.TournamentWinnerPredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TournamentWinnerPredictionServiceTest {

    @Mock private TournamentWinnerPredictionRepository repository;
    @Mock private TeamRepository teamRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScoringService scoringService;

    private TournamentWinnerPredictionService service;

    private User alice;
    private Team brazil;

    @BeforeEach
    void setUp() {
        service = new TournamentWinnerPredictionService(repository, teamRepository, userRepository, scoringService);

        alice = User.builder().id(1L).email("alice@example.com")
                .firstName("Alice").lastName("Smith")
                .status(UserStatus.ACTIVE).role(UserRole.PARTICIPANT).build();

        brazil = Team.builder().id(10L).name("Brazil").fifaCode("BRA").flagCode("br").build();
    }

    @Nested
    @DisplayName("submitOrUpdate")
    class SubmitOrUpdate {

        @Test @DisplayName("creates new prediction when user has none")
        void newPrediction_creates() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(teamRepository.findByFlagCodeIgnoreCase("br")).thenReturn(Optional.of(brazil));
            when(repository.findByUserId(1L)).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TournamentWinnerPrediction result =
                    service.submitOrUpdate(1L, new TournamentWinnerPredictionDto("br"));

            assertThat(result.getTeam().getFlagCode()).isEqualTo("br");
            verify(repository).save(any(TournamentWinnerPrediction.class));
        }

        @Test @DisplayName("updates existing prediction when user already has one")
        void existingPrediction_updates() {
            Team france = Team.builder().id(11L).name("France").fifaCode("FRA").flagCode("fr").build();
            TournamentWinnerPrediction existing = TournamentWinnerPrediction.builder()
                    .id(99L).user(alice).team(france).build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(teamRepository.findByFlagCodeIgnoreCase("br")).thenReturn(Optional.of(brazil));
            when(repository.findByUserId(1L)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.submitOrUpdate(1L, new TournamentWinnerPredictionDto("br"));

            ArgumentCaptor<TournamentWinnerPrediction> captor =
                    ArgumentCaptor.forClass(TournamentWinnerPrediction.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getTeam().getFlagCode()).isEqualTo("br");
        }

        @Test @DisplayName("normalises flag code to lowercase before lookup")
        void flagCode_normalisedToLowercase() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(teamRepository.findByFlagCodeIgnoreCase("BR")).thenReturn(Optional.of(brazil));
            when(repository.findByUserId(1L)).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.submitOrUpdate(1L, new TournamentWinnerPredictionDto("BR"));

            verify(teamRepository).findByFlagCodeIgnoreCase("BR");
        }

        @Test @DisplayName("throws IllegalArgumentException for blank flagCode")
        void blankFlagCode_throws() {
            assertThatThrownBy(() ->
                    service.submitOrUpdate(1L, new TournamentWinnerPredictionDto("  ")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("flagCode");
        }

        @Test @DisplayName("throws IllegalArgumentException for null dto")
        void nullDto_throws() {
            assertThatThrownBy(() -> service.submitOrUpdate(1L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test @DisplayName("throws when team not found")
        void unknownTeam_throws() {
            when(teamRepository.findByFlagCodeIgnoreCase("xx")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.submitOrUpdate(1L, new TournamentWinnerPredictionDto("xx")))
                    .isInstanceOf(TournamentWinnerPredictionService.TeamNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getForUser")
    class GetForUser {

        @Test @DisplayName("returns Optional with prediction when submitted")
        void hasPrediction_returnsOptional() {
            TournamentWinnerPrediction twp = TournamentWinnerPrediction.builder()
                    .user(alice).team(brazil).build();
            when(repository.findByUserId(1L)).thenReturn(Optional.of(twp));

            Optional<TournamentWinnerPrediction> result = service.getForUser(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getTeam().getFlagCode()).isEqualTo("br");
        }

        @Test @DisplayName("returns empty when not submitted")
        void noPrediction_returnsEmpty() {
            when(repository.findByUserId(99L)).thenReturn(Optional.empty());
            assertThat(service.getForUser(99L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("awardPoints")
    class AwardPoints {

        @Test @DisplayName("awards +10 points to all correct predictors")
        void awardsPointsToCorrectPredictors() {
            User bob = User.builder().id(2L).email("bob@example.com")
                    .firstName("Bob").lastName("J").status(UserStatus.ACTIVE).role(UserRole.PARTICIPANT).build();

            TournamentWinnerPrediction p1 = TournamentWinnerPrediction.builder().user(alice).team(brazil).build();
            TournamentWinnerPrediction p2 = TournamentWinnerPrediction.builder().user(bob).team(brazil).build();

            when(teamRepository.findByFlagCodeIgnoreCase("br")).thenReturn(Optional.of(brazil));
            when(repository.findByTeamId(10L)).thenReturn(List.of(p1, p2));
            when(scoringService.tournamentWinnerPoints("br", "br")).thenReturn(10);

            service.awardPoints("br");

            assertThat(p1.getPointsAwarded()).isEqualTo(10);
            assertThat(p2.getPointsAwarded()).isEqualTo(10);
            verify(repository, times(2)).save(any(TournamentWinnerPrediction.class));
        }

        @Test @DisplayName("does nothing when no one picked the winning team")
        void noCorrectPredictors_doesNothing() {
            when(teamRepository.findByFlagCodeIgnoreCase("br")).thenReturn(Optional.of(brazil));
            when(repository.findByTeamId(10L)).thenReturn(List.of());

            service.awardPoints("br");

            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test @DisplayName("returns all submitted predictions")
        void returnsAll() {
            Team france = Team.builder().id(11L).name("France").fifaCode("FRA").flagCode("fr").build();
            when(repository.findAllWithDetails()).thenReturn(List.of(
                    TournamentWinnerPrediction.builder().user(alice).team(brazil).build(),
                    TournamentWinnerPrediction.builder().user(alice).team(france).build()
            ));

            assertThat(service.getAll()).hasSize(2);
        }

        @Test @DisplayName("returns empty list when no submissions")
        void empty_returnsEmptyList() {
            when(repository.findAllWithDetails()).thenReturn(List.of());
            assertThat(service.getAll()).isEmpty();
        }
    }
}
