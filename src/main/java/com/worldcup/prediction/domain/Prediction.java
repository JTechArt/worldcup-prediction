package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.PlayoffWinnerPick;
import com.worldcup.prediction.domain.enums.PredictionScore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "predictions",
       uniqueConstraints = @UniqueConstraint(
               name = "predictions_user_match_community_idx",
               columnNames = {"user_id", "match_id", "community_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "match"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(name = "predicted_home", nullable = false)
    private int predictedHome;

    @Column(name = "predicted_away", nullable = false)
    private int predictedAway;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_result", nullable = false, length = 50)
    @Builder.Default
    private PredictionScore scoreResult = PredictionScore.PENDING;

    @Column(name = "points_awarded", nullable = false)
    @Builder.Default
    private int pointsAwarded = 0;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "edited_by_admin", nullable = false)
    @Builder.Default
    private boolean editedByAdmin = false;

    @Column(name = "admin_edit_note", length = 500)
    private String adminEditNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "predicted_playoff_winner", length = 5)
    private PlayoffWinnerPick predictedPlayoffWinner;

    public boolean isPredictedDraw() {
        return predictedHome == predictedAway;
    }
}
