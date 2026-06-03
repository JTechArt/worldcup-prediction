package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.OAuthIdentity;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.OAuthProvider;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.OAuthIdentityRepository;
import com.worldcup.prediction.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthIdentityRepository oauthIdentityRepository;

    @Mock
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    @InjectMocks
    private CustomOAuth2UserService service;

    private OAuth2UserRequest googleRequest;
    private OAuth2User googleOAuth2User;

    @BeforeEach
    void setUp() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .build();

        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "token",
                Instant.now(), Instant.now().plusSeconds(3600));

        googleRequest = new OAuth2UserRequest(registration, token);

        Map<String, Object> attrs = Map.of(
                "sub", "google-sub-123",
                "email", "newuser@example.com",
                "given_name", "New",
                "family_name", "User",
                "picture", "https://example.com/photo.jpg"
        );
        googleOAuth2User = new DefaultOAuth2User(Set.of(), attrs, "sub");

        // Wire the mock delegate into the service (replaces DefaultOAuth2UserService)
        service.setDelegate(delegate);
    }

    @Test
    void loadUser_createsNewUser_onFirstLogin() {
        when(delegate.loadUser(googleRequest)).thenReturn(googleOAuth2User);
        when(userRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("newuser@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });

        CustomOAuth2User result = service.loadUser(googleRequest);

        assertThat(result.getEmail()).isEqualTo("newuser@example.com");
        assertThat(result.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(result.getRole()).isEqualTo(UserRole.PARTICIPANT);

        ArgumentCaptor<OAuthIdentity> identityCaptor = ArgumentCaptor.forClass(OAuthIdentity.class);
        verify(oauthIdentityRepository).save(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(identityCaptor.getValue().getProviderSubject()).isEqualTo("google-sub-123");
    }

    @Test
    void loadUser_mergesByEmail_whenNewProviderForExistingAccount() {
        User existingUser = User.builder()
                .id(10L).email("newuser@example.com").firstName("New").lastName("User")
                .status(UserStatus.ACTIVE).role(UserRole.PARTICIPANT).build();

        when(delegate.loadUser(googleRequest)).thenReturn(googleOAuth2User);
        when(userRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("newuser@example.com"))
                .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomOAuth2User result = service.loadUser(googleRequest);

        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(oauthIdentityRepository).save(any(OAuthIdentity.class));
    }

    @Test
    void loadUser_updatesAvatarUrl_onReturningUser() {
        User existingUser = User.builder()
                .id(5L).email("newuser@example.com").firstName("New").lastName("User")
                .avatarUrl("https://old-photo.com")
                .status(UserStatus.ACTIVE).role(UserRole.PARTICIPANT).build();

        when(delegate.loadUser(googleRequest)).thenReturn(googleOAuth2User);
        when(userRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.loadUser(googleRequest);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvatarUrl()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void loadUser_doesNotCreateNewIdentity_forReturningUser() {
        User existingUser = User.builder()
                .id(5L).email("newuser@example.com").firstName("New").lastName("User")
                .status(UserStatus.ACTIVE).role(UserRole.PARTICIPANT).build();

        when(delegate.loadUser(googleRequest)).thenReturn(googleOAuth2User);
        when(userRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.loadUser(googleRequest);

        verify(oauthIdentityRepository, never()).save(any());
    }
}
