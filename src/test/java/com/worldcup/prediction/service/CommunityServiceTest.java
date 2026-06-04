package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;

    private CommunityService communityService;
    private User superAdmin;
    private User regularUser;

    @BeforeEach
    void setUp() {
        communityService = new CommunityService(communityRepository, membershipRepository, userRepository);
        superAdmin = User.builder().id(1L).email("admin@test.com")
                .firstName("Super").lastName("Admin")
                .role(UserRole.SUPER_ADMIN).status(UserStatus.ACTIVE).build();
        regularUser = User.builder().id(2L).email("user@test.com")
                .firstName("Regular").lastName("User")
                .role(UserRole.USER).status(UserStatus.ACTIVE).build();
    }

    @Nested @DisplayName("createCommunity")
    class CreateCommunity {
        @Test @DisplayName("creates community with valid name and slug")
        void createsCommunity() {
            when(communityRepository.existsBySlug("acme-corp")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(superAdmin));
            when(communityRepository.save(any(Community.class))).thenAnswer(inv -> {
                Community c = inv.getArgument(0);
                c.setId(10L);
                return c;
            });
            Community result = communityService.createCommunity("Acme Corp", "acme-corp", "A company group", 1L);
            assertThat(result.getName()).isEqualTo("Acme Corp");
            assertThat(result.getSlug()).isEqualTo("acme-corp");
            verify(communityRepository).save(any(Community.class));
        }

        @Test @DisplayName("throws when slug already exists")
        void throwsOnDuplicateSlug() {
            when(communityRepository.existsBySlug("acme-corp")).thenReturn(true);
            assertThatThrownBy(() -> communityService.createCommunity("Acme Corp", "acme-corp", null, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("slug");
        }
    }

    @Nested @DisplayName("addMember")
    class AddMember {
        @Test @DisplayName("adds member with PENDING status")
        void addsMember() {
            Community community = Community.builder().id(10L).name("Test").slug("test").build();
            when(communityRepository.findById(10L)).thenReturn(Optional.of(community));
            when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
            when(membershipRepository.existsByCommunityIdAndUserId(10L, 2L)).thenReturn(false);
            when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            CommunityMembership result = communityService.addMember(10L, 2L, CommunityRole.MEMBER, MembershipStatus.PENDING);
            assertThat(result.getRole()).isEqualTo(CommunityRole.MEMBER);
            assertThat(result.getStatus()).isEqualTo(MembershipStatus.PENDING);
        }

        @Test @DisplayName("throws when already a member")
        void throwsOnDuplicate() {
            when(communityRepository.findById(10L)).thenReturn(Optional.of(Community.builder().id(10L).build()));
            when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
            when(membershipRepository.existsByCommunityIdAndUserId(10L, 2L)).thenReturn(true);
            assertThatThrownBy(() -> communityService.addMember(10L, 2L, CommunityRole.MEMBER, MembershipStatus.PENDING))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already a member");
        }
    }

    @Nested @DisplayName("findBySlug")
    class FindBySlug {
        @Test @DisplayName("returns community when slug exists")
        void returnsCommunity() {
            Community c = Community.builder().id(10L).slug("test").build();
            when(communityRepository.findBySlug("test")).thenReturn(Optional.of(c));
            assertThat(communityService.findBySlug("test")).isPresent();
        }

        @Test @DisplayName("returns empty when slug not found")
        void returnsEmpty() {
            when(communityRepository.findBySlug("nope")).thenReturn(Optional.empty());
            assertThat(communityService.findBySlug("nope")).isEmpty();
        }
    }

    @Nested @DisplayName("isActiveMember")
    class IsMember {
        @Test @DisplayName("returns true for active member")
        void activeReturnsTrue() {
            CommunityMembership m = CommunityMembership.builder().status(MembershipStatus.ACTIVE).build();
            when(membershipRepository.findByCommunityIdAndUserId(10L, 2L)).thenReturn(Optional.of(m));
            assertThat(communityService.isActiveMember(10L, 2L)).isTrue();
        }

        @Test @DisplayName("returns false for pending member")
        void pendingReturnsFalse() {
            CommunityMembership m = CommunityMembership.builder().status(MembershipStatus.PENDING).build();
            when(membershipRepository.findByCommunityIdAndUserId(10L, 2L)).thenReturn(Optional.of(m));
            assertThat(communityService.isActiveMember(10L, 2L)).isFalse();
        }
    }
}
