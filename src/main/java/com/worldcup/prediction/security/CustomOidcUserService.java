package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.Invitation;
import com.worldcup.prediction.domain.OAuthIdentity;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.OAuthProvider;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.InvitationRepository;
import com.worldcup.prediction.repository.OAuthIdentityRepository;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final UserRepository userRepository;
    private final OAuthIdentityRepository oauthIdentityRepository;
    private final InvitationRepository invitationRepository;
    private final CommunityMembershipRepository membershipRepository;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcIdToken idToken = userRequest.getIdToken();
        Map<String, Object> claims = idToken.getClaims();

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthProvider provider = resolveProvider(registrationId);

        String providerSubject = idToken.getSubject();
        String email = extractClaim(claims, "email");
        String firstName = (String) claims.getOrDefault("given_name", "");
        String lastName = (String) claims.getOrDefault("family_name", "");
        String avatarUrl = (String) claims.getOrDefault("picture", null);

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Email not provided by OIDC provider");
        }
        email = email.toLowerCase().trim();

        log.debug("OIDC login: provider={} subject={} email={}", provider, providerSubject, email);

        Optional<User> byProvider = userRepository.findByProviderAndProviderId(provider, providerSubject);
        if (byProvider.isPresent()) {
            User user = byProvider.get();
            user.setAvatarUrl(avatarUrl);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            User saved = userRepository.save(user);
            return new CustomOAuth2User(saved, claims, idToken, null);
        }

        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            user.setAvatarUrl(avatarUrl);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            User saved = userRepository.save(user);

            oauthIdentityRepository.save(OAuthIdentity.builder()
                    .user(saved).provider(provider).providerSubject(providerSubject)
                    .email(email).avatarUrl(avatarUrl).build());
            log.info("Merged OIDC provider {} onto existing user id={}", provider, saved.getId());
            return new CustomOAuth2User(saved, claims, idToken, null);
        }

        UserStatus initialStatus = UserStatus.PENDING;
        Community invitedCommunity = null;
        Optional<Invitation> invitation = invitationRepository.findByEmailIgnoreCase(email);
        if (invitation.isPresent() && !invitation.get().isAccepted()) {
            initialStatus = UserStatus.ACTIVE;
            invitedCommunity = invitation.get().getCommunity();
            invitation.get().setAcceptedAt(LocalDateTime.now());
            invitationRepository.save(invitation.get());
        }

        User newUser = User.builder()
                .email(email).firstName(firstName).lastName(lastName)
                .avatarUrl(avatarUrl).status(initialStatus).role(UserRole.USER).build();
        User saved = userRepository.save(newUser);

        if (invitedCommunity != null) {
            membershipRepository.save(CommunityMembership.builder()
                    .community(invitedCommunity).user(saved)
                    .role(CommunityRole.MEMBER).status(MembershipStatus.ACTIVE).build());
        }

        oauthIdentityRepository.save(OAuthIdentity.builder()
                .user(saved).provider(provider).providerSubject(providerSubject)
                .email(email).avatarUrl(avatarUrl).build());

        log.info("New OIDC user registered: id={} email={} status={}", saved.getId(), email, initialStatus);
        return new CustomOAuth2User(saved, claims, idToken, null);
    }

    private OAuthProvider resolveProvider(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> OAuthProvider.GOOGLE;
            case "linkedin" -> OAuthProvider.LINKEDIN;
            default -> throw new OAuth2AuthenticationException("Unsupported OIDC provider: " + registrationId);
        };
    }

    private String extractClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        return value != null ? value.toString() : null;
    }
}
