package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.RoundSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoundSubmissionRepository extends JpaRepository<RoundSubmission, Long> {

    boolean existsByUserIdAndCommunityIdAndRoundLabel(Long userId, Long communityId, String roundLabel);

    Optional<RoundSubmission> findByUserIdAndCommunityIdAndRoundLabel(Long userId, Long communityId, String roundLabel);

    List<RoundSubmission> findByCommunityIdAndRoundLabel(Long communityId, String roundLabel);
}
