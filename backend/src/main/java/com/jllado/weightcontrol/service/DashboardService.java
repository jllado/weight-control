package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.DashboardDtos;
import com.jllado.weightcontrol.api.dto.DashboardDtos.DailyStatusResponse;
import com.jllado.weightcontrol.api.dto.DashboardDtos.WeekStatusResponse;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class DashboardService {

    private final DailyStatusSnapshotService snapshotService;
    private final UserRepository userRepository;

    public DashboardService(DailyStatusSnapshotService snapshotService, UserRepository userRepository) {
        this.snapshotService = snapshotService;
        this.userRepository = userRepository;
    }

    public DashboardDtos.DashboardResponse getDashboard(User user) {
        LocalDate anchorDate = requireAnchorDate(user);
        DailyStatus dailyStatus = snapshotService.getOrBuild(user, anchorDate);
        DailyStatus lastWeek = snapshotService.getLastWeekDailyStatus(user, anchorDate);
        return new DashboardDtos.DashboardResponse(
            anchorDate,
            DailyStatusResponse.from(dailyStatus),
            DailyStatusResponse.from(lastWeek),
            toWeek(snapshotService.getWeek(user, anchorDate)),
            toWeek(snapshotService.getWeek(user, anchorDate.minusDays(7)))
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

    public DashboardDtos.DashboardResponse refresh(User user) {
        snapshotService.rebuild(user, requireAnchorDate(user));
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

    private WeekStatusResponse toWeek(List<DailyStatus> statuses) {
        DailyStatusResponse saturday = statuses.size() > 0 ? DailyStatusResponse.from(statuses.get(0)) : null;
        DailyStatusResponse sunday = statuses.size() > 1 ? DailyStatusResponse.from(statuses.get(1)) : null;
        DailyStatusResponse monday = statuses.size() > 2 ? DailyStatusResponse.from(statuses.get(2)) : null;
        DailyStatusResponse tuesday = statuses.size() > 3 ? DailyStatusResponse.from(statuses.get(3)) : null;
        DailyStatusResponse wednesday = statuses.size() > 4 ? DailyStatusResponse.from(statuses.get(4)) : null;
        DailyStatusResponse thursday = statuses.size() > 5 ? DailyStatusResponse.from(statuses.get(5)) : null;
        DailyStatusResponse friday = statuses.size() > 6 ? DailyStatusResponse.from(statuses.get(6)) : null;
        return new WeekStatusResponse(
            saturday, sunday, monday, tuesday, wednesday, thursday, friday,
            average(statuses, DailyStatus::getRoutinesPercentage),
            average(statuses, DailyStatus::getWeightPercentage),
            average(statuses, DailyStatus::getBloodPressurePercentage),
            average(statuses, DailyStatus::getFlexibilityPercentage),
            average(statuses, DailyStatus::getMindPercentage)
        );
    }

    private BigDecimal average(List<DailyStatus> statuses, java.util.function.Function<DailyStatus, BigDecimal> extractor) {
        if (statuses.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return statuses.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(statuses.size()), 2, RoundingMode.HALF_UP);
    }
}
