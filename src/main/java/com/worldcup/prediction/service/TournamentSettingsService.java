package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.TournamentSettings;
import com.worldcup.prediction.domain.enums.KnockoutScoringMode;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.TournamentSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TournamentSettingsService {

    private final TournamentSettingsRepository settingsRepository;
    private final CommunityRepository communityRepository;

    @Transactional
    public TournamentSettings getSettings() {
        return settingsRepository.findById(1L)
                .orElseGet(() -> settingsRepository.save(
                        TournamentSettings.builder()
                                .id(1L)
                                .windowMode(WindowMode.ROUND)
                                .dailyWindowCloseOffsetMinutes(30)
                                .roundLockOffsetMinutes(60)
                                .knockoutScoringMode(KnockoutScoringMode.NINETY_MINUTES)
                                .build()));
    }

    public WindowMode getEffectiveMode(Long communityId) {
        if (communityId != null) {
            WindowMode override = communityRepository.findById(communityId)
                    .map(c -> c.getWindowModeOverride())
                    .orElse(null);
            if (override != null) return override;
        }
        return getSettings().getWindowMode();
    }

    @Transactional
    public TournamentSettings updateMode(WindowMode mode) {
        TournamentSettings s = getSettings();
        s.setWindowMode(mode);
        return settingsRepository.save(s);
    }

    @Transactional
    public TournamentSettings updateCloseOffset(int minutes) {
        TournamentSettings s = getSettings();
        s.setDailyWindowCloseOffsetMinutes(minutes);
        return settingsRepository.save(s);
    }

    @Transactional
    public TournamentSettings updateRoundLockOffset(int minutes) {
        TournamentSettings s = getSettings();
        s.setRoundLockOffsetMinutes(minutes);
        return settingsRepository.save(s);
    }

    public KnockoutScoringMode getKnockoutScoringMode() {
        return getSettings().getKnockoutScoringMode();
    }

    @Transactional
    public TournamentSettings updateKnockoutScoringMode(KnockoutScoringMode mode) {
        TournamentSettings settings = getSettings();
        settings.setKnockoutScoringMode(mode);
        return settingsRepository.save(settings);
    }
}
