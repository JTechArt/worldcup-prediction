package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM predictions");
        jdbcTemplate.execute("DELETE FROM tournament_winner_predictions");
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute("DELETE FROM matches");
        jdbcTemplate.execute("DELETE FROM group_standings");
        jdbcTemplate.execute("DELETE FROM group_teams");
        jdbcTemplate.execute("DELETE FROM teams");
        jdbcTemplate.execute("DELETE FROM groups");
        jdbcTemplate.execute("DELETE FROM oauth_identities");
        jdbcTemplate.execute("DELETE FROM users");

        userRepository.save(User.builder()
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .totalPoints(15)
                .exactScoreCount(3)
                .correctWinnerCount(4)
                .build());

        userRepository.save(User.builder()
                .email("bob@example.com")
                .firstName("Bob")
                .lastName("Jones")
                .status(UserStatus.PENDING)
                .role(UserRole.USER)
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

    // TODO: findLeaderboard was removed from UserRepository during multi-community migration
    // @Test
    // void findLeaderboard_returnsActiveUsersOrderedByPoints() { ... }

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
                .role(UserRole.SUPER_ADMIN)
                .totalPoints(0)
                .exactScoreCount(0)
                .correctWinnerCount(0)
                .build());

        User found = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(found.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(found.isAdmin()).isTrue();
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
