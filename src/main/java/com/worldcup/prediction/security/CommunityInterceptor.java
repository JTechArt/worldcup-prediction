package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityInterceptor implements HandlerInterceptor {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^/c/([^/]+)");
    private static final Pattern ADMIN_PATTERN = Pattern.compile("^/c/[^/]+/admin");

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String path = request.getServletPath();
        Matcher matcher = SLUG_PATTERN.matcher(path);
        if (!matcher.find()) {
            return true;
        }

        String slug = matcher.group(1);
        Optional<Community> communityOpt = communityRepository.findBySlug(slug);
        if (communityOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Community not found");
            return false;
        }

        Community community = communityOpt.get();
        request.setAttribute("community", community);
        request.setAttribute("communitySlug", slug);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomOAuth2User customUser)) {
            response.sendRedirect("/login");
            return false;
        }

        if (customUser.getRole() == UserRole.SUPER_ADMIN) {
            request.setAttribute("communityMembership", null);
            return true;
        }

        Optional<CommunityMembership> membershipOpt =
                membershipRepository.findByCommunityIdAndUserId(community.getId(), customUser.getUserId());

        if (membershipOpt.isEmpty() || membershipOpt.get().getStatus() != MembershipStatus.ACTIVE) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not a member of this community");
            return false;
        }

        CommunityMembership membership = membershipOpt.get();
        request.setAttribute("communityMembership", membership);

        if (ADMIN_PATTERN.matcher(path).find() && membership.getRole() != CommunityRole.ADMIN) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Community admin access required");
            return false;
        }

        return true;
    }
}
