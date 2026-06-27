package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.dto.DailyExactPredictorDto;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DailyExactPredictorService {

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;

    /**
     * Returns users who got exact score predictions right on the most recently completed matchday,
     * ordered by number of correct predictions (descending).
     */
    public List<DailyExactPredictorDto> getLastMatchdayExactPredictors(Long communityId, LocalDateTime now) {
        List<Match> lastMatchdayMatches = findLastMatchday(now);
        if (lastMatchdayMatches.isEmpty()) {
            return List.of();
        }

        List<Long> matchIds = lastMatchdayMatches.stream().map(Match::getId).toList();

        List<Prediction> exactPredictions =
                predictionRepository.findExactPredictionsByMatchIdsAndCommunityId(matchIds, communityId);

        if (exactPredictions.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Prediction>> byUser = exactPredictions.stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getId(), LinkedHashMap::new, Collectors.toList()));

        List<DailyExactPredictorDto> result = new ArrayList<>();
        for (var entry : byUser.entrySet()) {
            List<Prediction> userPredictions = entry.getValue();
            var user = userPredictions.get(0).getUser();

            List<DailyExactPredictorDto.ExactMatchDto> exactMatches = userPredictions.stream()
                    .map(p -> {
                        Match m = p.getMatch();
                        Integer homeScore = m.getEffectiveHomeScore();
                        Integer awayScore = m.getEffectiveAwayScore();
                        if (homeScore == null || awayScore == null) return null;
                        return DailyExactPredictorDto.ExactMatchDto.builder()
                                .homeTeamName(m.getHomeTeam() != null ? m.getHomeTeam().getName() : m.getHomeTeamPlaceholder())
                                .awayTeamName(m.getAwayTeam() != null ? m.getAwayTeam().getName() : m.getAwayTeamPlaceholder())
                                .homeTeamFlagCode(m.getHomeTeam() != null ? m.getHomeTeam().getFlagCode() : null)
                                .awayTeamFlagCode(m.getAwayTeam() != null ? m.getAwayTeam().getFlagCode() : null)
                                .homeTeamFifaCode(m.getHomeTeam() != null ? m.getHomeTeam().getFifaCode() : null)
                                .awayTeamFifaCode(m.getAwayTeam() != null ? m.getAwayTeam().getFifaCode() : null)
                                .homeScore(homeScore)
                                .awayScore(awayScore)
                                .build();
                    })
                    .filter(Objects::nonNull)
                    .toList();

            result.add(DailyExactPredictorDto.builder()
                    .userId(user.getId())
                    .displayName(user.getFullName())
                    .avatarUrl(user.getAvatarUrl())
                    .exactCount(userPredictions.size())
                    .exactMatches(exactMatches)
                    .build());
        }

        result.sort(Comparator.comparingInt(DailyExactPredictorDto::getExactCount).reversed()
                .thenComparing(DailyExactPredictorDto::getDisplayName));

        return result;
    }

    /**
     * Returns the earliest kickoff date of the last completed round (for display).
     */
    public LocalDate getLastMatchdayDate(LocalDateTime now) {
        List<Match> matches = findLastMatchday(now);
        if (matches.isEmpty()) return null;
        return matches.stream()
                .map(m -> m.getKickoffTime().toLocalDate())
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    public List<DailyExactPredictorDto> getCumulativeHeroes(Long communityId, String stageFilter) {
        List<Prediction> exactPredictions =
                predictionRepository.findCumulativeExactPredictions(communityId, stageFilter);

        if (exactPredictions.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Prediction>> byUser = exactPredictions.stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getId(), LinkedHashMap::new, Collectors.toList()));

        List<DailyExactPredictorDto> result = new ArrayList<>();
        for (var entry : byUser.entrySet()) {
            List<Prediction> userPredictions = entry.getValue();
            var user = userPredictions.get(0).getUser();

            List<DailyExactPredictorDto.ExactMatchDto> exactMatches = userPredictions.stream()
                    .map(p -> {
                        Match m = p.getMatch();
                        Integer homeScore = m.getEffectiveHomeScore();
                        Integer awayScore = m.getEffectiveAwayScore();
                        if (homeScore == null || awayScore == null) return null;
                        return DailyExactPredictorDto.ExactMatchDto.builder()
                                .homeTeamName(m.getHomeTeam() != null ? m.getHomeTeam().getName() : m.getHomeTeamPlaceholder())
                                .awayTeamName(m.getAwayTeam() != null ? m.getAwayTeam().getName() : m.getAwayTeamPlaceholder())
                                .homeTeamFlagCode(m.getHomeTeam() != null ? m.getHomeTeam().getFlagCode() : null)
                                .awayTeamFlagCode(m.getAwayTeam() != null ? m.getAwayTeam().getFlagCode() : null)
                                .homeTeamFifaCode(m.getHomeTeam() != null ? m.getHomeTeam().getFifaCode() : null)
                                .awayTeamFifaCode(m.getAwayTeam() != null ? m.getAwayTeam().getFifaCode() : null)
                                .homeScore(homeScore)
                                .awayScore(awayScore)
                                .build();
                    })
                    .filter(Objects::nonNull)
                    .toList();

            result.add(DailyExactPredictorDto.builder()
                    .userId(user.getId())
                    .displayName(user.getFullName())
                    .avatarUrl(user.getAvatarUrl())
                    .exactCount(userPredictions.size())
                    .exactMatches(exactMatches)
                    .build());
        }

        result.sort(Comparator.comparingInt(DailyExactPredictorDto::getExactCount).reversed()
                .thenComparing(DailyExactPredictorDto::getDisplayName));

        if (result.size() > 20) {
            result = result.subList(0, 20);
        }

        return result;
    }

    /**
     * Returns all completed matches belonging to the most recently completed round.
     * Groups by roundLabel so that users who predicted correctly across multiple days
     * within the same round are all included together.
     */
    private List<Match> findLastMatchday(LocalDateTime now) {
        List<Match> allCompleted = matchRepository.findByStatusWithTeams(
            com.worldcup.prediction.domain.enums.MatchStatus.COMPLETED);

        if (allCompleted.isEmpty()) return List.of();

        List<Match> completedBeforeNow = allCompleted.stream()
                .filter(m -> m.getKickoffTime().isBefore(now))
                .sorted(Comparator.comparing(Match::getKickoffTime).reversed())
                .toList();

        if (completedBeforeNow.isEmpty()) return List.of();

        String latestRoundLabel = completedBeforeNow.get(0).getRoundLabel();
        if (latestRoundLabel != null) {
            return completedBeforeNow.stream()
                    .filter(m -> latestRoundLabel.equals(m.getRoundLabel()))
                    .toList();
        }

        // Fallback to date-based grouping if round label is not set
        LocalDate latestDate = completedBeforeNow.get(0).getKickoffTime().toLocalDate();
        return completedBeforeNow.stream()
                .filter(m -> m.getKickoffTime().toLocalDate().equals(latestDate))
                .toList();
    }
}
