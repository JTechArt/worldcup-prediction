package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.WindowMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tournament_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TournamentSettings {

    @Id
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "window_mode", nullable = false, length = 10)
    private WindowMode windowMode;

    @Column(name = "daily_window_close_offset_minutes", nullable = false)
    private int dailyWindowCloseOffsetMinutes;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
