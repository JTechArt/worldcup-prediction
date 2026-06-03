package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.GroupStandingDto;
import com.worldcup.prediction.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping("/groups")
    public String groups(Model model) {
        Map<String, List<GroupStandingDto>> groups = groupService.getAllGroupStandings();
        List<String> qualifiedThirdGroups = groupService.getQualifiedThirdPlaceGroups();

        model.addAttribute("groups", groups);
        model.addAttribute("qualifiedThirdGroups", qualifiedThirdGroups);
        model.addAttribute("pageTitle", "Groups");
        return "groups";
    }
}
