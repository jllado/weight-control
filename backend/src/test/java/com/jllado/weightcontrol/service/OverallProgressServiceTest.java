package com.jllado.weightcontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.*;
import com.jllado.weightcontrol.service.OvernightHeartRateAnalysis.Limit;
import com.jllado.weightcontrol.service.OvernightHeartRateAnalysis.Night;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OverallProgressServiceTest {
    private final DailyStatusRepository statuses = mock(DailyStatusRepository.class);
    private final WeightRepository weights = mock(WeightRepository.class);
    private final BloodPressureRepository pressures = mock(BloodPressureRepository.class);
    private final SleepRepository sleeps = mock(SleepRepository.class);
    private final WorkoutRepository workouts = mock(WorkoutRepository.class);
    private final OvernightHeartRateService heartRates = mock(OvernightHeartRateService.class);
    private final OverallProgressService service = new OverallProgressService(statuses, weights, pressures, sleeps, workouts, heartRates);

    @BeforeEach
    void noHeartRateRecordsByDefault() {
        when(heartRates.analysis(any(), any())).thenReturn(OvernightHeartRateAnalysis.evaluate(List.of(), List.of(), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void classifiesAllFiveStatesAtTheirInclusiveBoundaries() {
        assertThat(OverallProgressService.classify(new BigDecimal("1"))).isEqualTo("STRONGLY_IMPROVING");
        assertThat(OverallProgressService.classify(new BigDecimal("0.2001"))).isEqualTo("SLIGHTLY_IMPROVING");
        assertThat(OverallProgressService.classify(new BigDecimal("0.2"))).isEqualTo("STABLE");
        assertThat(OverallProgressService.classify(BigDecimal.ZERO)).isEqualTo("STABLE");
        assertThat(OverallProgressService.classify(new BigDecimal("-0.2"))).isEqualTo("STABLE");
        assertThat(OverallProgressService.classify(new BigDecimal("-0.2001"))).isEqualTo("SLIGHTLY_DECLINING");
        assertThat(OverallProgressService.classify(new BigDecimal("-1"))).isEqualTo("STRONGLY_DECLINING");
        assertThat(OverallProgressService.classify(null)).isNull();
    }

    @Test
    void reportsNotEnoughDataInsteadOfStableWhenNoMetricsQualify() {
        when(statuses.findByUserAndStatusDateBetweenOrderByStatusDateAsc(any(), any(), any())).thenReturn(List.of());
        when(weights.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(any(), any(), any())).thenReturn(List.of());
        when(pressures.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(any(), any(), any())).thenReturn(List.of());
        when(sleeps.findByUserAndSleepDateBetweenOrderBySleepDateAsc(any(), any(), any())).thenReturn(List.of());
        when(workouts.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(any(), any(), any())).thenReturn(List.of());

        var result = service.get(new User(), LocalDate.now().minusDays(1));

        assertThat(result.status()).isNull();
        assertThat(result.score()).isNull();
        assertThat(result.contributions()).hasSize(7).allMatch(metric -> !metric.included());
    }

    @Test
    void redistributesAvailableWeightAndClampsEachContribution() {
        when(statuses.findByUserAndStatusDateBetweenOrderByStatusDateAsc(any(), any(), any())).thenAnswer(invocation -> {
            LocalDate start = invocation.getArgument(1);
            return java.util.stream.IntStream.range(0, 7).mapToObj(i -> {
                DailyStatus status = new DailyStatus();
                status.setStatusDate(start.plusDays(i));
                status.setRoutinesPercentage(new BigDecimal(start.isAfter(LocalDate.now().minusDays(31)) ? "100" : "50"));
                return status;
            }).toList();
        });
        when(weights.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(any(), any(), any())).thenAnswer(invocation -> {
            LocalDate start = ((OffsetDateTime) invocation.getArgument(1)).toLocalDate();
            boolean recent = start.isAfter(LocalDate.now().minusDays(31));
            return java.util.stream.IntStream.of(2, 4, 6).mapToObj(day -> {
                com.jllado.weightcontrol.domain.Weight weight = new com.jllado.weightcontrol.domain.Weight();
                weight.setMeasuredAt(start.plusDays(day).atStartOfDay().atOffset(ZoneOffset.UTC));
                weight.setFatPercentage(new BigDecimal(recent ? "29" : "30"));
                weight.setMuscle(new BigDecimal(recent ? "51" : "50"));
                return weight;
            }).toList();
        });
        when(pressures.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(any(), any(), any())).thenReturn(List.of());
        when(sleeps.findByUserAndSleepDateBetweenOrderBySleepDateAsc(any(), any(), any())).thenReturn(List.of());
        when(workouts.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(any(), any(), any())).thenReturn(List.of());

        var result = service.get(new User(), LocalDate.now().minusDays(1));

        assertThat(result.status()).isEqualTo("STRONGLY_IMPROVING");
        assertThat(result.score()).isEqualByComparingTo("1.6667");
        var included = result.contributions().stream().filter(metric -> metric.included()).toList();
        assertThat(included).hasSize(3);
        assertThat(included).anySatisfy(metric -> {
            assertThat(metric.metric()).isEqualTo("Routine completion");
            assertThat(metric.weight()).isEqualByComparingTo("30");
            assertThat(metric.normalizedContribution()).isEqualByComparingTo("2.0");
        });
        assertThat(included).anySatisfy(metric -> {
            assertThat(metric.metric()).isEqualTo("Body fat");
            assertThat(metric.weight()).isEqualByComparingTo("20");
            assertThat(metric.change()).isEqualByComparingTo("1.0");
            assertThat(metric.normalizedContribution()).isEqualByComparingTo("1.0");
        });
        assertThat(included).anySatisfy(metric -> {
            assertThat(metric.metric()).isEqualTo("Muscle mass");
            assertThat(metric.weight()).isEqualByComparingTo("10");
            assertThat(metric.normalizedContribution()).isEqualByComparingTo("2.0");
        });
    }

    @ParameterizedTest
    @CsvSource({
        "7, 1, 7, 1, 0, 0", "20, 3, 20, 1, 10, 1", "20, 1, 20, 3, -10, -1",
        "10, 2, 10, 0, 20, 2", "10, 0, 10, 2, -20, -2",
        "10, 3, 10, 0, 30, 2", "10, 0, 10, 3, -30, -2",
        "20, 3, 10, 1, 5, 0.5"
    })
    void scoresTheShareOfElevatedNightsAcrossCompletedThirtyDayPeriods(int previousTotal, int previousHigh,
        int currentTotal, int currentHigh, String change, String score) {
        LocalDate end = LocalDate.of(2026, 8, 30);
        User user = new User();
        stubHeartRateHistory(user, end, previousTotal, previousHigh, currentTotal, currentHigh);

        var result = service.get(user, end);
        var metric = result.contributions().stream().filter(c -> c.metric().equals("Overnight heart rate")).findFirst().orElseThrow();
        assertThat(metric.included()).isTrue();
        assertThat(metric.weight()).isEqualByComparingTo("5");
        assertThat(metric.change()).isEqualByComparingTo(change);
        assertThat(metric.normalizedContribution()).isEqualByComparingTo(score);
        assertThat(result.score()).isEqualByComparingTo(score);
        assertThat(metric.explanation()).contains(currentHigh + "/" + currentTotal + " in the current",
            previousHigh + "/" + previousTotal + " in the previous");
    }

    @ParameterizedTest
    @CsvSource({"6,7", "7,6", "0,0"})
    void showsBothFractionsWhenEitherPeriodLacksSevenAssessableNights(int previousTotal, int currentTotal) {
        LocalDate end = LocalDate.of(2026, 8, 30);
        User user = new User();
        stubHeartRateHistory(user, end, previousTotal, 0, currentTotal, 0);

        var result = service.get(user, end);
        var metric = result.contributions().stream().filter(c -> c.metric().equals("Overnight heart rate")).findFirst().orElseThrow();
        assertThat(metric.included()).isFalse();
        assertThat(metric.change()).isNull();
        assertThat(metric.normalizedContribution()).isNull();
        assertThat(metric.explanation()).contains("0/" + currentTotal + " in the current", "0/" + previousTotal + " in the previous",
            "seven assessable nights");
        assertThat(result.score()).isNull();
    }

    @Test
    void futureSelectedDatesUseTheLastCompletedMadridDayForHeartRate() {
        User user = new User();
        LocalDate today = LocalDate.now(DateTimes.USER_ZONE);
        var result = service.get(user, today.plusDays(4));
        assertThat(result.currentEnd()).isEqualTo(today.minusDays(1));
        verify(heartRates).analysis(user, result.currentEnd());
    }

    private void stubHeartRateHistory(User user, LocalDate end, int previousTotal, int previousHigh, int currentTotal, int currentHigh) {
        var nights = new ArrayList<Night>();
        nights.add(new Night(end.minusDays(60), BigDecimal.valueOf(80)));
        nights.add(new Night(end.plusDays(1), BigDecimal.valueOf(80)));
        addPeriod(nights, end.minusDays(59), previousTotal, previousHigh);
        addPeriod(nights, end.minusDays(29), currentTotal, currentHigh);
        var limit = new Limit(end.minusDays(60), BigDecimal.valueOf(70));
        when(heartRates.analysis(user, end)).thenReturn(OvernightHeartRateAnalysis.evaluate(nights, List.of(limit), end));
    }

    private void addPeriod(List<Night> nights, LocalDate start, int total, int high) {
        for (int index = 0; index < total; index++) {
            LocalDate date = start.plusDays(index == total - 1 ? 29 : index);
            nights.add(new Night(date, BigDecimal.valueOf(index < high ? 80 : 60)));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "25199, 21600, false, -1", "25200, 21600, false, 0", "25201, 21600, false, 0",
        "32401, 21600, false, -1", "36000, 21600, false, -1",
        "25200, 21599, false, -1", "25200, 21601, false, 0",
        "36000, 32400, false, 0", "36000, 32400, true, -1"
    })
    void sleepScoreUsesInclusiveDurationMinimumsWithoutAnUpperBoundAndRetainsOtherCriteria(
        int bedSeconds, int asleepSeconds, boolean irregularMidpoint, int expectedChange
    ) {
        LocalDate selectedDate = LocalDate.of(2026, 8, 30);
        when(sleeps.findByUserAndSleepDateBetweenOrderBySleepDateAsc(any(), any(), any())).thenAnswer(invocation -> {
            LocalDate start = invocation.getArgument(1);
            boolean current = start.equals(selectedDate.minusDays(29));
            return java.util.stream.IntStream.range(0, 8).mapToObj(day -> {
                var sleep = new com.jllado.weightcontrol.domain.Sleep();
                sleep.setSleepDate(start.plusDays(day));
                var bedtime = start.plusDays(day).atTime(22, 0).atOffset(ZoneOffset.UTC);
                if (current && irregularMidpoint && day % 2 == 0) bedtime = bedtime.minusHours(4);
                sleep.setBedtimeStart(bedtime);
                sleep.setBedtimeEnd(bedtime.plusSeconds(current ? bedSeconds : 25200));
                sleep.setTotalSleepDuration(current ? asleepSeconds : 21600);
                return sleep;
            }).toList();
        });

        User user = new User();
        stubHeartRateHistory(user, selectedDate, 10, 1, 10, 0);
        var result = service.get(user, selectedDate);
        var sleep = result.contributions().stream().filter(metric -> metric.metric().equals("Sleep quality")).findFirst().orElseThrow();

        assertThat(sleep.included()).isTrue();
        assertThat(sleep.weight()).isEqualByComparingTo("10");
        assertThat(sleep.change()).isEqualByComparingTo(Integer.toString(expectedChange));
        BigDecimal weightedScore = BigDecimal.valueOf(10L * expectedChange + 5).divide(BigDecimal.valueOf(15), 4, java.math.RoundingMode.HALF_UP);
        assertThat(result.score()).isEqualByComparingTo(weightedScore);
    }
}
