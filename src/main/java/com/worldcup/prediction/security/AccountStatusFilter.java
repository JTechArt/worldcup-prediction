package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Re-fetches the user from the database on every authenticated request.
 * Invalidates the session immediately if the account is DISABLED.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    private static final String[] SKIP_PATHS = {
            "/css/", "/js/", "/images/", "/webjars/", "/favicon.ico",
            "/login", "/admin/login", "/oauth2/", "/error"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        for (String skip : SKIP_PATHS) {
            if (path.startsWith(skip)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomOAuth2User customUser)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> userOpt = userRepository.findByEmail(customUser.getEmail());
        if (userOpt.isEmpty()) {
            log.warn("Authenticated user not found in DB, invalidating session: {}", customUser.getEmail());
            invalidateAndRedirect(request, response);
            return;
        }

        if (userOpt.get().getStatus() == UserStatus.DISABLED) {
            log.info("Disabled user attempted access, invalidating session: {}", customUser.getEmail());
            invalidateAndRedirect(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void invalidateAndRedirect(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect("/login?disabled");
    }
}
