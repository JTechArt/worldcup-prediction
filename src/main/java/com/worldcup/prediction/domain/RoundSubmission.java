package com.worldcup.prediction.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

// Stores scalar FK ids intentionally (no @ManyToOne) — only existence checks and
// community-scoped list queries are needed; eager/lazy association loading is unnecessary.
@Entity
@Table(name = "round_submissions",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_round_submissions",
               columnNames = {"user_id", "community_id", "round_label"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RoundSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    @Column(name = "round_label", nullable = false, length = 50)
    private String roundLabel;

    @Column(name = "prediction_window_id")
    private Long predictionWindowId;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
}
