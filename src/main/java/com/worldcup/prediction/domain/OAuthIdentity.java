package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.OAuthProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_identities",
       uniqueConstraints = @UniqueConstraint(
               name = "oauth_identities_provider_subject_idx",
               columnNames = {"provider", "provider_subject"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OAuthProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
