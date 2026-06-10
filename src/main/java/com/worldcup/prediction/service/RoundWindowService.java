package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.RoundWindowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoundWindowService {

    private final RoundWindowRepository roundWindowRepository;
    private final MatchRepository matchRepository;

    public boolean isRoundOpen(String roundLabel, LocalDateTime now) {
        Optional<RoundWindow> opt = roundWindowRepository.findByRoundLabel(roundLabel);
        if (opt.isEmpty()) return false;
        RoundWindow rw = opt.get();
        if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_OPEN) return true;
        if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_CLOSED) return false;
        if (rw.getAutoOpensAt() == null || rw.getAutoClosesAt() == null) return false;
        return !now.isBefore(rw.getAutoOpensAt()) && now.isBefore(rw.getAutoClosesAt());
    }

    @Transactional
    public RoundWindow openRound(String roundLabel) {
        RoundWindow rw = roundWindowRepository.findByRoundLabel(roundLabel)
                .orElseGet(() -> RoundWindow.builder().roundLabel(roundLabel).build());
        rw.setOverrideStatus(RoundOverrideStatus.FORCE_OPEN);
        return roundWindowRepository.save(rw);
    }

    @Transactional
    public RoundWindow closeRound(String roundLabel) {
        RoundWindow rw = findOrThrow(roundLabel);
        rw.setOverrideStatus(RoundOverrideStatus.FORCE_CLOSED);
        return roundWindowRepository.save(rw);
    }

    @Transactional
    public RoundWindow resetOverride(String roundLabel) {
        RoundWindow rw = findOrThrow(roundLabel);
        rw.setOverrideStatus(null);
        return roundWindowRepository.save(rw);
    }

    public List<RoundWindow> findAll() {
        return roundWindowRepository.findAllOrderByAutoOpensAtAsc();
    }

    public Optional<RoundWindow> findByRoundLabel(String roundLabel) {
        return roundWindowRepository.findByRoundLabel(roundLabel);
    }

    /**
     * Returns true if the tournament winner prediction can still be changed.
     * Locked once the first matchday window closes (autoClosesAt of "Matchday 1" has passed,
     * or the window is FORCE_CLOSED).
     */
    public boolean isWinnerChangeable(LocalDateTime now) {
        return roundWindowRepository.findByRoundLabel("Matchday 1")
                .map(rw -> {
                    if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_CLOSED) return false;
                    if (rw.getAutoClosesAt() == null) return true;
                    return now.isBefore(rw.getAutoClosesAt());
                })
                .orElse(true);
    }

    @Transactional
    public void recalculateAutoTimes(String roundLabel) {
        RoundWindow rw = findOrThrow(roundLabel);
        var matches = matchRepository.findByRoundLabelWithTeams(roundLabel);
        if (matches.isEmpty()) return;
        LocalDateTime firstKickoff = matches.stream()
                .map(m -> m.getKickoffTime())
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime lastKickoff = matches.stream()
                .map(m -> m.getKickoffTime())
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (firstKickoff != null) rw.setAutoOpensAt(firstKickoff.minusHours(24));
        if (lastKickoff != null) rw.setAutoClosesAt(lastKickoff.minusHours(1));
        roundWindowRepository.save(rw);
    }

    private RoundWindow findOrThrow(String roundLabel) {
        return roundWindowRepository.findByRoundLabel(roundLabel)
                .orElseThrow(() -> new IllegalArgumentException("Round window not found: " + roundLabel));
    }
}
