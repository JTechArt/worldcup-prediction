package com.worldcup.prediction.config;

import com.worldcup.prediction.security.AccountStatusFilter;
import com.worldcup.prediction.security.CustomOAuth2UserService;
import com.worldcup.prediction.security.OAuth2AuthenticationFailureHandler;
import com.worldcup.prediction.security.OAuth2AuthenticationSuccessHandler;
import com.worldcup.prediction.security.SuperAdminAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    private final AccountStatusFilter accountStatusFilter;
    private final SuperAdminAuthenticationProvider superAdminAuthenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(superAdminAuthenticationProvider)
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                .requestMatchers("/", "/login", "/error").permitAll()
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
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login/form")
                .defaultSuccessUrl("/admin", true)
                .failureUrl("/login?error")
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
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
}
