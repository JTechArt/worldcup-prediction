package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User pendingUser;

    @BeforeEach
    void setUp() {
        pendingUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.PENDING)
                .role(UserRole.USER)
                .build();
    }

    @Test
    void approveUser_setsStatusToActive() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(pendingUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.approveUser(1L);

        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(pendingUser);
    }

    @Test
    void approveUser_throwsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.approveUser(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void rejectUser_setsStatusToDisabled() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(pendingUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.rejectUser(1L);

        assertThat(result.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(userRepository).save(pendingUser);
    }

    @Test
    void disableUser_setsStatusToDisabled() {
        pendingUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(pendingUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.disableUser(1L);

        assertThat(result.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void enableUser_setsStatusToActive() {
        pendingUser.setStatus(UserStatus.DISABLED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(pendingUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.enableUser(1L);

        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void findPendingUsers_returnsOnlyPending() {
        when(userRepository.findByStatus(UserStatus.PENDING)).thenReturn(List.of(pendingUser));

        List<User> result = userService.findPendingUsers();

        assertThat(result).containsExactly(pendingUser);
    }

    @Test
    void findAll_delegatesToRepository() {
        when(userRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(pendingUser));

        List<User> result = userService.findAll();

        assertThat(result).containsExactly(pendingUser);
    }
}
