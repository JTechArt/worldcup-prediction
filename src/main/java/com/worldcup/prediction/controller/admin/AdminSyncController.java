package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.integration.football.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/sync")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSyncController {

    private final BootstrapSyncService bootstrapSyncService;
    private final FootballApiClient footballApiClient;
    private final TeamSyncService teamSyncService;
    private final MatchSyncService matchSyncService;
    private final StandingSyncService standingSyncService;
    private final LineupSyncService lineupSyncService;
    private final ScorersService scorersService;

    @GetMapping
    public String syncPage(Model model) {
        model.addAttribute("apiEnabled", footballApiClient.isEnabled());
        return "admin/sync";
    }

    @PostMapping("/teams")
    public String syncTeams(RedirectAttributes ra) {
        SyncResult r = teamSyncService.syncTeamsAndSquads();
        ra.addFlashAttribute("successMessage", "Teams/Squads: " + r.message());
        return "redirect:/admin/sync";
    }

    @PostMapping("/bootstrap")
    public String runBootstrap(RedirectAttributes ra) {
        String result = bootstrapSyncService.runFullBootstrap();
        ra.addFlashAttribute("successMessage", "Bootstrap complete:\n" + result);
        return "redirect:/admin/sync";
    }

    @PostMapping("/matches")
    public String syncMatches(RedirectAttributes ra) {
        SyncResult r = matchSyncService.syncGroupStageMatches();
        ra.addFlashAttribute("successMessage", "Matches: " + r.message());
        return "redirect:/admin/sync";
    }

    @PostMapping("/standings")
    public String syncStandings(RedirectAttributes ra) {
        SyncResult r = standingSyncService.syncStandings();
        ra.addFlashAttribute("successMessage", "Standings: " + r.message());
        return "redirect:/admin/sync";
    }

    @PostMapping("/lineups")
    public String syncLineups(RedirectAttributes ra) {
        SyncResult r = lineupSyncService.syncLineups();
        ra.addFlashAttribute("successMessage", "Lineups: " + r.message());
        return "redirect:/admin/sync";
    }

    @PostMapping("/scorers")
    public String syncScorers(RedirectAttributes ra) {
        SyncResult r = scorersService.syncScorers();
        ra.addFlashAttribute("successMessage", "Scorers: " + r.message());
        return "redirect:/admin/sync";
    }
}
