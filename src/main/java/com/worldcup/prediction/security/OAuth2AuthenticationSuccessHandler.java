package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final CommunityMembershipRepository membershipRepository;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        if (!(authentication.getPrincipal() instanceof CustomOAuth2User customUser)) {
            log.warn("Unexpected principal type: {}", authentication.getPrincipal().getClass());
            getRedirectStrategy().sendRedirect(request, response, "/communities");
            return;
        }

        UserStatus status = customUser.getStatus();
        log.info("Authentication success for user={} status={}", customUser.getEmail(), status);

        String targetUrl = switch (status) {
            case PENDING  -> "/pending";
            case ACTIVE   -> resolveActiveRedirect(customUser);
            case DISABLED -> "/login?disabled";
        };

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String resolveActiveRedirect(CustomOAuth2User customUser) {
        if (customUser.getRole() == UserRole.SUPER_ADMIN) {
            return "/admin";
        }
        List<CommunityMembership> memberships = membershipRepository
                .findByUserIdAndStatus(customUser.getUserId(), MembershipStatus.ACTIVE);
        if (memberships.size() == 1) {
            return "/c/" + memberships.get(0).getCommunity().getSlug() + "/home";
        }
        return "/communities";
    }
}
