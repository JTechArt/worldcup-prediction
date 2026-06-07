package com.worldcup.prediction.config;

import com.worldcup.prediction.security.AccountStatusFilter;
import com.worldcup.prediction.security.CustomOAuth2UserService;
import com.worldcup.prediction.security.CustomOidcUserService;
import com.worldcup.prediction.security.OAuth2AuthenticationFailureHandler;
import com.worldcup.prediction.security.OAuth2AuthenticationSuccessHandler;
import com.worldcup.prediction.security.SuperAdminAuthenticationProvider;
import com.worldcup.prediction.security.UserAuthenticationProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    private final AccountStatusFilter accountStatusFilter;
    private final SuperAdminAuthenticationProvider superAdminAuthenticationProvider;
    private final UserAuthenticationProvider userAuthenticationProvider;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(List.of(superAdminAuthenticationProvider, userAuthenticationProvider));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(superAdminAuthenticationProvider)
            .authenticationProvider(userAuthenticationProvider)
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                .requestMatchers("/", "/login", "/login/email", "/register", "/admin/login", "/admin/login/form", "/error").permitAll()
                .requestMatchers("/dev/**").permitAll()
                // leaderboard is now community-scoped at /c/{slug}/leaderboard
                .requestMatchers("/fixtures/**").permitAll()
                .requestMatchers("/groups/**").permitAll()
                .requestMatchers("/bracket/**").permitAll()
                .requestMatchers("/teams/**").permitAll()
                .requestMatchers("/scorers/**").permitAll()
                .requestMatchers("/rules").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/pending").authenticated()
                .requestMatchers("/admin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/c/*/admin/**").authenticated()
                .requestMatchers("/c/**").authenticated()
                .requestMatchers("/communities/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    String path = request.getServletPath();
                    if (path.startsWith("/admin")) {
                        response.sendRedirect(request.getContextPath() + "/admin/login");
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login");
                    }
                })
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login/form")
                .defaultSuccessUrl("/admin", true)
                .failureUrl("/admin/login?error")
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestResolver(linkedInNonceAwareResolver())
                )
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                    .oidcUserService(customOidcUserService)
                )
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            );

        http.addFilterAfter(accountStatusFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private OAuth2AuthorizationRequestResolver linkedInNonceAwareResolver() {
        DefaultOAuth2AuthorizationRequestResolver defaultResolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, "/oauth2/authorization");

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return removeNonceForLinkedIn(defaultResolver.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return removeNonceForLinkedIn(defaultResolver.resolve(request, clientRegistrationId));
            }
        };
    }

    private static OAuth2AuthorizationRequest removeNonceForLinkedIn(OAuth2AuthorizationRequest request) {
        if (request == null) return null;
        if (!"linkedin".equals(request.getAttributes().get("registration_id"))) return request;

        Map<String, Object> params = new LinkedHashMap<>(request.getAdditionalParameters());
        params.remove(OidcParameterNames.NONCE);
        Map<String, Object> attrs = new LinkedHashMap<>(request.getAttributes());
        attrs.remove(OidcParameterNames.NONCE);

        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(request.getAuthorizationUri())
                .clientId(request.getClientId())
                .redirectUri(request.getRedirectUri())
                .scopes(request.getScopes())
                .state(request.getState())
                .additionalParameters(params)
                .attributes(attrs)
                .build();
    }
}
