package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "prediction_window")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PredictionWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    @Column(name = "close_at")
    private LocalDateTime closeAt;

    @Column(name = "effective_close_at")
    private LocalDateTime effectiveCloseAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_status", length = 20)
    private RoundOverrideStatus overrideStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PredictionWindowStatus status = PredictionWindowStatus.DRAFT;

    @Column(name = "community_id")
    private Long communityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "prediction_window_match",
            joinColumns = @JoinColumn(name = "window_id"),
            inverseJoinColumns = @JoinColumn(name = "match_id"))
    @Builder.Default
    private Set<Match> matches = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
