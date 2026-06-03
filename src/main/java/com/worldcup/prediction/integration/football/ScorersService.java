package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.integration.football.dto.FootballApiScorersResponseDto;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ScorersService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;

    public SyncResult syncScorers() {
        long recentCompletions = matchRepository.countByStatusAndUpdatedAtAfter(
                MatchStatus.COMPLETED, LocalDateTime.now().minusHours(24));
        if (recentCompletions == 0) {
            return SyncResult.skipped("No matches completed in last 24h");
        }

        FootballApiScorersResponseDto response = rateLimiter.call(client::fetchTopScorers);
        if (response == null || response.scorers() == null) {
            return SyncResult.skipped("No API response");
        }

        int updated = 0;
        for (var entry : response.scorers()) {
            if (entry.player() == null || entry.player().id() == null) continue;
            var playerOpt = playerRepository.findByExternalId(entry.player().id());
            if (playerOpt.isPresent()) {
                playerOpt.get().setTournamentGoals(entry.goals() != null ? entry.goals() : 0);
                playerRepository.save(playerOpt.get());
                updated++;
            }
        }

        return SyncResult.success(updated + " scorer records updated");
    }
}
