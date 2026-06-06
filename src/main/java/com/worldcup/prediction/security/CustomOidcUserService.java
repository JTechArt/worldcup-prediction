package com.worldcup.prediction.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final CustomOAuth2UserService customOAuth2UserService;

    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // Delegate to the existing CustomOAuth2UserService for user lookup/creation
        OAuth2UserRequest oauth2Request = new OAuth2UserRequest(
                userRequest.getClientRegistration(),
                userRequest.getAccessToken(),
                userRequest.getAdditionalParameters()
        );
        CustomOAuth2User customUser = customOAuth2UserService.loadUser(oauth2Request);

        // Wrap with OIDC token info
        return new CustomOAuth2User(
                customUser.getUser(),
                customUser.getAttributes(),
                userRequest.getIdToken(),
                null
        );
    }
}
