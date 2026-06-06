package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional
    public Community createCommunity(String name, String slug, String description, Long createdById) {
        if (communityRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Community with slug '" + slug + "' already exists");
        }
        User creator = userRepository.findById(createdById)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + createdById));
        Community community = Community.builder()
                .name(name).slug(slug).description(description).createdBy(creator).build();
        return communityRepository.save(community);
    }

    @Transactional
    public void deleteCommunity(Long communityId) {
        communityRepository.deleteById(communityId);
        log.info("Deleted community id={}", communityId);
    }

    @Transactional(readOnly = true)
    public Optional<Community> findBySlug(String slug) {
        return communityRepository.findBySlug(slug);
    }

    @Transactional(readOnly = true)
    public Community findById(Long communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found: " + communityId));
    }

    @Transactional(readOnly = true)
    public List<Community> findAll() {
        return communityRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public CommunityMembership addMember(Long communityId, Long userId, CommunityRole role, MembershipStatus status) {
        Community community = findById(communityId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (membershipRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw new IllegalArgumentException("User " + userId + " is already a member of community " + communityId);
        }
        CommunityMembership membership = CommunityMembership.builder()
                .community(community).user(user).role(role).status(status).build();
        log.info("Added user {} to community {} as {} ({})", userId, communityId, role, status);
        return membershipRepository.save(membership);
    }

    @Transactional
    public CommunityMembership approveMember(Long communityId, Long userId) {
        CommunityMembership m = getMembership(communityId, userId);
        m.setStatus(MembershipStatus.ACTIVE);
        log.info("Approved membership: user={} community={}", userId, communityId);
        return membershipRepository.save(m);
    }

    @Transactional
    public CommunityMembership rejectMember(Long communityId, Long userId) {
        CommunityMembership m = getMembership(communityId, userId);
        m.setStatus(MembershipStatus.DISABLED);
        log.info("Rejected membership: user={} community={}", userId, communityId);
        return membershipRepository.save(m);
    }

    @Transactional
    public CommunityMembership setMemberRole(Long communityId, Long userId, CommunityRole role) {
        CommunityMembership m = getMembership(communityId, userId);
        m.setRole(role);
        log.info("Changed role: user={} community={} role={}", userId, communityId, role);
        return membershipRepository.save(m);
    }

    @Transactional(readOnly = true)
    public boolean isActiveMember(Long communityId, Long userId) {
        return membershipRepository.findByCommunityIdAndUserId(communityId, userId)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isCommunityAdmin(Long communityId, Long userId) {
        return membershipRepository.findByCommunityIdAndUserId(communityId, userId)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE && m.getRole() == CommunityRole.ADMIN).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<CommunityMembership> getActiveMembershipsForUser(Long userId) {
        return membershipRepository.findByUserIdAndStatusWithCommunity(userId, MembershipStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<CommunityMembership> getMembershipsForCommunity(Long communityId) {
        return membershipRepository.findByCommunityIdWithUser(communityId);
    }

    @Transactional(readOnly = true)
    public CommunityMembership getMembership(Long communityId, Long userId) {
        return membershipRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Membership not found: user=" + userId + " community=" + communityId));
    }

    @Transactional(readOnly = true)
    public long countActiveMembers(Long communityId) {
        return membershipRepository.countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<CommunityMembership> getAllMembershipsForUser(Long userId) {
        return membershipRepository.findByUserIdWithCommunity(userId);
    }

    @Transactional
    public CommunityMembership requestJoin(Long communityId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getRole() == com.worldcup.prediction.domain.enums.UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Super admin cannot join communities");
        }
        Optional<CommunityMembership> existing = membershipRepository.findByCommunityIdAndUserId(communityId, userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Community community = findById(communityId);
        CommunityMembership membership = CommunityMembership.builder()
                .community(community).user(user)
                .role(CommunityRole.MEMBER)
                .status(MembershipStatus.PENDING)
                .build();
        log.info("User {} requested to join community {}", userId, communityId);
        return membershipRepository.save(membership);
    }

    @Transactional
    public void removeMember(Long communityId, Long userId) {
        membershipRepository.findByCommunityIdAndUserId(communityId, userId).ifPresent(m -> {
            membershipRepository.deleteById(m.getId());
            log.info("Removed user {} from community {}", userId, communityId);
        });
    }

    @Transactional
    public CommunityMembership addOrActivateMember(Long communityId, Long userId, CommunityRole role) {
        userRepository.findById(userId).ifPresent(u -> {
            if (u.getRole() == com.worldcup.prediction.domain.enums.UserRole.SUPER_ADMIN) {
                throw new IllegalArgumentException("Super admin cannot be added to communities");
            }
        });
        Optional<CommunityMembership> existing = membershipRepository.findByCommunityIdAndUserId(communityId, userId);
        if (existing.isPresent()) {
            CommunityMembership m = existing.get();
            m.setStatus(MembershipStatus.ACTIVE);
            m.setRole(role);
            log.info("Updated membership: user={} community={} role={}", userId, communityId, role);
            return membershipRepository.save(m);
        }
        return addMember(communityId, userId, role, MembershipStatus.ACTIVE);
    }
}
