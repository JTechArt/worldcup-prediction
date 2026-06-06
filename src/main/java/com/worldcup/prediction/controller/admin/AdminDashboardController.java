package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.AuditLog;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.MatchAdminService;
import com.worldcup.prediction.service.RoundWindowService;
import com.worldcup.prediction.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final UserService userService;
    private final MatchAdminService matchAdminService;
    private final AuditLogService auditLogService;
    private final RoundWindowService roundWindowService;

    @GetMapping
    public String dashboard(Model model) {
        long pendingCount = userService.countByStatus(UserStatus.PENDING);
        model.addAttribute("pendingCount", pendingCount);

        List<Match> todayMatches = matchAdminService.findByKickoffDate(LocalDate.now());
        model.addAttribute("todayMatches", todayMatches);

        long openRoundCount = roundWindowService.findAll().stream()
                .filter(rw -> roundWindowService.isRoundOpen(rw.getRoundLabel(), LocalDateTime.now()))
                .count();
        model.addAttribute("openRoundCount", openRoundCount);

        List<AuditLog> recentAudit = auditLogService.getRecent(10);
        model.addAttribute("recentAudit", recentAudit);

        return "admin/dashboard";
    }
}
