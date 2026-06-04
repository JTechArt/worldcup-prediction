package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.TournamentWinnerPrediction;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.TournamentWinnerPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaderboardService {

    private final CommunityMembershipRepository membershipRepository;
    private final TournamentWinnerPredictionRepository twpRepository;

    public List<LeaderboardEntryDto> getFullLeaderboard(Long communityId) {
        List<CommunityMembership> members = membershipRepository
                .findByCommunityIdAndStatusWithUser(communityId, MembershipStatus.ACTIVE);

        List<LeaderboardEntryDto> unsorted = members.stream()
                .map(m -> buildEntry(m, communityId))
                .sorted(leaderboardComparator())
                .toList();

        return assignRanks(unsorted);
    }

    public List<LeaderboardEntryDto> getTopN(int n, Long communityId) {
        List<LeaderboardEntryDto> full = getFullLeaderboard(communityId);
        return full.subList(0, Math.min(n, full.size()));
    }

    public Optional<LeaderboardEntryDto> getEntryForUser(Long userId, Long communityId) {
        return getFullLeaderboard(communityId).stream()
                .filter(e -> e.getUserId().equals(userId))
                .findFirst();
    }

    private LeaderboardEntryDto buildEntry(CommunityMembership membership, Long communityId) {
        var user = membership.getUser();
        Optional<TournamentWinnerPrediction> twp = twpRepository
                .findByUserIdAndCommunityId(user.getId(), communityId);
        boolean tournamentWinnerCorrect = twp.map(TournamentWinnerPrediction::isScored).orElse(false);
        String predictedWinnerFlagCode = twp.map(t -> t.getTeam().getFlagCode()).orElse(null);

        return new LeaderboardEntryDto(
                0, user.getId(), user.getFullName(), user.getAvatarUrl(),
                predictedWinnerFlagCode, membership.getTotalPoints(),
                membership.getExactScoreCount(), membership.getCorrectWinnerCount(),
                membership.getCorrectDrawCount(), tournamentWinnerCorrect, 0);
    }

    private Comparator<LeaderboardEntryDto> leaderboardComparator() {
        return Comparator
                .<LeaderboardEntryDto>comparingInt(e -> -e.getTotalPoints())
                .thenComparingInt(e -> -e.getExactCount())
                .thenComparingInt(e -> -e.getCorrectWinnerCount())
                .thenComparingInt(e -> e.isTournamentWinnerCorrect() ? 0 : 1);
    }

    private List<LeaderboardEntryDto> assignRanks(List<LeaderboardEntryDto> sorted) {
        List<LeaderboardEntryDto> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            LeaderboardEntryDto e = sorted.get(i);
            ranked.add(new LeaderboardEntryDto(
                    i + 1, e.getUserId(), e.getDisplayName(), e.getAvatarUrl(),
                    e.getPredictedWinnerFlagCode(), e.getTotalPoints(), e.getExactCount(),
                    e.getCorrectWinnerCount(), e.getDrawCount(), e.isTournamentWinnerCorrect(),
                    e.getRankChange()));
        }
        return ranked;
    }
}
