package dev.fos.service;

import dev.fos.repo.DrillLogRepository;
import dev.fos.web.dto.ActivityDtos;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta a visão de streak a partir do log de drills. */
@Service
public class StreakQueryService {

    /** Meta declarada no critério de sucesso do MVP: 12 dias com registro em 30. */
    static final int TARGET_ACTIVE_DAYS_30 = 12;

    private static final int WINDOW_DAYS = 30;

    private final DrillLogRepository drillLogRepository;
    private final StreakService streakService;

    public StreakQueryService(DrillLogRepository drillLogRepository, StreakService streakService) {
        this.drillLogRepository = drillLogRepository;
        this.streakService = streakService;
    }

    @Transactional(readOnly = true)
    public ActivityDtos.StreakView streak(Long userId, LocalDate today) {
        List<LocalDate> days = drillLogRepository.findDistinctDrillDates(userId);
        return new ActivityDtos.StreakView(
                streakService.currentStreak(days, today),
                streakService.longestStreak(days),
                days.contains(today),
                streakService.activeDaysInWindow(days, today, WINDOW_DAYS),
                TARGET_ACTIVE_DAYS_30,
                today);
    }
}
