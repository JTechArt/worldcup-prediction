package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CustomOAuth2User implements OidcUser {

    private final User user;
    private final Map<String, Object> attributes;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public CustomOAuth2User(User user, Map<String, Object> attributes) {
        this(user, attributes, null, null);
    }

    public CustomOAuth2User(User user, Map<String, Object> attributes,
                            OidcIdToken idToken, OidcUserInfo userInfo) {
        this.user = user;
        this.attributes = attributes;
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    @Override
    public Map<String, Object> getClaims() {
        return attributes;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    /**
     * Spring Security uses getName() as the principal name stored in the session.
     * Email is used so AccountStatusFilter can reload by email on each request.
     */
    @Override
    public String getName() {
        return user.getEmail();
    }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }

    public UserStatus getStatus() {
        return user.getStatus();
    }

    public UserRole getRole() {
        return user.getRole();
    }

    public String getEmail() {
        return user.getEmail();
    }

    public String getDisplayName() {
        return user.getFirstName() + " " + user.getLastName();
    }

    public String getAvatarUrl() {
        return user.getAvatarUrl();
    }

    public String getInitials() {
        String f = user.getFirstName();
        String l = user.getLastName();
        String fi = (f != null && !f.isEmpty()) ? String.valueOf(f.charAt(0)).toUpperCase() : "";
        String li = (l != null && !l.isEmpty()) ? String.valueOf(l.charAt(0)).toUpperCase() : "";
        return fi + li;
    }
}
