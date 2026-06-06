package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "round_windows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RoundWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "round_label", nullable = false, unique = true, length = 50)
    private String roundLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_status", length = 20)
    private RoundOverrideStatus overrideStatus;

    @Column(name = "auto_opens_at")
    private LocalDateTime autoOpensAt;

    @Column(name = "auto_closes_at")
    private LocalDateTime autoClosesAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
