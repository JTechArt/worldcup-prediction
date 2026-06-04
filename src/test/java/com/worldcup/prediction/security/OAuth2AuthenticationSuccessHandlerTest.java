package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2AuthenticationSuccessHandlerTest {

    private CommunityMembershipRepository membershipRepository;
    private OAuth2AuthenticationSuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        membershipRepository = mock(CommunityMembershipRepository.class);
        handler = new OAuth2AuthenticationSuccessHandler(membershipRepository);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        when(membershipRepository.findByUserIdAndStatus(any(), eq(MembershipStatus.ACTIVE)))
                .thenReturn(List.of());
    }

    private Authentication authFor(UserStatus status) {
        User user = User.builder()
                .id(1L).email("test@example.com").firstName("Test").lastName("User")
                .status(status).role(UserRole.USER).build();
        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of("sub", "id-1"));
        return new TestingAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING,  /pending",
            "ACTIVE,   /communities",
            "DISABLED, /login?disabled"
    })
    void redirectsBasedOnStatus(UserStatus status, String expectedUrl) throws Exception {
        handler.onAuthenticationSuccess(request, response, authFor(status));
        assertThat(response.getRedirectedUrl()).isEqualTo(expectedUrl.trim());
    }

    @Test
    void superAdmin_redirectsToAdmin() throws Exception {
        User user = User.builder()
                .id(2L).email("admin@worldcup.local").firstName("admin").lastName("Admin")
                .status(UserStatus.ACTIVE).role(UserRole.SUPER_ADMIN).build();
        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of("sub", "super-admin"));
        Authentication auth = new TestingAuthenticationToken(principal, null, principal.getAuthorities());

        handler.onAuthenticationSuccess(request, response, auth);
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin");
    }
}
