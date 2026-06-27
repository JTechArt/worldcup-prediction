package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.dto.GroupStandingDto;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class GroupController {

    private static final LocalDate PLAYOFF_START = LocalDate.of(2026, 6, 28);

    private static final List<MatchStage> BRACKET_STAGES = List.of(
            MatchStage.ROUND_OF_32,
            MatchStage.ROUND_OF_16,
            MatchStage.QUARTER_FINAL,
            MatchStage.SEMI_FINAL,
            MatchStage.THIRD_PLACE,
            MatchStage.FINAL
    );

    private final GroupService groupService;
    private final MatchRepository matchRepository;

    @GetMapping("/groups")
    public String groups(Model model) {
        Map<String, List<GroupStandingDto>> groups = groupService.getAllGroupStandings();
        List<String> qualifiedThirdGroups = groupService.getQualifiedThirdPlaceGroups();
        Map<String, List<Match>> matchesByGroup = groupService.getMatchesByGroup();

        Map<String, List<Match>> bracketByStage = new LinkedHashMap<>();
        for (MatchStage stage : BRACKET_STAGES) {
            List<Match> stageMatches = matchRepository.findByStageWithTeams(stage);
            if (!stageMatches.isEmpty()) {
                bracketByStage.put(stageName(stage), stageMatches);
            }
        }

        String defaultTab = LocalDate.now().isBefore(PLAYOFF_START) ? "groups" : "playoff";

        model.addAttribute("groups", groups);
        model.addAttribute("qualifiedThirdGroups", qualifiedThirdGroups);
        model.addAttribute("matchesByGroup", matchesByGroup);
        model.addAttribute("bracketByStage", bracketByStage);
        model.addAttribute("defaultTab", defaultTab);
        model.addAttribute("pageTitle", "Standings");
        return "groups";
    }

    private String stageName(MatchStage stage) {
        return switch (stage) {
            case ROUND_OF_32   -> "Round of 32";
            case ROUND_OF_16   -> "Round of 16";
            case QUARTER_FINAL -> "Quarter-Finals";
            case SEMI_FINAL    -> "Semi-Finals";
            case FINAL         -> "Final";
            case THIRD_PLACE   -> "3rd Place";
            default            -> stage.name();
        };
    }
}
