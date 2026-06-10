package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.dto.MemberSubmissionStatusDto;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/c/{slug}/admin/submission-status")
@RequiredArgsConstructor
public class CommunityAdminSubmissionController {

    private final RoundWindowService roundWindowService;
    private final RoundSubmissionService roundSubmissionService;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.timezone:UTC}")
    private String timezoneId;

    private ZoneId appZone;

    @PostConstruct
    void init() {
        appZone = ZoneId.of(timezoneId);
    }

    @GetMapping
    public String statusPage(@PathVariable String slug,
                             @RequestParam(required = false) String round,
                             HttpServletRequest request,
                             Model model) {
        Community community = (Community) request.getAttribute("community");
        Long communityId = community.getId();

        List<RoundWindow> allWindows = roundWindowService.findAll();

        String selectedRound = round;
        if (selectedRound == null) {
            LocalDateTime now = LocalDateTime.now();
            selectedRound = allWindows.stream()
                    .filter(rw -> isOpen(rw, now))
                    .map(RoundWindow::getRoundLabel)
                    .findFirst()
                    .orElseGet(() -> allWindows.isEmpty() ? null : allWindows.get(0).getRoundLabel());
        }

        String closesAtIso = null;
        if (selectedRound != null) {
            final String finalRound = selectedRound;
            closesAtIso = allWindows.stream()
                    .filter(rw -> rw.getRoundLabel().equals(finalRound) && rw.getAutoClosesAt() != null)
                    .map(rw -> rw.getAutoClosesAt()
                            .atZone(appZone)
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .findFirst().orElse(null);
        }

        List<MemberSubmissionStatusDto> statuses = List.of();
        if (selectedRound != null) {
            List<CommunityMembership> members = communityMembershipRepository
                    .findByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);
            Map<Long, RoundSubmission> submissionMap =
                    roundSubmissionService.findStatusesForCommunityRound(communityId, selectedRound);
            statuses = members.stream()
                    .map(m -> {
                        RoundSubmission rs = submissionMap.get(m.getUser().getId());
                        return new MemberSubmissionStatusDto(
                                m.getUser().getId(),
                                m.getUser().getFullName(),
                                m.getUser().getEmail(),
                                m.getUser().getAvatarUrl(),
                                rs != null,
                                rs != null ? rs.getSubmittedAt() : null);
                    })
                    .sorted(Comparator.comparing(MemberSubmissionStatusDto::submitted))
                    .toList();
        }

        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("allWindows", allWindows);
        model.addAttribute("selectedRound", selectedRound);
        model.addAttribute("closesAtIso", closesAtIso);
        model.addAttribute("statuses", statuses);
        model.addAttribute("pageTitle", community.getName() + " · Submission Status");
        return "community/admin/submission-status";
    }

    @PostMapping("/{userId}/remind")
    public String remind(@PathVariable String slug,
                         @PathVariable Long userId,
                         @RequestParam String round,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        Community community = (Community) request.getAttribute("community");
        Long communityId = community.getId();

        communityMembershipRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User is not a member of this community"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found: " + userId));

        emailService.sendPredictionReminder(user, round);
        redirectAttributes.addFlashAttribute("successMessage",
                "Reminder sent to " + user.getDisplayName());

        return "redirect:" + UriComponentsBuilder.fromPath("/c/{slug}/admin/submission-status")
                .queryParam("round", round)
                .buildAndExpand(slug)
                .encode()
                .toUriString();
    }

    private boolean isOpen(RoundWindow rw, LocalDateTime now) {
        if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_OPEN) return true;
        if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_CLOSED) return false;
        if (rw.getAutoOpensAt() == null || rw.getAutoClosesAt() == null) return false;
        return !now.isBefore(rw.getAutoOpensAt()) && now.isBefore(rw.getAutoClosesAt());
    }
}
