package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.dto.FixtureViewDto;
import com.worldcup.prediction.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final RoundWindowService roundWindowService;

    @Override
    public List<FixtureViewDto> getAllFixtures() {
        return matchRepository.findAllWithTeams().stream().map(this::toDto).toList();
    }

    @Override
    public List<FixtureViewDto> getGroupStageFixtures() {
        return matchRepository.findByStageWithTeams(MatchStage.GROUP).stream().map(this::toDto).toList();
    }

    @Override
    public List<FixtureViewDto> getKnockoutFixtures() {
        return matchRepository.findAllWithTeams().stream()
                .filter(m -> m.getStage() != MatchStage.GROUP)
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<FixtureViewDto> getTodayFixtures() {
        LocalDate today = LocalDate.now();
        return matchRepository.findAllWithTeams().stream()
                .filter(m -> m.getKickoffTime().toLocalDate().equals(today))
                .map(this::toDto)
                .toList();
    }

    @Override
    public FixtureViewDto getNextPredictableMatch(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return matchRepository.findAllWithTeams().stream()
                .filter(m -> roundWindowService.isRoundOpen(m.getRoundLabel(), now))
                .findFirst()
                .map(this::toDto)
                .orElse(null);
    }

    @Override
    public int getOpenMatchCount() {
        LocalDateTime now = LocalDateTime.now();
        return (int) matchRepository.findAllWithTeams().stream()
                .filter(m -> roundWindowService.isRoundOpen(m.getRoundLabel(), now))
                .count();
    }

    private FixtureViewDto toDto(Match m) {
        FixtureViewDto dto = new FixtureViewDto();
        dto.setId(m.getId());
        dto.setPhase(m.getStage().getDisplayName());
        dto.setGroupLabel(m.getGroup() != null ? "Group " + m.getGroup().getName() : null);
        dto.setKickoff(m.getKickoffTime());
        dto.setVenue(m.getVenue());
        dto.setCity(m.getCity());

        if (m.getHomeTeam() != null) {
            dto.setHomeTeamId(m.getHomeTeam().getId());
            dto.setHomeTeamName(m.getHomeTeam().getName());
            dto.setHomeTeamCode(m.getHomeTeam().getFlagCode());
        } else {
            dto.setHomeTeamName(m.getHomeTeamPlaceholder() != null ? m.getHomeTeamPlaceholder() : "TBD");
        }

        if (m.getAwayTeam() != null) {
            dto.setAwayTeamId(m.getAwayTeam().getId());
            dto.setAwayTeamName(m.getAwayTeam().getName());
            dto.setAwayTeamCode(m.getAwayTeam().getFlagCode());
        } else {
            dto.setAwayTeamName(m.getAwayTeamPlaceholder() != null ? m.getAwayTeamPlaceholder() : "TBD");
        }

        dto.setHomeScore(m.getHomeScore());
        dto.setAwayScore(m.getAwayScore());
        dto.setHomeScore90(m.getHomeScore90());
        dto.setAwayScore90(m.getAwayScore90());
        dto.setKnockout(m.isKnockout());
        dto.setStatus(m.getStatus().name());

        return dto;
    }
}
