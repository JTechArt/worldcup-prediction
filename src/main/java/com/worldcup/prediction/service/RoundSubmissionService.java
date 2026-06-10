package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.RoundSubmission;
import com.worldcup.prediction.repository.RoundSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoundSubmissionService {

    private final RoundSubmissionRepository repository;

    @Transactional
    public void upsert(Long userId, Long communityId, String roundLabel) {
        repository.findByUserIdAndCommunityIdAndRoundLabel(userId, communityId, roundLabel)
                .ifPresentOrElse(
                        rs -> {
                            rs.setSubmittedAt(LocalDateTime.now());
                            repository.save(rs);
                        },
                        () -> repository.save(RoundSubmission.builder()
                                .userId(userId)
                                .communityId(communityId)
                                .roundLabel(roundLabel)
                                .submittedAt(LocalDateTime.now())
                                .build())
                );
    }

    public boolean hasSubmitted(Long userId, Long communityId, String roundLabel) {
        return repository.existsByUserIdAndCommunityIdAndRoundLabel(userId, communityId, roundLabel);
    }

    public Map<Long, RoundSubmission> findStatusesForCommunityRound(Long communityId, String roundLabel) {
        return repository.findByCommunityIdAndRoundLabel(communityId, roundLabel)
                .stream()
                .collect(Collectors.toMap(RoundSubmission::getUserId, rs -> rs));
    }
}
