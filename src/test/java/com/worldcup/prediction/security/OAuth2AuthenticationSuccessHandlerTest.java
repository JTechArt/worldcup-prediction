package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AuthenticationSuccessHandlerTest {

    private OAuth2AuthenticationSuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new OAuth2AuthenticationSuccessHandler();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    private Authentication authFor(UserStatus status) {
        User user = User.builder()
                .id(1L).email("test@example.com").firstName("Test").lastName("User")
                .status(status).role(UserRole.PARTICIPANT).build();
        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of("sub", "id-1"));
        return new TestingAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING,  /pending",
            "ACTIVE,   /home",
            "DISABLED, /login?disabled"
    })
    void redirectsBasedOnStatus(UserStatus status, String expectedUrl) throws Exception {
        handler.onAuthenticationSuccess(request, response, authFor(status));
        assertThat(response.getRedirectedUrl()).isEqualTo(expectedUrl.trim());
    }
}
