package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.FixtureViewDto;
import com.worldcup.prediction.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class FixtureController {

    private final MatchService matchService;

    @GetMapping("/fixtures")
    public String fixtures(
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            Model model) {

        List<FixtureViewDto> fixtures = loadFixtures(filter);
        model.addAttribute("grouped", groupByPhaseAndDate(fixtures));
        model.addAttribute("activeFilter", filter);
        model.addAttribute("pageTitle", "Fixtures");
        return "fixtures";
    }

    @GetMapping("/fixtures/filter")
    public String fixturesFilter(
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            Model model) {

        List<FixtureViewDto> fixtures = loadFixtures(filter);
        model.addAttribute("grouped", groupByPhaseAndDate(fixtures));
        model.addAttribute("activeFilter", filter);
        return "fragments/fixture-rows :: fixture-rows";
    }

    private List<FixtureViewDto> loadFixtures(String filter) {
        return switch (filter) {
            case "group"    -> matchService.getGroupStageFixtures();
            case "knockout" -> matchService.getKnockoutFixtures();
            case "today"    -> matchService.getTodayFixtures();
            default         -> matchService.getAllFixtures();
        };
    }

    private Map<String, Map<LocalDate, List<FixtureViewDto>>> groupByPhaseAndDate(List<FixtureViewDto> fixtures) {
        Map<String, List<FixtureViewDto>> byPhase = fixtures.stream()
                .collect(Collectors.groupingBy(FixtureViewDto::getPhase, LinkedHashMap::new, Collectors.toList()));

        Map<String, Map<LocalDate, List<FixtureViewDto>>> result = new LinkedHashMap<>();
        byPhase.forEach((phase, phaseFixtures) -> {
            Map<LocalDate, List<FixtureViewDto>> byDate = phaseFixtures.stream()
                    .collect(Collectors.groupingBy(
                            f -> f.getKickoff().toLocalDate(),
                            LinkedHashMap::new,
                            Collectors.toList()));
            result.put(phase, byDate);
        });
        return result;
    }
}
