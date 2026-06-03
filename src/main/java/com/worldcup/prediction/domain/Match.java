package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "predictions")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "external_id", length = 50)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MatchStage stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(name = "match_number", nullable = false, unique = true)
    private int matchNumber;

    @Column(name = "round_label", length = 50)
    private String roundLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    @Column(name = "home_team_placeholder", length = 100)
    private String homeTeamPlaceholder;

    @Column(name = "away_team_placeholder", length = 100)
    private String awayTeamPlaceholder;

    @Column(name = "kickoff_time", nullable = false)
    private LocalDateTime kickoffTime;

    @Column(length = 200)
    private String venue;

    @Column(length = 100)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private MatchStatus status = MatchStatus.SCHEDULED;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(name = "home_score_90")
    private Integer homeScore90;

    @Column(name = "away_score_90")
    private Integer awayScore90;

    @Column(name = "lineup_fetched", nullable = false)
    @Builder.Default
    private boolean lineupFetched = false;

    @Column(name = "prediction_window_open", nullable = false)
    @Builder.Default
    private boolean predictionWindowOpen = false;

    @Column(name = "prediction_window_opens_at")
    private LocalDateTime predictionWindowOpensAt;

    @Column(name = "prediction_window_closes_at")
    private LocalDateTime predictionWindowClosesAt;

    @Column(name = "result_entered_at")
    private LocalDateTime resultEnteredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_entered_by_id")
    private User resultEnteredBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Prediction> predictions = new ArrayList<>();

    public boolean isCompleted() {
        return status == MatchStatus.COMPLETED;
    }

    public boolean isGroupStage() {
        return stage == MatchStage.GROUP;
    }

    /**
     * For knockout matches, uses the 90-minute score for prediction scoring (extra time/pens ignored).
     */
    public Integer getEffectiveHomeScore() {
        if (stage != MatchStage.GROUP && homeScore90 != null) {
            return homeScore90;
        }
        return homeScore;
    }

    public Integer getEffectiveAwayScore() {
        if (stage != MatchStage.GROUP && awayScore90 != null) {
            return awayScore90;
        }
        return awayScore;
    }
}
