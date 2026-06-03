package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.AuditAction;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    @GetMapping
    public String listUsers(@RequestParam(required = false) String status, Model model) {
        List<User> users = (status != null && !status.isBlank())
                ? userService.findByStatus(UserStatus.valueOf(status.toUpperCase()))
                : userService.findAll();
        model.addAttribute("users", users);
        model.addAttribute("statusFilter", status);
        return "admin/users";
    }

    @PostMapping("/{id}/approve")
    public String approveUser(@PathVariable Long id,
                              @AuthenticationPrincipal CustomOAuth2User admin,
                              Model model) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        User user = userService.approveUser(id);
        emailService.sendApprovalEmail(user);
        auditLogService.log(adminId, AuditAction.USER_APPROVED, "USER", id,
                "User " + user.getEmail() + " approved");
        model.addAttribute("user", user);
        return "admin/users :: userRow";
    }

    @PostMapping("/{id}/reject")
    public String rejectUser(@PathVariable Long id,
                             @AuthenticationPrincipal CustomOAuth2User admin,
                             Model model) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        User user = userService.rejectUser(id);
        emailService.sendRejectionEmail(user);
        auditLogService.log(adminId, AuditAction.USER_REJECTED, "USER", id,
                "User " + user.getEmail() + " rejected");
        model.addAttribute("user", user);
        return "admin/users :: userRow";
    }

    @PostMapping("/{id}/enable")
    public String enableUser(@PathVariable Long id,
                             @AuthenticationPrincipal CustomOAuth2User admin,
                             Model model) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        User user = userService.enableUser(id);
        auditLogService.log(adminId, AuditAction.USER_ENABLED, "USER", id,
                "User " + user.getEmail() + " enabled");
        model.addAttribute("user", user);
        return "admin/users :: userRow";
    }

    @PostMapping("/{id}/disable")
    public String disableUser(@PathVariable Long id,
                              @AuthenticationPrincipal CustomOAuth2User admin,
                              Model model) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        User user = userService.disableUser(id);
        auditLogService.log(adminId, AuditAction.USER_DISABLED, "USER", id,
                "User " + user.getEmail() + " disabled");
        model.addAttribute("user", user);
        return "admin/users :: userRow";
    }
}
