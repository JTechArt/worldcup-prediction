package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.dto.MemberSubmissionStatusDto;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/admin/communities/{communityId}/matchday-status")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminMatchdayStatusController {

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;
    private final RoundWindowService roundWindowService;
    private final RoundSubmissionService roundSubmissionService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @GetMapping
    public String statusPage(@PathVariable Long communityId,
                             @RequestParam(required = false) String round,
                             Model model) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found: " + communityId));

        List<RoundWindow> allWindows = roundWindowService.findAll();

        String selectedRound = round;
        if (selectedRound == null) {
            selectedRound = allWindows.stream()
                    .filter(rw -> roundWindowService.isRoundOpen(rw.getRoundLabel(), LocalDateTime.now()))
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
                            .atZone(ZoneId.of("UTC"))
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .findFirst().orElse(null);
        }

        List<MemberSubmissionStatusDto> statuses = List.of();
        if (selectedRound != null) {
            List<CommunityMembership> members = membershipRepository
                    .findByCommunityIdAndStatusWithUser(communityId, MembershipStatus.ACTIVE);
            Map<Long, RoundSubmission> submissionMap =
                    roundSubmissionService.findStatusesForCommunityRound(communityId, selectedRound);
            statuses = members.stream()
                    .map(m -> {
                        RoundSubmission rs = submissionMap.get(m.getUser().getId());
                        return new MemberSubmissionStatusDto(
                                m.getUser().getId(), m.getUser().getFullName(),
                                m.getUser().getEmail(), m.getUser().getAvatarUrl(),
                                rs != null, rs != null ? rs.getSubmittedAt() : null);
                    })
                    .sorted(Comparator.comparing(MemberSubmissionStatusDto::submitted))
                    .toList();
        }

        model.addAttribute("community", community);
        model.addAttribute("allWindows", allWindows);
        model.addAttribute("selectedRound", selectedRound);
        model.addAttribute("closesAtIso", closesAtIso);
        model.addAttribute("statuses", statuses);
        model.addAttribute("communityId", communityId);
        return "admin/matchday-status";
    }

    @PostMapping("/{userId}/remind")
    public String remind(@PathVariable Long communityId,
                         @RequestParam String round,
                         @PathVariable Long userId,
                         RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        emailService.sendPredictionReminder(user, round);
        redirectAttributes.addFlashAttribute("successMessage",
                "Reminder sent to " + user.getFullName());
        return "redirect:/admin/communities/" + communityId + "/matchday-status?round=" + round;
    }
}
