package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.GroupStanding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupStandingRepository extends JpaRepository<GroupStanding, Long> {

    List<GroupStanding> findByGroupIdOrderByPositionAsc(Long groupId);

    Optional<GroupStanding> findByGroupIdAndTeamId(Long groupId, Long teamId);

    @Query("SELECT MAX(gs.updatedAt) FROM GroupStanding gs")
    Optional<LocalDateTime> findMostRecentUpdateTime();
}
