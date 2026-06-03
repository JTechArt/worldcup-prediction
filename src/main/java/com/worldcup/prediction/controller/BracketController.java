package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/bracket")
@RequiredArgsConstructor
public class BracketController {

    private static final List<MatchStage> BRACKET_STAGES = List.of(
            MatchStage.ROUND_OF_32,
            MatchStage.ROUND_OF_16,
            MatchStage.QUARTER_FINAL,
            MatchStage.SEMI_FINAL,
            MatchStage.FINAL,
            MatchStage.THIRD_PLACE
    );

    private final MatchRepository matchRepository;

    @GetMapping
    public String bracket(Model model) {
        Map<String, List<Match>> bracketByStage = new LinkedHashMap<>();
        for (MatchStage stage : BRACKET_STAGES) {
            List<Match> stageMatches = matchRepository.findByStageOrderByKickoffTimeAsc(stage);
            if (!stageMatches.isEmpty()) {
                bracketByStage.put(stageName(stage), stageMatches);
            }
        }
        model.addAttribute("bracketByStage", bracketByStage);
        model.addAttribute("pageTitle", "Knockout Bracket");
        return "bracket";
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
