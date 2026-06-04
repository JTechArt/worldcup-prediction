package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStatusFilterTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountStatusFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticated(String email) {
        User user = User.builder().id(1L).email(email)
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void allowsRequest_whenUserIsActive() throws Exception {
        setAuthenticated("active@example.com");
        User activeUser = User.builder().id(1L).email("active@example.com")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeUser));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void invalidatesSession_andRedirects_whenUserIsDisabled() throws Exception {
        setAuthenticated("disabled@example.com");
        User disabledUser = User.builder().id(1L).email("disabled@example.com")
                .status(UserStatus.DISABLED).role(UserRole.USER).build();
        when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(disabledUser));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?disabled");
    }

    @Test
    void passesThrough_whenNotAuthenticated() throws Exception {
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shouldNotFilter_returnsTrue_forStaticAssetPaths() {
        request.setServletPath("/css/style.css");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        request.setServletPath("/js/app.js");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        request.setServletPath("/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}
