package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityMembershipRepository extends JpaRepository<CommunityMembership, Long> {
    Optional<CommunityMembership> findByCommunityIdAndUserId(Long communityId, Long userId);
    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);
    List<CommunityMembership> findByUserId(Long userId);
    List<CommunityMembership> findByUserIdAndStatus(Long userId, MembershipStatus status);
    List<CommunityMembership> findByCommunityIdAndStatus(Long communityId, MembershipStatus status);
    List<CommunityMembership> findByCommunityId(Long communityId);

    @Query("""
            SELECT cm FROM CommunityMembership cm
            JOIN FETCH cm.user
            WHERE cm.community.id = :communityId
            ORDER BY cm.status ASC, cm.joinedAt ASC
            """)
    List<CommunityMembership> findByCommunityIdWithUser(@Param("communityId") Long communityId);
    long countByCommunityIdAndStatus(Long communityId, MembershipStatus status);

    @Query("""
            SELECT cm FROM CommunityMembership cm
            JOIN FETCH cm.community
            WHERE cm.user.id = :userId AND cm.status = :status
            ORDER BY cm.community.name ASC
            """)
    List<CommunityMembership> findByUserIdAndStatusWithCommunity(
            @Param("userId") Long userId, @Param("status") MembershipStatus status);

    @Query("""
            SELECT cm FROM CommunityMembership cm
            JOIN FETCH cm.user
            WHERE cm.community.id = :communityId AND cm.status = :status
            ORDER BY cm.totalPoints DESC, cm.exactScoreCount DESC
            """)
    List<CommunityMembership> findByCommunityIdAndStatusWithUser(
            @Param("communityId") Long communityId, @Param("status") MembershipStatus status);

    Optional<CommunityMembership> findByCommunityIdAndUserIdAndRole(
            Long communityId, Long userId, CommunityRole role);

    @Query("""
            SELECT cm FROM CommunityMembership cm
            JOIN FETCH cm.community
            WHERE cm.user.id = :userId
            ORDER BY cm.community.name ASC
            """)
    List<CommunityMembership> findByUserIdWithCommunity(@Param("userId") Long userId);
}
