package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/admin/prediction-windows")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminPredictionWindowController {

    private final PredictionWindowService windowService;
    private final UserService userService;
    private final MatchRepository matchRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("windows", windowService.findAllGlobal());
        return "admin/prediction-windows";
    }

    @GetMapping("/new")
    public String newForm(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Model model) {
        model.addAttribute("window", PredictionWindow.builder().build());
        if (from != null && to != null) {
            model.addAttribute("preview", windowService.generatePreview(from, to));
            model.addAttribute("from", from);
            model.addAttribute("to", to);
        }
        return "admin/prediction-window-form";
    }

    @PostMapping("/new")
    public String create(
            @RequestParam String label,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime openAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime closeAt,
            @RequestParam(required = false) List<Long> matchIds,
            @AuthenticationPrincipal CustomOAuth2User principal,
            RedirectAttributes redirectAttributes) {

        User creator = userService.findById(principal.getUserId());
        Set<Match> matchSet = matchIds != null
                ? new HashSet<>(matchRepository.findAllById(matchIds))
                : new HashSet<>();
        PredictionWindow window = PredictionWindow.builder()
                .label(label).openAt(openAt).closeAt(closeAt).createdBy(creator)
                .matches(matchSet).build();
        windowService.save(window);
        redirectAttributes.addFlashAttribute("successMessage", "Window created as DRAFT.");
        return "redirect:/admin/prediction-windows";
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable Long id, RedirectAttributes ra) {
        windowService.publish(id);
        ra.addFlashAttribute("successMessage", "Window published — will activate at open time.");
        return "redirect:/admin/prediction-windows";
    }

    @PostMapping("/{id}/force-open")
    public String forceOpen(@PathVariable Long id, RedirectAttributes ra) {
        windowService.forceOpen(id);
        ra.addFlashAttribute("successMessage", "Window forced OPEN.");
        return "redirect:/admin/prediction-windows";
    }

    @PostMapping("/{id}/force-close")
    public String forceClose(@PathVariable Long id, RedirectAttributes ra) {
        windowService.forceClose(id);
        ra.addFlashAttribute("successMessage", "Window forced CLOSED.");
        return "redirect:/admin/prediction-windows";
    }

    @PostMapping("/{id}/reset-override")
    public String resetOverride(@PathVariable Long id, RedirectAttributes ra) {
        windowService.resetOverride(id);
        ra.addFlashAttribute("successMessage", "Override cleared.");
        return "redirect:/admin/prediction-windows";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        windowService.delete(id);
        ra.addFlashAttribute("successMessage", "Window deleted.");
        return "redirect:/admin/prediction-windows";
    }
}
