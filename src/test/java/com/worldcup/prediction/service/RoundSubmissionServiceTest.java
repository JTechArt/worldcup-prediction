package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.RoundSubmission;
import com.worldcup.prediction.repository.RoundSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoundSubmissionServiceTest {

    @Mock private RoundSubmissionRepository repository;

    private RoundSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new RoundSubmissionService(repository);
    }

    @Test
    void upsert_createsNewRow_whenNoneExists() {
        when(repository.findByUserIdAndCommunityIdAndRoundLabel(1L, 2L, "Matchday 1"))
                .thenReturn(Optional.empty());

        service.upsert(1L, 2L, "Matchday 1");

        ArgumentCaptor<RoundSubmission> captor = ArgumentCaptor.forClass(RoundSubmission.class);
        verify(repository).save(captor.capture());
        RoundSubmission saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getCommunityId()).isEqualTo(2L);
        assertThat(saved.getRoundLabel()).isEqualTo("Matchday 1");
        assertThat(saved.getSubmittedAt()).isNotNull();
    }

    @Test
    void upsert_updatesSubmittedAt_whenRowExists() {
        LocalDateTime original = LocalDateTime.of(2026, 6, 1, 10, 0);
        RoundSubmission existing = RoundSubmission.builder()
                .id(99L).userId(1L).communityId(2L).roundLabel("Matchday 1")
                .submittedAt(original).build();
        when(repository.findByUserIdAndCommunityIdAndRoundLabel(1L, 2L, "Matchday 1"))
                .thenReturn(Optional.of(existing));

        service.upsert(1L, 2L, "Matchday 1");

        verify(repository).save(existing);
        assertThat(existing.getSubmittedAt()).isAfter(original);
    }

    @Test
    void hasSubmitted_delegatesToRepository() {
        when(repository.existsByUserIdAndCommunityIdAndRoundLabel(1L, 2L, "Matchday 1"))
                .thenReturn(true);
        assertThat(service.hasSubmitted(1L, 2L, "Matchday 1")).isTrue();

        when(repository.existsByUserIdAndCommunityIdAndRoundLabel(1L, 2L, "Matchday 2"))
                .thenReturn(false);
        assertThat(service.hasSubmitted(1L, 2L, "Matchday 2")).isFalse();
    }

    @Test
    void findStatusesForCommunityRound_returnsMapKeyedByUserId() {
        RoundSubmission rs1 = RoundSubmission.builder().id(1L).userId(10L).communityId(5L)
                .roundLabel("Matchday 1").submittedAt(LocalDateTime.now()).build();
        RoundSubmission rs2 = RoundSubmission.builder().id(2L).userId(20L).communityId(5L)
                .roundLabel("Matchday 1").submittedAt(LocalDateTime.now()).build();
        when(repository.findByCommunityIdAndRoundLabel(5L, "Matchday 1"))
                .thenReturn(List.of(rs1, rs2));

        Map<Long, RoundSubmission> result = service.findStatusesForCommunityRound(5L, "Matchday 1");

        assertThat(result).containsOnlyKeys(10L, 20L);
        assertThat(result.get(10L)).isSameAs(rs1);
        assertThat(result.get(20L)).isSameAs(rs2);
    }
}
