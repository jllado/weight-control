package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.DashboardDtos;
import com.jllado.weightcontrol.api.dto.DashboardDtos.DailyStatusResponse;
import com.jllado.weightcontrol.api.dto.DashboardDtos.WeekStatusResponse;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class DashboardService {

    private final DailyStatusSnapshotService snapshotService;
    private final UserRepository userRepository;
    private final MoodService moodService;

    public DashboardService(DailyStatusSnapshotService snapshotService, UserRepository userRepository, MoodService moodService) {
        this.snapshotService = snapshotService;
        this.userRepository = userRepository;
        this.moodService = moodService;
    }

    public DashboardDtos.DashboardResponse getDashboard(User user) {
        LocalDate anchorDate = requireAnchorDate(user);
        DailyStatus dailyStatus = snapshotService.getOrBuild(user, anchorDate);
        DailyStatus lastWeek = snapshotService.getLastWeekDailyStatus(user, anchorDate);
        Map<LocalDate, Mood> moods = moodService.findByDateRange(user, anchorDate.minusDays(37), anchorDate);
        return new DashboardDtos.DashboardResponse(
            anchorDate,
            user.getLastCompletedDashboardDate(),
            toDailyStatusResponse(dailyStatus, moods),
            toDailyStatusResponse(lastWeek, moods),
            toWeek(user, snapshotService.getWeek(user, anchorDate), moods),
            toWeek(user, snapshotService.getWeek(user, anchorDate.minusDays(7)), moods)
        );
    }

    public DashboardDtos.DashboardResponse advance(User user) {
        LocalDate today = LocalDate.now(com.jllado.weightcontrol.util.DateTimes.USER_ZONE);
        LocalDate anchorDate = requireAnchorDate(user);
        if (anchorDate.isBefore(today)) {
            user.setDashboardAnchorDate(anchorDate.plusDays(1));
            userRepository.save(user);
        }
        snapshotService.getOrBuild(user, user.getDashboardAnchorDate());
        return getDashboard(user);
    }

    public DashboardDtos.DashboardResponse retreat(User user) {
        LocalDate anchorDate = requireAnchorDate(user);
        user.setDashboardAnchorDate(anchorDate.minusDays(1));
        userRepository.save(user);
        snapshotService.getOrBuild(user, user.getDashboardAnchorDate());
        return getDashboard(user);
    }

    public DashboardDtos.DashboardResponse refresh(User user) {
        snapshotService.rebuild(user, requireAnchorDate(user));
        return getDashboard(user);
    }

    public DashboardDtos.DashboardResponse setDashboardCompletion(User user, boolean completed) {
        LocalDate anchorDate = requireAnchorDate(user);
        if (completed) {
            user.setLastCompletedDashboardDate(anchorDate);
        } else if (anchorDate.equals(user.getLastCompletedDashboardDate())) {
            user.setLastCompletedDashboardDate(anchorDate.minusDays(1));
        }
        userRepository.save(user);
        return getDashboard(user);
    }

    public void refreshCurrentStatus(User user) {
        if (user.getDashboardAnchorDate() != null) {
            snapshotService.rebuild(user, user.getDashboardAnchorDate());
        }
    }

    private LocalDate requireAnchorDate(User user) {
        if (user.getDashboardAnchorDate() == null) {
            throw new BadRequestException("Dashboard anchor date not set");
        }
        return user.getDashboardAnchorDate();
    }

    private DailyStatusResponse toDailyStatusResponse(DailyStatus status, Map<LocalDate, Mood> moods) {
        return DailyStatusResponse.from(
            status,
            moods.get(status.getStatusDate()),
            moodAverage(moods, status.getStatusDate().minusDays(29), status.getStatusDate())
        );
    }

    private WeekStatusResponse toWeek(User user, List<DailyStatus> statuses, Map<LocalDate, Mood> moods) {
        DailyStatusResponse saturday = statuses.size() > 0 ? toDailyStatusResponse(statuses.get(0), moods) : null;
        DailyStatusResponse sunday = statuses.size() > 1 ? toDailyStatusResponse(statuses.get(1), moods) : null;
        DailyStatusResponse monday = statuses.size() > 2 ? toDailyStatusResponse(statuses.get(2), moods) : null;
        DailyStatusResponse tuesday = statuses.size() > 3 ? toDailyStatusResponse(statuses.get(3), moods) : null;
        DailyStatusResponse wednesday = statuses.size() > 4 ? toDailyStatusResponse(statuses.get(4), moods) : null;
        DailyStatusResponse thursday = statuses.size() > 5 ? toDailyStatusResponse(statuses.get(5), moods) : null;
        DailyStatusResponse friday = statuses.size() > 6 ? toDailyStatusResponse(statuses.get(6), moods) : null;
        LocalDate weekStart = statuses.isEmpty() ? null : statuses.getFirst().getStatusDate();
        BigDecimal moodAverage = weekStart == null ? null : moodService.getAverage(user, weekStart, weekStart.plusDays(6));
        return new WeekStatusResponse(
            saturday, sunday, monday, tuesday, wednesday, thursday, friday,
            average(statuses, DailyStatus::getRoutinesPercentage),
            average(statuses, DailyStatus::getWeightPercentage),
            average(statuses, DailyStatus::getBloodPressurePercentage),
            average(statuses, DailyStatus::getFlexibilityPercentage),
            average(statuses, DailyStatus::getMindPercentage),
            moodAverage
        );
    }

    private BigDecimal moodAverage(Map<LocalDate, Mood> moods, LocalDate startDate, LocalDate endDate) {
        return moodService.average(moods.entrySet().stream()
            .filter(entry -> !entry.getKey().isBefore(startDate) && !entry.getKey().isAfter(endDate))
            .map(Map.Entry::getValue)
            .toList());
    }

    private BigDecimal average(List<DailyStatus> statuses, java.util.function.Function<DailyStatus, BigDecimal> extractor) {
        if (statuses.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return statuses.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(statuses.size()), 2, RoundingMode.HALF_UP);
    }
}
