package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.AuditAction;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import com.worldcup.prediction.service.UserRoundOverrideService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/c/{slug}/admin/reopen-predictions")
@RequiredArgsConstructor
public class CommunityAdminReopenController {

    private final RoundWindowService roundWindowService;
    private final RoundSubmissionService roundSubmissionService;
    private final UserRoundOverrideService overrideService;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final AuditLogService auditLogService;

    @GetMapping
    public String reopenPage(@PathVariable String slug,
                             @RequestParam(required = false) String round,
                             HttpServletRequest request,
                             Model model) {
        Community community = (Community) request.getAttribute("community");
        Long communityId = community.getId();

        List<RoundWindow> allWindows = roundWindowService.findAll();

        String selectedRound = round;
        if (selectedRound == null && !allWindows.isEmpty()) {
            selectedRound = allWindows.get(0).getRoundLabel();
        }

        List<MemberOverrideStatus> statuses = List.of();
        List<Match> eligibleMatches = List.of();

        if (selectedRound != null) {
            eligibleMatches = overrideService.getEligibleMatches(selectedRound, LocalDateTime.now());

            List<CommunityMembership> members = communityMembershipRepository
                    .findByCommunityIdAndStatusWithUser(communityId, MembershipStatus.ACTIVE);
            Map<Long, RoundSubmission> submissionMap =
                    roundSubmissionService.findStatusesForCommunityRound(communityId, selectedRound);
            List<UserRoundOverride> overrides =
                    overrideService.findByCommunityAndRound(communityId, selectedRound);
            Map<Long, UserRoundOverride> overrideMap = new HashMap<>();
            for (UserRoundOverride o : overrides) {
                overrideMap.put(o.getUser().getId(), o);
            }

            statuses = members.stream()
                    .map(m -> {
                        Long userId = m.getUser().getId();
                        boolean submitted = submissionMap.containsKey(userId);
                        UserRoundOverride override = overrideMap.get(userId);
                        return new MemberOverrideStatus(
                                userId,
                                m.getUser().getFullName(),
                                m.getUser().getEmail(),
                                m.getUser().getAvatarUrl(),
                                submitted,
                                override != null,
                                override != null && !override.isUsed(),
                                override != null ? override.getId() : null
                        );
                    })
                    .sorted(Comparator.comparing(MemberOverrideStatus::submitted))
                    .toList();
        }

        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("allWindows", allWindows);
        model.addAttribute("selectedRound", selectedRound);
        model.addAttribute("statuses", statuses);
        model.addAttribute("eligibleMatchCount", eligibleMatches.size());
        model.addAttribute("eligibleMatches", eligibleMatches);
        model.addAttribute("pageTitle", community.getName() + " · Reopen Predictions");
        return "community/admin/reopen-predictions";
    }

    @PostMapping("/{userId}/open")
    public String openForUser(@PathVariable String slug,
                              @PathVariable Long userId,
                              @RequestParam String round,
                              @AuthenticationPrincipal CustomOAuth2User admin,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        Community community = (Community) request.getAttribute("community");
        try {
            UserRoundOverride override = overrideService.create(
                    userId, community.getId(), round, admin.getUserId());

            auditLogService.log(
                    admin.getUserId(),
                    AuditAction.PREDICTION_REOPENED_FOR_USER,
                    "USER_ROUND_OVERRIDE",
                    override.getId(),
                    "Reopened " + round + " for user " + userId
            );

            redirectAttributes.addFlashAttribute("successMessage",
                    "Prediction window reopened for " + override.getUser().getFullName());
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:" + UriComponentsBuilder.fromPath("/c/{slug}/admin/reopen-predictions")
                .queryParam("round", round)
                .buildAndExpand(slug)
                .encode()
                .toUriString();
    }

    @PostMapping("/{overrideId}/revoke")
    public String revoke(@PathVariable String slug,
                         @PathVariable Long overrideId,
                         @RequestParam String round,
                         RedirectAttributes redirectAttributes) {
        overrideService.revoke(overrideId);
        redirectAttributes.addFlashAttribute("successMessage", "Override revoked.");
        return "redirect:" + UriComponentsBuilder.fromPath("/c/{slug}/admin/reopen-predictions")
                .queryParam("round", round)
                .buildAndExpand(slug)
                .encode()
                .toUriString();
    }

    public record MemberOverrideStatus(
            Long userId,
            String displayName,
            String email,
            String avatarUrl,
            boolean submitted,
            boolean hasOverride,
            boolean overrideActive,
            Long overrideId
    ) {}
}
