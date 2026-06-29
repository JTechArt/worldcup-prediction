package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunityMembershipMissingPredictionTest {

    @Autowired CommunityMembershipRepository communityMembershipRepository;
    @Autowired PredictionRepository predictionRepository;
    @Autowired UserRepository userRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired CommunityRepository communityRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private User alice, bob, charlie;
    private Match match;
    private Community community;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM predictions");
        jdbcTemplate.execute("DELETE FROM tournament_winner_predictions");
        jdbcTemplate.execute("DELETE FROM community_memberships");
        jdbcTemplate.execute("DELETE FROM communities");
        jdbcTemplate.execute("DELETE FROM matches");
        jdbcTemplate.execute("DELETE FROM group_standings");
        jdbcTemplate.execute("DELETE FROM group_teams");
        jdbcTemplate.execute("DELETE FROM teams");
        jdbcTemplate.execute("DELETE FROM users");

        alice   = userRepository.save(buildUser("alice@test.com", "Alice"));
        bob     = userRepository.save(buildUser("bob@test.com", "Bob"));
        charlie = userRepository.save(buildUser("charlie@test.com", "Charlie"));

        community = communityRepository.save(
                Community.builder().name("Test Community").slug("test-community").build());

        match = matchRepository.save(Match.builder()
                .matchNumber(1)
                .roundLabel("Matchday 1")
                .stage(MatchStage.GROUP)
                .status(MatchStatus.SCHEDULED)
                .kickoffTime(LocalDateTime.now().plusDays(1))
                .build());

        // All three are active members
        for (User u : List.of(alice, bob, charlie)) {
            communityMembershipRepository.save(CommunityMembership.builder()
                    .community(community).user(u).role(CommunityRole.MEMBER)
                    .status(MembershipStatus.ACTIVE).build());
        }

        // Only alice submitted a prediction
        predictionRepository.save(Prediction.builder()
                .user(alice).match(match).community(community)
                .predictedHome(2).predictedAway(1).build());
    }

    @Test
    void findActiveMembersWithoutPrediction_returnsOnlyMissingUsers() {
        List<CommunityMembership> missing = communityMembershipRepository
                .findActiveMembersWithoutPrediction(community.getId(), match.getId());

        assertThat(missing).hasSize(2);
        assertThat(missing.stream().map(cm -> cm.getUser().getEmail()))
                .containsExactlyInAnyOrder("bob@test.com", "charlie@test.com");
    }

    @Test
    void findActiveMembersWithoutPrediction_excludesPendingMembers() {
        // Make bob's membership pending (not active)
        CommunityMembership bobMembership = communityMembershipRepository
                .findByCommunityIdAndUserId(community.getId(), bob.getId()).orElseThrow();
        bobMembership.setStatus(MembershipStatus.PENDING);
        communityMembershipRepository.save(bobMembership);

        List<CommunityMembership> missing = communityMembershipRepository
                .findActiveMembersWithoutPrediction(community.getId(), match.getId());

        assertThat(missing).hasSize(1);
        assertThat(missing.get(0).getUser().getEmail()).isEqualTo("charlie@test.com");
    }

    private User buildUser(String email, String firstName) {
        return User.builder()
                .email(email).firstName(firstName).lastName("Test")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
    }
}
