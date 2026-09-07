package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import java.time.LocalTime;

public final class BackPainEpisodeDtos {

    private BackPainEpisodeDtos() {
    }

    public record BackPainEpisodeCreateRequest(
        @NotNull LocalDate date,
        @NotNull MoodPeriod period,
        BackRegion region,
        BackSide side,
        @NotNull BackPainSeverity severity,
        @Size(max = 500) String note
    ) {
        @AssertTrue(message = "Choose a location for pain, or no location for No pain")
        public boolean isLocationValid() {
            return severity == BackPainSeverity.NONE ? region == null && side == null : region != null && side != null;
        }
    }

    public record CoachBackPainEpisodeRequest(
        @NotNull LocalDate date,
        @NotNull MoodPeriod period,
        BackRegion region,
        BackSide side,
        @NotNull BackPainSeverity severity,
        @Size(max = 500) String note,
        @AssertTrue boolean confirmed
    ) {
        @AssertTrue(message = "Choose a location for pain, or no location for No pain")
        public boolean isLocationValid() {
            return severity == BackPainSeverity.NONE ? region == null && side == null : region != null && side != null;
        }

        public BackPainEpisodeCreateRequest episode() {
            return new BackPainEpisodeCreateRequest(date, period, region, side, severity, note);
        }
    }

    public record BackPainEpisodeUpdateRequest(
        @NotNull MoodPeriod period,
        BackRegion region,
        BackSide side,
        @NotNull BackPainSeverity severity,
        @Size(max = 500) String note
    ) {
        @AssertTrue(message = "Choose a location for pain, or no location for No pain")
        public boolean isLocationValid() {
            return severity == BackPainSeverity.NONE ? region == null && side == null : region != null && side != null;
        }
    }

    public record CoachBackPainEpisodeUpdateRequest(
        @NotNull MoodPeriod period,
        BackRegion region,
        BackSide side,
        @NotNull BackPainSeverity severity,
        @Size(max = 500) String note,
        @AssertTrue boolean confirmed
    ) {
        @AssertTrue(message = "Choose a location for pain, or no location for No pain")
        public boolean isLocationValid() {
            return severity == BackPainSeverity.NONE ? region == null && side == null : region != null && side != null;
        }

        public BackPainEpisodeUpdateRequest episode() {
            return new BackPainEpisodeUpdateRequest(period, region, side, severity, note);
        }
    }

    public record BackPainEpisodeResponse(
        Long id,
        String dateFormat,
        LocalDate date,
        String timeFormat,
        LocalTime time,
        MoodPeriod period,
        BackRegion region,
        BackSide side,
        BackPainSeverity severity,
        String note
    ) {
        public static BackPainEpisodeResponse from(BackPainEpisode episode) {
            return new BackPainEpisodeResponse(
                episode.getId(),
                DateTimes.formatDate(episode.getEpisodeDate()),
                episode.getEpisodeDate(),
                episode.getEpisodeTime() == null ? null : DateTimes.formatTime(episode.getEpisodeTime()),
                episode.getEpisodeTime(),
                episode.getPeriod(),
                episode.getRegion(),
                episode.getSide(),
                episode.getSeverity(),
                episode.getNote()
            );
        }
    }
}
