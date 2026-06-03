package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.enums.UserStatus;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Redirects after successful OAuth2 login based on account status:
 *   PENDING   → /pending        (approval waiting page)
 *   ACTIVE    → /home           (main app)
 *   DISABLED  → /login?disabled (error message on login page)
 */
@Component
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        if (!(authentication.getPrincipal() instanceof CustomOAuth2User customUser)) {
            log.warn("Unexpected principal type: {}", authentication.getPrincipal().getClass());
            getRedirectStrategy().sendRedirect(request, response, "/home");
            return;
        }

        UserStatus status = customUser.getStatus();
        log.info("Authentication success for user={} status={}", customUser.getEmail(), status);

        String targetUrl = switch (status) {
            case PENDING  -> "/pending";
            case ACTIVE   -> "/home";
            case DISABLED -> "/login?disabled";
        };

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
