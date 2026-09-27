package com.jllado.weightcontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverallProgressServiceTest {
    private final DailyStatusRepository statuses = mock(DailyStatusRepository.class);
    private final WeightRepository weights = mock(WeightRepository.class);
    private final BloodPressureRepository pressures = mock(BloodPressureRepository.class);
    private final SleepRepository sleeps = mock(SleepRepository.class);
    private final WorkoutRepository workouts = mock(WorkoutRepository.class);
    private final OverallProgressService service = new OverallProgressService(statuses, weights, pressures, sleeps, workouts);

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
        assertThat(result.contributions()).hasSize(6).allMatch(metric -> !metric.included());
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
}
