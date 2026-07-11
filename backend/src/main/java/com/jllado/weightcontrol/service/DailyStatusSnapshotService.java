package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.util.Numbers;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class DailyStatusSnapshotService {

    private final DailyStatusRepository dailyStatusRepository;
    private final WeightRepository weightRepository;
    private final BloodPressureRepository bloodPressureRepository;
    private final RoutineRepository routineRepository;
    private final RoutineCheckinRepository routineCheckinRepository;

    public DailyStatusSnapshotService(
        DailyStatusRepository dailyStatusRepository,
        WeightRepository weightRepository,
        BloodPressureRepository bloodPressureRepository,
        RoutineRepository routineRepository,
        RoutineCheckinRepository routineCheckinRepository
    ) {
        this.dailyStatusRepository = dailyStatusRepository;
        this.weightRepository = weightRepository;
        this.bloodPressureRepository = bloodPressureRepository;
        this.routineRepository = routineRepository;
        this.routineCheckinRepository = routineCheckinRepository;
    }

    public DailyStatus getOrBuild(User user, LocalDate date) {
        return dailyStatusRepository.findByUserAndStatusDate(user, date).orElseGet(() -> rebuild(user, date));
    }

    public DailyStatus rebuild(User user, LocalDate date) {
        DailyStatus dailyStatus = dailyStatusRepository.findByUserAndStatusDate(user, date).orElseGet(DailyStatus::new);
        dailyStatus.setUser(user);
        dailyStatus.setStatusDate(date);

        OffsetDateTime dayStart = DateTimes.startOfDay(date);
        Weight weight = weightRepository.findFirstByUserAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(user, dayStart.plusDays(1)).orElse(null);
        BloodPressure bloodPressure = bloodPressureRepository.findFirstByUserAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(user, dayStart.plusDays(1)).orElse(null);
        List<Routine> routines = routineRepository.findByUserOrderByStartDateAsc(user).stream()
            .filter(routine -> !DateTimes.toLocalDate(routine.getStartDate()).isAfter(date))
            .toList();
        List<Routine> done = routines.stream().filter(routine -> isDone(routine, date)).toList();

        dailyStatus.setWeight(weight);
        dailyStatus.setBloodPressure(bloodPressure);
        dailyStatus.setTotalRoutines(routines.size());
        dailyStatus.setTotalWeightRoutines((int) routines.stream().filter(routine -> routine.getTypes().contains(RoutineType.WEIGHT)).count());
        dailyStatus.setTotalBloodPressureRoutines((int) routines.stream().filter(routine -> routine.getTypes().contains(RoutineType.BLOOD_PRESSURE)).count());
        dailyStatus.setTotalFlexibilityRoutines((int) routines.stream().filter(routine -> routine.getTypes().contains(RoutineType.FLEXIBILITY)).count());
        dailyStatus.setTotalMindRoutines((int) routines.stream().filter(routine -> routine.getTypes().contains(RoutineType.MIND)).count());
        dailyStatus.setRoutinesDone(done.size());
        dailyStatus.setWeightDone((int) done.stream().filter(routine -> routine.getTypes().contains(RoutineType.WEIGHT)).count());
        dailyStatus.setBloodPressureDone((int) done.stream().filter(routine -> routine.getTypes().contains(RoutineType.BLOOD_PRESSURE)).count());
        dailyStatus.setFlexibilityDone((int) done.stream().filter(routine -> routine.getTypes().contains(RoutineType.FLEXIBILITY)).count());
        dailyStatus.setMindDone((int) done.stream().filter(routine -> routine.getTypes().contains(RoutineType.MIND)).count());
        dailyStatus.setRoutinesPercentage(percentage(dailyStatus.getRoutinesDone(), dailyStatus.getTotalRoutines()));
        dailyStatus.setWeightPercentage(percentage(dailyStatus.getWeightDone(), dailyStatus.getTotalWeightRoutines()));
        dailyStatus.setBloodPressurePercentage(percentage(dailyStatus.getBloodPressureDone(), dailyStatus.getTotalBloodPressureRoutines()));
        dailyStatus.setFlexibilityPercentage(percentage(dailyStatus.getFlexibilityDone(), dailyStatus.getTotalFlexibilityRoutines()));
        dailyStatus.setMindPercentage(percentage(dailyStatus.getMindDone(), dailyStatus.getTotalMindRoutines()));

        BigDecimal routinesScore = score(routines, date.minusDays(1));
        BigDecimal weightScore = score(filterByType(routines, RoutineType.WEIGHT), date);
        BigDecimal bloodPressureScore = score(filterByType(routines, RoutineType.BLOOD_PRESSURE), date);
        BigDecimal flexibilityScore = score(filterByType(routines, RoutineType.FLEXIBILITY), date);
        BigDecimal mindScore = score(filterByType(routines, RoutineType.MIND), date);
        dailyStatus.setRoutinesScore(routinesScore);
        dailyStatus.setWeightScore(weightScore);
        dailyStatus.setBloodPressureScore(bloodPressureScore);
        dailyStatus.setFlexibilityScore(flexibilityScore);
        dailyStatus.setMindScore(mindScore);
        dailyStatus.setRoutinesStatus(percentage(routinesScore, dailyStatus.getTotalRoutines()));
        dailyStatus.setWeightStatus(percentage(weightScore, dailyStatus.getTotalWeightRoutines()));
        dailyStatus.setBloodPressureStatus(percentage(bloodPressureScore, dailyStatus.getTotalBloodPressureRoutines()));
        dailyStatus.setFlexibilityStatus(percentage(flexibilityScore, dailyStatus.getTotalFlexibilityRoutines()));
        dailyStatus.setMindStatus(percentage(mindScore, dailyStatus.getTotalMindRoutines()));

        return dailyStatusRepository.save(dailyStatus);
    }

    public List<DailyStatus> getWeek(User user, LocalDate anchorDate) {
        LocalDate start = getWeekStart(anchorDate);
        List<DailyStatus> statuses = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(anchorDate) && statuses.size() < 7) {
            statuses.add(getOrBuild(user, current));
            current = current.plusDays(1);
        }
        return statuses;
    }

    public List<DailyStatus> getFullWeek(User user, LocalDate anchorDate) {
        LocalDate start = getWeekStart(anchorDate);
        List<DailyStatus> statuses = new ArrayList<>();
        LocalDate current = start;
        while (statuses.size() < 7) {
            statuses.add(getOrBuild(user, current));
            current = current.plusDays(1);
        }
        return statuses;
    }

    public DailyStatus getLastWeekDailyStatus(User user, LocalDate currentDate) {
        return dailyStatusRepository.findFirstByUserAndStatusDateLessThanEqualOrderByStatusDateDesc(user, currentDate.minusDays(7))
            .orElseGet(() -> getOrBuild(user, currentDate.minusDays(7)));
    }

    private LocalDate getWeekStart(LocalDate date) {
        int effectiveDay = (date.getDayOfWeek().getValue() + 1) % 7;
        return date.minusDays(effectiveDay);
    }

    private boolean isDone(Routine routine, LocalDate date) {
        return routineCheckinRepository.findByRoutineOrderByCheckedAtAsc(routine).stream()
            .anyMatch(checkin -> DateTimes.toLocalDate(checkin.getCheckedAt()).isEqual(date));
    }

    private List<Routine> filterByType(List<Routine> routines, RoutineType type) {
        return routines.stream().filter(routine -> routine.getTypes().contains(type)).toList();
    }

    private BigDecimal score(List<Routine> routines, LocalDate date) {
        return Numbers.round(routines.stream()
            .map(routine -> routineScore(routine, date))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal routineScore(Routine routine, LocalDate date) {
        BigDecimal status = routineStatus(routine, date);
        if (status.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return BigDecimal.ONE;
        }
        if (status.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return BigDecimal.valueOf(0.75);
        }
        if (status.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return BigDecimal.valueOf(0.5);
        }
        if (status.compareTo(BigDecimal.valueOf(40)) >= 0) {
            return BigDecimal.valueOf(0.25);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal routineStatus(Routine routine, LocalDate date) {
        long days = Math.min(31, java.time.temporal.ChronoUnit.DAYS.between(DateTimes.toLocalDate(routine.getStartDate()), date.plusDays(1)));
        if (days <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        OffsetDateTime monthAgo = DateTimes.startOfDay(date).minusDays(31);
        long count = routineCheckinRepository.countByRoutineAndCheckedAtBetween(routine, monthAgo, DateTimes.startOfDay(date).plusDays(1));
        BigDecimal percentage = BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        return percentage.min(BigDecimal.valueOf(100));
    }

    private BigDecimal percentage(BigDecimal number, int total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return number.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(int number, int total) {
        return Numbers.percentage(number, total);
    }
}
