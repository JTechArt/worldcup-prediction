package com.worldcup.prediction.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "invitations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_id", nullable = false)
    private User invitedBy;

    @Column(name = "invited_at", nullable = false)
    @Builder.Default
    private LocalDateTime invitedAt = LocalDateTime.now();

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    public boolean isAccepted() {
        return acceptedAt != null;
    }
}
