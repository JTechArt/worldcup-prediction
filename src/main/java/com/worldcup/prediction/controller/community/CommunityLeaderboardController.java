package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.LeaderboardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.worldcup.prediction.domain.enums.MatchStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/c/{slug}")
@RequiredArgsConstructor
public class CommunityLeaderboardController {

    private final LeaderboardService leaderboardService;
    private final PredictionRepository predictionRepository;
    private final MatchRepository matchRepository;

    @GetMapping("/leaderboard")
    public String leaderboard(@PathVariable String slug,
                              HttpServletRequest request,
                              Authentication authentication,
                              Model model) {
        Community community = (Community) request.getAttribute("community");
        Long communityId = community.getId();

        List<LeaderboardEntryDto> entries = leaderboardService.getFullLeaderboard(communityId);

        // Phase breakdown: userId → (stage → sumPoints)
        Map<Long, Map<MatchStage, Integer>> phasePoints = new HashMap<>();
        predictionRepository.sumPointsByUserAndStageAndCommunityId(communityId).forEach(row -> {
            Long userId   = (Long)       row[0];
            MatchStage st = (MatchStage) row[1];
            int pts       = ((Number)    row[2]).intValue();
            phasePoints.computeIfAbsent(userId, k -> new EnumMap<>(MatchStage.class)).put(st, pts);
        });

        // Current user entry for KPI strip
        LeaderboardEntryDto currentUserEntry = null;
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomOAuth2User customUser) {
            currentUserEntry = leaderboardService.getEntryForUser(customUser.getUserId(), communityId).orElse(null);
        }

        // Group stage: fetch and sort by kickoff, then group by round label
        List<Match> groupMatchesList = matchRepository.findByStageWithTeams(MatchStage.GROUP);
        groupMatchesList.sort(java.util.Comparator.comparing(Match::getKickoffTime));

        Map<String, List<Match>> groupRounds = new LinkedHashMap<>();
        for (Match m : groupMatchesList) {
            String label = m.getRoundLabel() != null ? m.getRoundLabel() : "Group Stage";
            groupRounds.computeIfAbsent(label, k -> new ArrayList<>()).add(m);
        }

        // Determine current round: the last round that has any non-SCHEDULED match;
        // defaults to the first round if nothing has started yet.
        String currentRoundLabel = groupRounds.isEmpty() ? null : groupRounds.keySet().iterator().next();
        for (Map.Entry<String, List<Match>> e : groupRounds.entrySet()) {
            boolean started = e.getValue().stream().anyMatch(m -> m.getStatus() != MatchStatus.SCHEDULED);
            if (started) currentRoundLabel = e.getKey();
        }

        // Convert current round label to a phase-nav ID (e.g. "ph-gs-r2")
        String currentPhaseId = "ph-gs-r1";
        int roundIdx = 1;
        for (String label : groupRounds.keySet()) {
            if (label.equals(currentRoundLabel)) {
                currentPhaseId = "ph-gs-r" + roundIdx;
                break;
            }
            roundIdx++;
        }

        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("entries", entries);
        model.addAttribute("stages", MatchStage.values());
        model.addAttribute("totalParticipants", entries.size());
        model.addAttribute("phasePoints", phasePoints);
        model.addAttribute("currentUserEntry", currentUserEntry);
        model.addAttribute("groupRounds", groupRounds);
        model.addAttribute("currentRoundLabel", currentRoundLabel);
        model.addAttribute("currentPhaseId", currentPhaseId);
        model.addAttribute("pageTitle", community.getName() + " · Leaderboard");

        return "community/leaderboard";
    }
}
