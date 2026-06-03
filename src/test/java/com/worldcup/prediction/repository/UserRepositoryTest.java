package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .status(UserStatus.ACTIVE)
                .role(UserRole.PARTICIPANT)
                .totalPoints(15)
                .exactScoreCount(3)
                .correctWinnerCount(4)
                .build());

        userRepository.save(User.builder()
                .email("bob@example.com")
                .firstName("Bob")
                .lastName("Jones")
                .status(UserStatus.PENDING)
                .role(UserRole.PARTICIPANT)
                .totalPoints(0)
                .exactScoreCount(0)
                .correctWinnerCount(0)
                .build());
    }

    @Test
    void findByEmailIgnoreCase_whenEmailExists_returnsUser() {
        Optional<User> found = userRepository.findByEmailIgnoreCase("ALICE@EXAMPLE.COM");
        assertThat(found).isPresent();
        assertThat(found.get().getFirstName()).isEqualTo("Alice");
    }

    @Test
    void findByEmailIgnoreCase_whenEmailNotFound_returnsEmpty() {
        Optional<User> found = userRepository.findByEmailIgnoreCase("unknown@example.com");
        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmailIgnoreCase_whenExists_returnsTrue() {
        assertThat(userRepository.existsByEmailIgnoreCase("alice@example.com")).isTrue();
    }

    @Test
    void findByStatus_returnsOnlyMatchingUsers() {
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
        assertThat(activeUsers).hasSize(1);
        assertThat(activeUsers.get(0).getEmail()).isEqualTo("alice@example.com");

        List<User> pendingUsers = userRepository.findByStatus(UserStatus.PENDING);
        assertThat(pendingUsers).hasSize(1);
        assertThat(pendingUsers.get(0).getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    void findLeaderboard_returnsActiveUsersOrderedByPoints() {
        userRepository.save(User.builder()
                .email("charlie@example.com")
                .firstName("Charlie")
                .lastName("Brown")
                .status(UserStatus.ACTIVE)
                .role(UserRole.PARTICIPANT)
                .totalPoints(25)
                .exactScoreCount(5)
                .correctWinnerCount(6)
                .build());

        List<User> leaderboard = userRepository.findLeaderboard();
        assertThat(leaderboard).hasSize(2); // alice and charlie (bob is PENDING)
        assertThat(leaderboard.get(0).getEmail()).isEqualTo("charlie@example.com");
        assertThat(leaderboard.get(1).getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void countByStatus_returnsCorrectCount() {
        assertThat(userRepository.countByStatus(UserStatus.ACTIVE)).isEqualTo(1);
        assertThat(userRepository.countByStatus(UserStatus.PENDING)).isEqualTo(1);
        assertThat(userRepository.countByStatus(UserStatus.DISABLED)).isEqualTo(0);
    }

    @Test
    void save_persistsAllFields() {
        User admin = userRepository.save(User.builder()
                .email("admin@example.com")
                .firstName("Super")
                .lastName("Admin")
                .status(UserStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .totalPoints(0)
                .exactScoreCount(0)
                .correctWinnerCount(0)
                .build());

        User found = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(found.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(found.isAdmin()).isTrue();
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
