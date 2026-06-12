package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionWindowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PredictionWindowService {

    private final PredictionWindowRepository windowRepository;
    private final MatchRepository matchRepository;
    private final TournamentSettingsService tournamentSettingsService;

    // ---- Preview ----

    public record WindowPreview(
            List<Match> includedMatches,
            Optional<Match> prevMatch,
            Optional<Match> nextMatch) {}

    public WindowPreview generatePreview(LocalDateTime from, LocalDateTime to) {
        List<Match> included = matchRepository.findByKickoffTimeBetweenOrderByKickoffTimeAsc(from, to);
        Optional<Match> prev = matchRepository.findFirstByKickoffTimeLessThanOrderByKickoffTimeDesc(from);
        Optional<Match> next = matchRepository.findFirstByKickoffTimeGreaterThanOrderByKickoffTimeAsc(to);
        return new WindowPreview(included, prev, next);
    }

    // ---- CRUD ----

    public List<PredictionWindow> findAllGlobal() {
        return windowRepository.findByCommunityIdIsNullOrderByOpenAtAsc();
    }

    public List<PredictionWindow> findByCommunity(Long communityId) {
        return windowRepository.findByCommunityIdOrderByOpenAtAsc(communityId);
    }

    public PredictionWindow findById(Long id) {
        return windowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PredictionWindow not found: " + id));
    }

    @Transactional
    public PredictionWindow save(PredictionWindow window) {
        return windowRepository.save(window);
    }

    @Transactional
    public void delete(Long id) {
        PredictionWindow w = findById(id);
        if (w.getStatus() == PredictionWindowStatus.OPEN) {
            throw new IllegalStateException("Cannot delete an OPEN window — close it first.");
        }
        windowRepository.delete(w);
    }

    // ---- Lifecycle ----

    @Transactional
    public PredictionWindow publish(Long id) {
        PredictionWindow w = findById(id);
        if (w.getStatus() != PredictionWindowStatus.DRAFT) {
            throw new IllegalStateException(
                    "Can only publish DRAFT windows — only DRAFT windows may be published; current status: " + w.getStatus());
        }
        w.setStatus(PredictionWindowStatus.SCHEDULED);
        return windowRepository.save(w);
    }

    @Transactional
    public PredictionWindow activateWindow(Long id) {
        PredictionWindow w = findById(id);
        w.setEffectiveCloseAt(computeEffectiveCloseAt(w));
        w.setStatus(PredictionWindowStatus.OPEN);
        return windowRepository.save(w);
    }

    @Transactional
    public PredictionWindow closeWindow(Long id) {
        PredictionWindow w = findById(id);
        w.setStatus(PredictionWindowStatus.CLOSED);
        return windowRepository.save(w);
    }

    @Transactional
    public PredictionWindow forceOpen(Long id) {
        PredictionWindow w = findById(id);
        w.setOverrideStatus(RoundOverrideStatus.FORCE_OPEN);
        return windowRepository.save(w);
    }

    @Transactional
    public PredictionWindow forceClose(Long id) {
        PredictionWindow w = findById(id);
        w.setOverrideStatus(RoundOverrideStatus.FORCE_CLOSED);
        return windowRepository.save(w);
    }

    @Transactional
    public PredictionWindow resetOverride(Long id) {
        PredictionWindow w = findById(id);
        w.setOverrideStatus(null);
        return windowRepository.save(w);
    }

    // ---- isWindowOpen ----

    public boolean isWindowOpen(Match match, LocalDateTime now, Long communityId) {
        PredictionWindow window = findEffectiveWindow(match, communityId);
        if (window == null) return false;
        if (window.getOverrideStatus() == RoundOverrideStatus.FORCE_OPEN) return true;
        if (window.getOverrideStatus() == RoundOverrideStatus.FORCE_CLOSED) return false;
        if (window.getStatus() != PredictionWindowStatus.OPEN) return false;
        if (window.getEffectiveCloseAt() == null) return false;
        return !now.isBefore(window.getOpenAt()) && now.isBefore(window.getEffectiveCloseAt());
    }

    public PredictionWindow findEffectiveWindow(Match match, Long communityId) {
        if (communityId != null) {
            Optional<PredictionWindow> communityForceOpen =
                    windowRepository.findForceOpenCommunityWindowForMatch(match.getId(), communityId);
            if (communityForceOpen.isPresent()) return communityForceOpen.get();

            Optional<PredictionWindow> communityOpen =
                    windowRepository.findOpenCommunityWindowForMatch(match.getId(), communityId);
            if (communityOpen.isPresent()) return communityOpen.get();
        }

        Optional<PredictionWindow> globalForceOpen =
                windowRepository.findForceOpenGlobalWindowForMatch(match.getId());
        if (globalForceOpen.isPresent()) return globalForceOpen.get();

        return windowRepository.findOpenGlobalWindowForMatch(match.getId()).orElse(null);
    }

    // ---- Scheduler support ----

    public List<PredictionWindow> findScheduledReadyToActivate(LocalDateTime now) {
        return windowRepository.findByStatusAndOpenAtLessThanEqual(PredictionWindowStatus.SCHEDULED, now);
    }

    public List<PredictionWindow> findExpiredOpenWindows(LocalDateTime now) {
        return windowRepository.findExpiredOpenWindows(PredictionWindowStatus.OPEN, now);
    }

    // ---- Private helpers ----

    private LocalDateTime computeEffectiveCloseAt(PredictionWindow w) {
        if (w.getCloseAt() != null) return w.getCloseAt();
        int offset = tournamentSettingsService.getSettings().getDailyWindowCloseOffsetMinutes();
        return w.getMatches().stream()
                .map(Match::getKickoffTime)
                .min(LocalDateTime::compareTo)
                .map(min -> min.minusMinutes(offset))
                .orElseThrow(() -> new IllegalStateException(
                        "Window has no matches — cannot compute effective close time."));
    }
}
