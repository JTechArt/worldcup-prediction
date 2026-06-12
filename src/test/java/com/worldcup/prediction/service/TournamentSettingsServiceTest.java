package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.TournamentSettings;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.TournamentSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class TournamentSettingsServiceTest {

    @Mock TournamentSettingsRepository settingsRepository;
    @Mock CommunityRepository communityRepository;
    @InjectMocks TournamentSettingsService service;

    private TournamentSettings defaultSettings;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        defaultSettings = TournamentSettings.builder()
                .id(1L).windowMode(WindowMode.ROUND).dailyWindowCloseOffsetMinutes(30).build();
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(defaultSettings));
    }

    @Test
    void getEffectiveMode_returnsCommunityOverrideWhenSet() {
        Community c = new Community();
        c.setWindowModeOverride(WindowMode.DAILY);
        when(communityRepository.findById(5L)).thenReturn(Optional.of(c));

        assertThat(service.getEffectiveMode(5L)).isEqualTo(WindowMode.DAILY);
    }

    @Test
    void getEffectiveMode_fallsBackToGlobalWhenNoOverride() {
        Community c = new Community();
        c.setWindowModeOverride(null);
        when(communityRepository.findById(5L)).thenReturn(Optional.of(c));

        assertThat(service.getEffectiveMode(5L)).isEqualTo(WindowMode.ROUND);
    }

    @Test
    void getEffectiveMode_returnsGlobalWhenCommunityIdNull() {
        assertThat(service.getEffectiveMode(null)).isEqualTo(WindowMode.ROUND);
        verifyNoInteractions(communityRepository);
    }

    @Test
    void updateMode_savesNewMode() {
        when(settingsRepository.save(any())).thenReturn(defaultSettings);
        service.updateMode(WindowMode.DAILY);
        verify(settingsRepository).save(argThat(s -> s.getWindowMode() == WindowMode.DAILY));
    }

    @Test
    void updateCloseOffset_savesNewOffset() {
        when(settingsRepository.save(any())).thenReturn(defaultSettings);
        service.updateCloseOffset(45);
        verify(settingsRepository).save(argThat(s -> s.getDailyWindowCloseOffsetMinutes() == 45));
    }

    @Test
    void getSettings_createsDefaultIfMissing() {
        when(settingsRepository.findById(1L)).thenReturn(Optional.empty());
        when(settingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TournamentSettings result = service.getSettings();

        assertThat(result.getWindowMode()).isEqualTo(WindowMode.ROUND);
        assertThat(result.getDailyWindowCloseOffsetMinutes()).isEqualTo(30);
    }
}
