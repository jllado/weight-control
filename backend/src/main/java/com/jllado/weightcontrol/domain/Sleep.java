package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.jllado.weightcontrol.util.DateTimes;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "sleeps")
@Getter
@Setter
public class Sleep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "sleep_date", nullable = false)
    private LocalDate sleepDate;

    @Column(name = "total_sleep_duration")
    private Integer totalSleepDuration;

    @Column(name = "awake_time")
    private Integer awakeTime;

    @Column(name = "deep_sleep_duration")
    private Integer deepSleepDuration;

    @Column(name = "rem_sleep_duration")
    private Integer remSleepDuration;

    @Column(name = "light_sleep_duration")
    private Integer lightSleepDuration;

    @Column(name = "average_heart_rate", precision = 5, scale = 2)
    private BigDecimal averageHeartRate;

    @Column(name = "average_hrv")
    private Integer averageHrv;

    @Column(name = "bedtime_start")
    private OffsetDateTime bedtimeStart;

    @Column(name = "bedtime_end")
    private OffsetDateTime bedtimeEnd;

    @Column(name = "bedtime_start_offset_seconds")
    private Integer bedtimeStartOffsetSeconds;

    @Column(name = "bedtime_end_offset_seconds")
    private Integer bedtimeEndOffsetSeconds;

    public OffsetDateTime getBedtimeStart() {
        return localTimestamp(bedtimeStart, bedtimeStartOffsetSeconds);
    }

    public void setBedtimeStart(OffsetDateTime value) {
        bedtimeStart = value;
        bedtimeStartOffsetSeconds = value == null ? null : value.getOffset().getTotalSeconds();
    }

    public OffsetDateTime getBedtimeEnd() {
        return localTimestamp(bedtimeEnd, bedtimeEndOffsetSeconds);
    }

    public void setBedtimeEnd(OffsetDateTime value) {
        bedtimeEnd = value;
        bedtimeEndOffsetSeconds = value == null ? null : value.getOffset().getTotalSeconds();
    }

    private static OffsetDateTime localTimestamp(OffsetDateTime value, Integer offsetSeconds) {
        if (value == null) return null;
        // Legacy rows did not retain their offset and historically used Madrid clocks.
        return offsetSeconds == null ? value.atZoneSameInstant(DateTimes.USER_ZONE).toOffsetDateTime()
            : value.withOffsetSameInstant(ZoneOffset.ofTotalSeconds(offsetSeconds));
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
