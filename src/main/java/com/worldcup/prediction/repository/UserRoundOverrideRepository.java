package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.UserRoundOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoundOverrideRepository extends JpaRepository<UserRoundOverride, Long> {

    Optional<UserRoundOverride> findByUserIdAndCommunityIdAndRoundLabel(
            Long userId, Long communityId, String roundLabel);

    @Query("SELECT o FROM UserRoundOverride o WHERE o.user.id = :userId " +
           "AND o.communityId = :communityId AND o.roundLabel = :roundLabel AND o.usedAt IS NULL")
    Optional<UserRoundOverride> findActiveOverride(Long userId, Long communityId, String roundLabel);

    List<UserRoundOverride> findByCommunityIdAndRoundLabel(Long communityId, String roundLabel);

    List<UserRoundOverride> findByCommunityIdOrderByCreatedAtDesc(Long communityId);
}
