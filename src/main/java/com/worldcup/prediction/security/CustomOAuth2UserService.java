package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.Invitation;
import com.worldcup.prediction.domain.OAuthIdentity;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.OAuthProvider;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.InvitationRepository;
import com.worldcup.prediction.repository.OAuthIdentityRepository;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final OAuthIdentityRepository oauthIdentityRepository;
    private final InvitationRepository invitationRepository;

    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();

    void setDelegate(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public CustomOAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthProvider provider = resolveProvider(registrationId);
        Map<String, Object> attributes = oauth2User.getAttributes();

        String providerSubject = extractProviderSubject(attributes);
        String email = extractEmail(attributes);
        String firstName = extractFirstName(attributes);
        String lastName = extractLastName(attributes);
        String avatarUrl = extractAvatarUrl(attributes);

        log.debug("OAuth2 login: provider={} subject={} email={}", provider, providerSubject, email);

        // 1. Returning user — same provider identity
        Optional<User> byProvider = userRepository.findByProviderAndProviderId(provider, providerSubject);
        if (byProvider.isPresent()) {
            User user = byProvider.get();
            user.setAvatarUrl(avatarUrl);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            User saved = userRepository.save(user);
            log.info("Returning user login: id={} email={} status={}", saved.getId(), saved.getEmail(), saved.getStatus());
            return new CustomOAuth2User(saved, attributes);
        }

        // 2. Same email, new provider — merge onto existing account
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            user.setAvatarUrl(avatarUrl);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            User saved = userRepository.save(user);

            OAuthIdentity identity = OAuthIdentity.builder()
                    .user(saved)
                    .provider(provider)
                    .providerSubject(providerSubject)
                    .email(email)
                    .avatarUrl(avatarUrl)
                    .build();
            oauthIdentityRepository.save(identity);

            log.info("Merged provider {} onto existing user id={}", provider, saved.getId());
            return new CustomOAuth2User(saved, attributes);
        }

        // 3. Brand-new user — check invitation first
        UserStatus initialStatus = UserStatus.PENDING;
        Optional<Invitation> invitation = invitationRepository.findByEmailIgnoreCase(email);
        if (invitation.isPresent() && !invitation.get().isAccepted()) {
            initialStatus = UserStatus.ACTIVE;
            invitation.get().setAcceptedAt(LocalDateTime.now());
            invitationRepository.save(invitation.get());
            log.info("Invited user auto-approved: email={}", email);
        }

        User newUser = User.builder()
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .avatarUrl(avatarUrl)
                .status(initialStatus)
                .role(UserRole.PARTICIPANT)
                .build();
        User saved = userRepository.save(newUser);

        OAuthIdentity identity = OAuthIdentity.builder()
                .user(saved)
                .provider(provider)
                .providerSubject(providerSubject)
                .email(email)
                .avatarUrl(avatarUrl)
                .build();
        oauthIdentityRepository.save(identity);

        log.info("New user registered: id={} email={} status={}", saved.getId(), saved.getEmail(), initialStatus);
        return new CustomOAuth2User(saved, attributes);
    }

    private OAuthProvider resolveProvider(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> OAuthProvider.GOOGLE;
            case "linkedin" -> OAuthProvider.LINKEDIN;
            default -> throw new OAuth2AuthenticationException("Unsupported OAuth2 provider: " + registrationId);
        };
    }

    private String extractProviderSubject(Map<String, Object> attrs) {
        return (String) attrs.get("sub");
    }

    private String extractEmail(Map<String, Object> attrs) {
        String email = (String) attrs.get("email");
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Email not provided by OAuth2 provider");
        }
        return email.toLowerCase().trim();
    }

    private String extractFirstName(Map<String, Object> attrs) {
        return (String) attrs.getOrDefault("given_name", "");
    }

    private String extractLastName(Map<String, Object> attrs) {
        return (String) attrs.getOrDefault("family_name", "");
    }

    private String extractAvatarUrl(Map<String, Object> attrs) {
        return (String) attrs.getOrDefault("picture", null);
    }
}
