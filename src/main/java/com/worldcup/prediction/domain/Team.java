package com.worldcup.prediction.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "short_name", length = 50)
    private String shortName;

    @Column(name = "fifa_code", nullable = false, length = 3, unique = true)
    private String fifaCode;

    @Column(name = "flag_code", nullable = false, length = 10)
    private String flagCode;

    @Column(name = "confederation", length = 20)
    private String confederation;

    @Column(name = "external_id")
    private Long externalId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public String getFlagUrl() {
        return "/images/flags/" + flagCode + ".svg";
    }
}
