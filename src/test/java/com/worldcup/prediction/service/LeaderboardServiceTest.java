package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.TournamentWinnerPrediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.TournamentWinnerPredictionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock CommunityMembershipRepository membershipRepository;
    @Mock TournamentWinnerPredictionRepository twpRepository;

    @InjectMocks LeaderboardService leaderboardService;

    private static final Long COMMUNITY_ID = 100L;

    private User makeUser(Long id, String firstName, String lastName) {
        User u = User.builder()
                .email(firstName.toLowerCase() + "@example.com")
                .firstName(firstName).lastName(lastName)
                .status(UserStatus.ACTIVE).role(UserRole.USER)
                .build();
        u.setId(id);
        return u;
    }

    private CommunityMembership makeMembership(User user, int totalPoints, int exactCount, int winnerCount, int drawCount) {
        return CommunityMembership.builder()
                .user(user)
                .role(CommunityRole.MEMBER)
                .status(MembershipStatus.ACTIVE)
                .totalPoints(totalPoints)
                .exactScoreCount(exactCount)
                .correctWinnerCount(winnerCount)
                .correctDrawCount(drawCount)
                .build();
    }

    @Test
    void getFullLeaderboard_sortsByTotalPointsDescending() {
        User alice = makeUser(1L, "Alice", "Smith");
        User bob = makeUser(2L, "Bob", "Jones");

        CommunityMembership mAlice = makeMembership(alice, 4, 1, 1, 0);
        CommunityMembership mBob = makeMembership(bob, 3, 1, 0, 0);

        when(membershipRepository.findByCommunityIdAndStatusWithUser(COMMUNITY_ID, MembershipStatus.ACTIVE))
                .thenReturn(List.of(mAlice, mBob));
        when(twpRepository.findByUserIdAndCommunityId(any(), eq(COMMUNITY_ID))).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard(COMMUNITY_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
        assertThat(result.get(0).getTotalPoints()).isEqualTo(4);
        assertThat(result.get(1).getUserId()).isEqualTo(2L);
        assertThat(result.get(0).getRank()).isEqualTo(1);
        assertThat(result.get(1).getRank()).isEqualTo(2);
    }

    @Test
    void getTopN_returnsFirstNEntries() {
        User alice = makeUser(1L, "A", "One");
        User bob = makeUser(2L, "B", "Two");
        User charlie = makeUser(3L, "C", "Three");

        when(membershipRepository.findByCommunityIdAndStatusWithUser(COMMUNITY_ID, MembershipStatus.ACTIVE))
                .thenReturn(List.of(
                        makeMembership(alice, 10, 2, 1, 0),
                        makeMembership(bob, 8, 1, 1, 0),
                        makeMembership(charlie, 5, 0, 1, 0)
                ));
        when(twpRepository.findByUserIdAndCommunityId(any(), eq(COMMUNITY_ID))).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> top2 = leaderboardService.getTopN(2, COMMUNITY_ID);

        assertThat(top2).hasSize(2);
        assertThat(top2.get(0).getRank()).isEqualTo(1);
        assertThat(top2.get(1).getRank()).isEqualTo(2);
    }

    @Test
    void getTopN_whenFewerThanNUsers_returnsAll() {
        User alice = makeUser(1L, "Alice", "Smith");
        when(membershipRepository.findByCommunityIdAndStatusWithUser(COMMUNITY_ID, MembershipStatus.ACTIVE))
                .thenReturn(List.of(makeMembership(alice, 3, 1, 0, 0)));
        when(twpRepository.findByUserIdAndCommunityId(any(), eq(COMMUNITY_ID))).thenReturn(Optional.empty());

        assertThat(leaderboardService.getTopN(10, COMMUNITY_ID)).hasSize(1);
    }

    @Test
    void getEntryForUser_returnsCorrectRankAndData() {
        User alice = makeUser(1L, "Alice", "Smith");
        User bob = makeUser(2L, "Bob", "Jones");

        when(membershipRepository.findByCommunityIdAndStatusWithUser(COMMUNITY_ID, MembershipStatus.ACTIVE))
                .thenReturn(List.of(
                        makeMembership(alice, 5, 1, 1, 0),
                        makeMembership(bob, 2, 0, 1, 0)
                ));
        when(twpRepository.findByUserIdAndCommunityId(any(), eq(COMMUNITY_ID))).thenReturn(Optional.empty());

        Optional<LeaderboardEntryDto> entry = leaderboardService.getEntryForUser(2L, COMMUNITY_ID);

        assertThat(entry).isPresent();
        assertThat(entry.get().getRank()).isEqualTo(2);
        assertThat(entry.get().getTotalPoints()).isEqualTo(2);
    }

    @Test
    void getEntryForUser_returnsEmptyForUnknownUser() {
        when(membershipRepository.findByCommunityIdAndStatusWithUser(COMMUNITY_ID, MembershipStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(leaderboardService.getEntryForUser(999L, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void getFullLeaderboard_tiebreaker_exactCountWins() {
        User alice = makeUser(1L, "Alice", "Smith");
        User bob = makeUser(2L, "Bob", "Jones");

        // Same points, Alice has more exact
        when(membershipRepository.findByCommunityIdAndStatusWithUser(COMMUNITY_ID, MembershipStatus.ACTIVE))
                .thenReturn(List.of(
                        makeMembership(alice, 6, 2, 0, 0),
                        makeMembership(bob, 6, 1, 1, 1)
                ));
        when(twpRepository.findByUserIdAndCommunityId(any(), eq(COMMUNITY_ID))).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard(COMMUNITY_ID);

        assertThat(result.get(0).getUserId()).isEqualTo(1L);
        assertThat(result.get(0).getExactCount()).isEqualTo(2);
    }

    @Test
    void getFullLeaderboard_tournamentWinnerCorrect_reflected() {
        User alice = makeUser(1L, "Alice", "Smith");
        Team brazil = Team.builder().fifaCode("BRA").flagCode("br").name("Brazil").build();
        TournamentWinnerPrediction twp = TournamentWinnerPrediction.builder()
                .user(alice).team(brazil).scored(true).build();

        when(membershipRepository.findByCommunityIdAndStatusWithUser(COMMUNITY_ID, MembershipStatus.ACTIVE))
                .thenReturn(List.of(makeMembership(alice, 3, 1, 0, 0)));
        when(twpRepository.findByUserIdAndCommunityId(1L, COMMUNITY_ID)).thenReturn(Optional.of(twp));

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard(COMMUNITY_ID);

        assertThat(result.get(0).isTournamentWinnerCorrect()).isTrue();
        assertThat(result.get(0).getPredictedWinnerFlagCode()).isEqualTo("br");
    }

    @Test
    void getFullLeaderboard_noMembers_returnsEmpty() {
        when(membershipRepository.findByCommunityIdAndStatusWithUser(COMMUNITY_ID, MembershipStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(leaderboardService.getFullLeaderboard(COMMUNITY_ID)).isEmpty();
    }
}
