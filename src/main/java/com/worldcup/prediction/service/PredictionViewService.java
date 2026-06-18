package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.dto.*;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * View-layer service for the predictions page.
 * Handles round summaries, match prediction display, and round submission.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PredictionViewService {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm");

    @Value("${app.timezone}")
    private String timezoneId;

    private ZoneId appZone;
    private DateTimeFormatter isoFmt;

    @PostConstruct
    private void initFormatters() {
        appZone = ZoneId.of(timezoneId);
        isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(appZone);
    }

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final RoundWindowService roundWindowService;
    private final RoundSubmissionService roundSubmissionService;
    private final TournamentSettingsService tournamentSettingsService;
    private final PredictionWindowService predictionWindowService;
    private final UserRoundOverrideService userRoundOverrideService;

    public List<RoundSummaryDto> getRoundSummaries(Long userId, Long communityId) {
        List<String> roundLabels = matchRepository.findDistinctRoundLabels();
        List<RoundSummaryDto> summaries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (String label : roundLabels) {
            List<Match> matches = matchRepository.findByRoundLabelWithTeams(label);
            if (matches.isEmpty()) continue;

            RoundSummaryDto dto = new RoundSummaryDto();
            dto.setRoundLabel(label);
            dto.setDisplayLabel(toDisplayLabel(label));
            dto.setTotalMatches(matches.size());

            List<Long> matchIds = matches.stream().map(Match::getId).toList();
            boolean allComplete = matches.stream().allMatch(m -> m.getStatus() == MatchStatus.COMPLETED);

            if (allComplete) {
                dto.setStatus("PAST");
                int pts = (int) predictionRepository.findByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId).stream()
                        .mapToInt(Prediction::getPointsAwarded).sum();
                dto.setPointsEarned(pts);
            } else if (roundWindowService.isRoundOpen(label, now)) {
                dto.setStatus("OPEN");
                long predicted = predictionRepository.countByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId);
                dto.setPredictedCount((int) predicted);
            } else if (userRoundOverrideService.hasActiveOverride(userId, communityId, label)
                       && !userRoundOverrideService.getEligibleMatches(label, now).isEmpty()) {
                dto.setStatus("OPEN");
                long predicted = predictionRepository.countByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId);
                dto.setPredictedCount((int) predicted);
            } else {
                // Window closed but results not in yet — predictions visible, points pending
                dto.setStatus("CLOSED");
                long predicted = predictionRepository.countByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId);
                dto.setPredictedCount((int) predicted);
            }

            summaries.add(dto);
        }
        return summaries;
    }

    public List<RoundSummaryDto> getWindowSummaries(Long userId, Long communityId) {
        List<PredictionWindow> windows = predictionWindowService.findAllGlobal();
        List<RoundSummaryDto> summaries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (PredictionWindow pw : windows) {
            List<Match> matches = new ArrayList<>(pw.getMatches());
            if (matches.isEmpty()) continue;

            List<Long> matchIds = matches.stream().map(Match::getId).toList();
            boolean allComplete = matches.stream().allMatch(m -> m.getStatus() == MatchStatus.COMPLETED);

            RoundSummaryDto dto = new RoundSummaryDto();
            dto.setRoundLabel(pw.getLabel());
            dto.setDisplayLabel(pw.getLabel());
            dto.setTotalMatches(matches.size());

            if (allComplete) {
                dto.setStatus("PAST");
                int pts = (int) predictionRepository
                        .findByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId)
                        .stream().mapToInt(Prediction::getPointsAwarded).sum();
                dto.setPointsEarned(pts);
            } else if (predictionWindowService.isWindowOpen(matches.get(0), now, communityId)) {
                dto.setStatus("OPEN");
                long predicted = predictionRepository.countByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId);
                dto.setPredictedCount((int) predicted);
            } else {
                dto.setStatus("CLOSED");
                long predicted = predictionRepository.countByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId);
                dto.setPredictedCount((int) predicted);
            }
            summaries.add(dto);
        }
        return summaries;
    }

    public List<MatchPredictionDto> getMatchesForRound(Long userId, String roundLabel, Long communityId) {
        List<Match> matches = matchRepository.findByRoundLabelWithTeams(roundLabel);
        List<Long> matchIds = matches.stream().map(Match::getId).toList();

        Map<Long, Prediction> predMap = predictionRepository
                .findByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId).stream()
                .collect(Collectors.toMap(p -> p.getMatch().getId(), p -> p));

        LocalDateTime now = LocalDateTime.now();
        boolean roundOpen = roundWindowService.isRoundOpen(roundLabel, now);
        boolean hasOverride = !roundOpen
                && userRoundOverrideService.hasActiveOverride(userId, communityId, roundLabel);
        Set<Long> eligibleIds = hasOverride
                ? userRoundOverrideService.getEligibleMatches(roundLabel, now).stream()
                        .map(Match::getId).collect(Collectors.toSet())
                : Set.of();

        return matches.stream().map(m -> {
            MatchPredictionDto dto = toMatchPredictionDto(m, predMap.get(m.getId()), now);
            if (hasOverride && eligibleIds.contains(m.getId())) {
                dto.setLocked(false);
            } else if (hasOverride && !eligibleIds.contains(m.getId())) {
                dto.setLocked(true);
            }
            return dto;
        }).toList();
    }

    public Map<String, List<MatchPredictionDto>> groupMatchesByDate(List<MatchPredictionDto> matches) {
        DateTimeFormatter key = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);
        Map<String, List<MatchPredictionDto>> result = new LinkedHashMap<>();
        for (MatchPredictionDto m : matches) {
            String dk = m.getKickoffIso() != null
                    ? OffsetDateTime.parse(m.getKickoffIso()).atZoneSameInstant(appZone).toLocalDateTime().format(key) : "TBD";
            result.computeIfAbsent(dk, k -> new ArrayList<>()).add(m);
        }
        return result;
    }

    @Transactional
    public int submitPredictionsForRound(Long userId, PredictionSubmitDto dto, Long communityId) {
        WindowMode mode = tournamentSettingsService.getEffectiveMode(communityId);
        if (mode == WindowMode.DAILY) {
            return submitPredictionsForWindow(userId, dto, communityId);
        }

        List<Match> roundMatches = matchRepository.findByRoundLabelWithTeams(dto.getRoundLabel());
        LocalDateTime now = LocalDateTime.now();
        boolean roundOpen = roundWindowService.isRoundOpen(dto.getRoundLabel(), now);
        boolean hasOverride = userRoundOverrideService.hasActiveOverride(userId, communityId, dto.getRoundLabel());

        if (!roundOpen && !hasOverride) {
            throw new IllegalStateException("The prediction window for " + dto.getRoundLabel() + " is not open.");
        }

        List<Match> eligibleMatches;
        if (hasOverride && !roundOpen) {
            eligibleMatches = userRoundOverrideService.getEligibleMatches(dto.getRoundLabel(), now);
            if (eligibleMatches.isEmpty()) {
                throw new IllegalStateException("No eligible matches remaining in " + dto.getRoundLabel() + ".");
            }
        } else {
            for (Match m : roundMatches) {
                if (now.isAfter(m.getKickoffTime().minusHours(1))) {
                    String ht = m.getHomeTeam() != null ? m.getHomeTeam().getName() : "?";
                    String at = m.getAwayTeam() != null ? m.getAwayTeam().getName() : "?";
                    throw new IllegalStateException(ht + " vs " + at + " is already locked.");
                }
            }
            eligibleMatches = roundMatches;
        }

        Set<Long> eligibleIds = eligibleMatches.stream().map(Match::getId).collect(Collectors.toSet());
        Set<Long> submittedIds = dto.getPredictions().stream()
                .map(PredictionSubmitDto.SinglePrediction::getMatchId).collect(Collectors.toSet());

        if (!submittedIds.equals(eligibleIds)) {
            throw new IllegalStateException(
                    "You must predict all " + eligibleIds.size() + " eligible matches (all-or-nothing).");
        }

        Map<Long, Match> matchMap = roundMatches.stream().collect(Collectors.toMap(Match::getId, m -> m));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        for (PredictionSubmitDto.SinglePrediction sp : dto.getPredictions()) {
            Match match = matchMap.get(sp.getMatchId());
            if (match == null) throw new IllegalStateException("Match not found: " + sp.getMatchId());
            if (!eligibleIds.contains(sp.getMatchId())) {
                throw new IllegalStateException("Match " + sp.getMatchId() + " is not eligible for prediction.");
            }

            Optional<Prediction> existing = predictionRepository.findByUserIdAndMatchIdAndCommunityId(userId, sp.getMatchId(), communityId);
            Prediction prediction;
            if (existing.isPresent()) {
                prediction = existing.get();
                prediction.setPredictedHome(sp.getHomeScore());
                prediction.setPredictedAway(sp.getAwayScore());
            } else {
                prediction = new Prediction();
                prediction.setUser(user);
                prediction.setMatch(match);
                prediction.setCommunity(communityRepository.findById(communityId).orElseThrow());
                prediction.setPredictedHome(sp.getHomeScore());
                prediction.setPredictedAway(sp.getAwayScore());
            }
            predictionRepository.save(prediction);
        }

        if (hasOverride && !roundOpen) {
            userRoundOverrideService.markUsed(userId, communityId, dto.getRoundLabel());
        }

        roundSubmissionService.upsert(userId, communityId, dto.getRoundLabel());
        return eligibleMatches.size();
    }

    @Transactional
    private int submitPredictionsForWindow(Long userId, PredictionSubmitDto dto, Long communityId) {
        if (dto.getWindowId() == null) {
            throw new IllegalStateException("windowId required in DAILY mode");
        }
        PredictionWindow pw = predictionWindowService.findById(dto.getWindowId());
        LocalDateTime now = LocalDateTime.now();

        List<Match> windowMatches = new ArrayList<>(pw.getMatches());
        if (windowMatches.isEmpty() || !predictionWindowService.isWindowOpen(windowMatches.get(0), now, communityId)) {
            throw new IllegalStateException("The prediction window '" + pw.getLabel() + "' is not open.");
        }

        Set<Long> openIds = windowMatches.stream().map(Match::getId).collect(Collectors.toSet());
        Set<Long> submittedIds = dto.getPredictions().stream()
                .map(PredictionSubmitDto.SinglePrediction::getMatchId).collect(Collectors.toSet());
        if (!submittedIds.equals(openIds)) {
            throw new IllegalStateException(
                    "You must predict all " + openIds.size() + " matches in this window (all-or-nothing).");
        }

        Map<Long, Match> matchMap = windowMatches.stream().collect(Collectors.toMap(Match::getId, m -> m));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        for (PredictionSubmitDto.SinglePrediction sp : dto.getPredictions()) {
            Match match = matchMap.get(sp.getMatchId());
            if (match == null) throw new IllegalStateException("Match not found: " + sp.getMatchId());
            Optional<Prediction> existing = predictionRepository
                    .findByUserIdAndMatchIdAndCommunityId(userId, sp.getMatchId(), communityId);
            Prediction prediction;
            if (existing.isPresent()) {
                prediction = existing.get();
                prediction.setPredictedHome(sp.getHomeScore());
                prediction.setPredictedAway(sp.getAwayScore());
            } else {
                prediction = new Prediction();
                prediction.setUser(user);
                prediction.setMatch(match);
                prediction.setCommunity(communityRepository.findById(communityId).orElseThrow());
                prediction.setPredictedHome(sp.getHomeScore());
                prediction.setPredictedAway(sp.getAwayScore());
            }
            predictionRepository.save(prediction);
        }

        roundSubmissionService.upsertForWindow(userId, communityId, pw.getId(), pw.getLabel());
        return windowMatches.size();
    }

    public List<PastRoundDto> getPastRoundsForUser(Long userId, Long communityId) {
        List<String> roundLabels = matchRepository.findDistinctRoundLabels();
        List<PastRoundDto> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (String label : roundLabels) {
            List<Match> matches = matchRepository.findByRoundLabelWithTeams(label);
            boolean windowClosed = !roundWindowService.isRoundOpen(label, now);
            boolean allComplete = matches.stream().allMatch(m -> m.getStatus() == MatchStatus.COMPLETED);
            // Show round once window is closed (predictions are now public) or all results are in
            if (!windowClosed && !allComplete) continue;

            List<Long> matchIds = matches.stream().map(Match::getId).toList();
            Map<Long, Prediction> predMap = predictionRepository
                    .findByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId).stream()
                    .collect(Collectors.toMap(p -> p.getMatch().getId(), p -> p));

            List<PastMatchResultDto> matchResults = matches.stream().map(m -> {
                PastMatchResultDto mr = new PastMatchResultDto();
                mr.setMatchId(m.getId());
                mr.setHomeTeamName(m.getHomeTeam() != null ? m.getHomeTeam().getName() : "TBD");
                mr.setHomeTeamCode(m.getHomeTeam() != null ? m.getHomeTeam().getFlagCode() : "xx");
                mr.setAwayTeamName(m.getAwayTeam() != null ? m.getAwayTeam().getName() : "TBD");
                mr.setAwayTeamCode(m.getAwayTeam() != null ? m.getAwayTeam().getFlagCode() : "xx");
                mr.setKickoffDisplay(m.getKickoffTime().format(DISPLAY));
                boolean matchCompleted = m.getStatus() == MatchStatus.COMPLETED && m.getHomeScore() != null;
                mr.setResultsAvailable(matchCompleted);
                mr.setActualHome(matchCompleted ? m.getHomeScore() : 0);
                mr.setActualAway(matchCompleted ? m.getAwayScore() : 0);

                Prediction pred = predMap.get(m.getId());
                if (pred != null) {
                    mr.setPredictedHome(pred.getPredictedHome());
                    mr.setPredictedAway(pred.getPredictedAway());
                    mr.setPointsEarned(matchCompleted ? pred.getPointsAwarded() : 0);
                    mr.setOutcome(matchCompleted
                            ? outcomeLabel(m.getHomeScore(), m.getAwayScore(), pred.getPredictedHome(), pred.getPredictedAway())
                            : "PENDING");
                } else {
                    mr.setOutcome("NOT_PREDICTED");
                }
                return mr;
            }).toList();

            PastRoundDto round = new PastRoundDto();
            round.setRoundLabel(label);
            round.setDisplayLabel(toDisplayLabel(label));
            round.setTotalPoints(matchResults.stream().mapToInt(PastMatchResultDto::getPointsEarned).sum());
            round.setMatches(matchResults);
            result.add(round);
        }

        Collections.reverse(result);
        return result;
    }

    private MatchPredictionDto toMatchPredictionDto(Match m, Prediction pred, LocalDateTime now) {
        MatchPredictionDto dto = new MatchPredictionDto();
        dto.setMatchId(m.getId());
        dto.setRoundLabel(m.getRoundLabel());
        dto.setStage(m.getStage().name());
        dto.setGroup(m.getGroup() != null ? m.getGroup().getName() : null);
        dto.setKickoffIso(m.getKickoffTime().atZone(appZone).format(isoFmt));
        dto.setLockTimeIso(m.getKickoffTime().minusHours(1).atZone(appZone).format(isoFmt));
        dto.setLocked(now.isAfter(m.getKickoffTime().minusHours(1)));
        dto.setHomeTeamName(m.getHomeTeam() != null ? m.getHomeTeam().getName() : "TBD");
        dto.setHomeTeamCode(m.getHomeTeam() != null ? m.getHomeTeam().getFlagCode() : "xx");
        dto.setAwayTeamName(m.getAwayTeam() != null ? m.getAwayTeam().getName() : "TBD");
        dto.setAwayTeamCode(m.getAwayTeam() != null ? m.getAwayTeam().getFlagCode() : "xx");
        dto.setVenue(m.getVenue());
        if (pred != null) {
            dto.setPredictedHome(pred.getPredictedHome());
            dto.setPredictedAway(pred.getPredictedAway());
            dto.setPredictionSaved(true);
        }
        return dto;
    }

    private String outcomeLabel(Integer ah, Integer aa, int ph, int pa) {
        if (ah == null || aa == null) return "NOT_PREDICTED";
        if (ph == ah && pa == aa) return "EXACT";
        if (ah.equals(aa) && ph == pa) return "DRAW";
        boolean ahw = ah > aa; boolean phw = ph > pa;
        if (ahw == phw && !ah.equals(aa)) return "WINNER";
        return "WRONG";
    }

    private String toDisplayLabel(String roundLabel) {
        return switch (roundLabel) {
            case "Round of 32"   -> "R32";
            case "Round of 16"   -> "R16";
            case "Quarter-Final" -> "QF";
            case "Semi-Final"    -> "SF";
            case "Third Place"   -> "3rd Place";
            case "Final"         -> "Final";
            default -> roundLabel; // "Group Stage MD1" etc.
        };
    }
}
