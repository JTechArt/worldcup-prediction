package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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

        User dbUser = userOpt.get();
        UserStatus dbStatus = dbUser.getStatus();

        if (dbStatus == UserStatus.DISABLED) {
            log.info("Disabled user attempted access, invalidating session: {}", customUser.getEmail());
            invalidateAndRedirect(request, response);
            return;
        }

        boolean isSuperAdmin = customUser.getRole() == UserRole.SUPER_ADMIN;

        // If status changed in DB since the session was created (e.g. admin approved a PENDING user),
        // refresh the session and send them to the right place.
        if (!isSuperAdmin && dbStatus != customUser.getStatus()) {
            log.info("User {} status changed {} → {}, refreshing session",
                    customUser.getEmail(), customUser.getStatus(), dbStatus);
            refreshSecurityContext(request, customUser, dbUser);
            response.sendRedirect(dbStatus == UserStatus.ACTIVE ? "/communities" : "/pending");
            return;
        }

        // PENDING users may only see /pending (and the public paths already excluded above).
        if (!isSuperAdmin && dbStatus == UserStatus.PENDING) {
            String path = request.getServletPath();
            if (!path.startsWith("/pending")) {
                response.sendRedirect("/pending");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void refreshSecurityContext(HttpServletRequest request, CustomOAuth2User old, User dbUser) {
        CustomOAuth2User fresh = new CustomOAuth2User(
                dbUser, old.getAttributes(), old.getIdToken(), old.getUserInfo());
        UsernamePasswordAuthenticationToken newAuth =
                new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(newAuth);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }
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
