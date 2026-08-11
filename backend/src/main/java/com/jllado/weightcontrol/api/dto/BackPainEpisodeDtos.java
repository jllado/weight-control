package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public final class BackPainEpisodeDtos {

    private BackPainEpisodeDtos() {
    }

    public record BackPainEpisodeCreateRequest(
        @NotNull LocalDate date,
        @NotNull BackRegion region,
        @NotNull BackSide side,
        @NotNull @Min(1) @Max(10) Integer pain,
        @Size(max = 500) String note
    ) {
    }

    public record BackPainEpisodeUpdateRequest(
        @NotNull BackRegion region,
        @NotNull BackSide side,
        @NotNull @Min(1) @Max(10) Integer pain,
        @Size(max = 500) String note
    ) {
    }

    public record BackPainEpisodeResponse(
        Long id,
        String dateFormat,
        LocalDate date,
        String timeFormat,
        LocalTime time,
        BackRegion region,
        BackSide side,
        Integer pain,
        String note
    ) {
        public static BackPainEpisodeResponse from(BackPainEpisode episode) {
            return new BackPainEpisodeResponse(
                episode.getId(),
                DateTimes.formatDate(episode.getEpisodeDate()),
                episode.getEpisodeDate(),
                episode.getEpisodeTime() == null ? null : DateTimes.formatTime(episode.getEpisodeTime()),
                episode.getEpisodeTime(),
                episode.getRegion(),
                episode.getSide(),
                episode.getPain(),
                episode.getNote()
            );
        }
    }
}
