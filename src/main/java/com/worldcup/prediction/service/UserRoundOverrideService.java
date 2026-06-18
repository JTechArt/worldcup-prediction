package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.UserRoundOverride;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.UserRoundOverrideRepository;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserRoundOverrideService {

    private final UserRoundOverrideRepository overrideRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    public boolean hasActiveOverride(Long userId, Long communityId, String roundLabel) {
        return overrideRepository.findActiveOverride(userId, communityId, roundLabel).isPresent();
    }

    public Optional<UserRoundOverride> findActiveOverride(Long userId, Long communityId, String roundLabel) {
        return overrideRepository.findActiveOverride(userId, communityId, roundLabel);
    }

    public List<UserRoundOverride> findByCommunityAndRound(Long communityId, String roundLabel) {
        return overrideRepository.findByCommunityIdAndRoundLabel(communityId, roundLabel);
    }

    public List<UserRoundOverride> findByCommunity(Long communityId) {
        return overrideRepository.findByCommunityIdOrderByCreatedAtDesc(communityId);
    }

    /**
     * Returns matches in this round that are eligible for reopened prediction:
     * not started (SCHEDULED) and kickoff > now + 1 hour.
     */
    public List<Match> getEligibleMatches(String roundLabel, LocalDateTime now) {
        List<Match> roundMatches = matchRepository.findByRoundLabelWithTeams(roundLabel);
        return roundMatches.stream()
                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED)
                .filter(m -> m.getKickoffTime().isAfter(now.plusHours(1)))
                .toList();
    }

    @Transactional
    public UserRoundOverride create(Long userId, Long communityId, String roundLabel, Long adminId) {
        Optional<UserRoundOverride> existing =
                overrideRepository.findByUserIdAndCommunityIdAndRoundLabel(userId, communityId, roundLabel);
        if (existing.isPresent()) {
            UserRoundOverride override = existing.get();
            override.setUsedAt(null);
            return overrideRepository.save(override);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found: " + adminId));

        List<Match> eligible = getEligibleMatches(roundLabel, LocalDateTime.now());
        if (eligible.isEmpty()) {
            throw new IllegalStateException(
                    "No eligible matches in " + roundLabel + " — all matches have started or are within 1 hour of kickoff.");
        }

        UserRoundOverride override = UserRoundOverride.builder()
                .user(user)
                .communityId(communityId)
                .roundLabel(roundLabel)
                .createdBy(admin)
                .build();
        return overrideRepository.save(override);
    }

    @Transactional
    public void markUsed(Long userId, Long communityId, String roundLabel) {
        overrideRepository.findActiveOverride(userId, communityId, roundLabel)
                .ifPresent(o -> {
                    o.setUsedAt(LocalDateTime.now());
                    overrideRepository.save(o);
                });
    }

    @Transactional
    public void revoke(Long overrideId) {
        overrideRepository.deleteById(overrideId);
    }
}
