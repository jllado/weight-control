package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.PlanningWeekChange;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.PlanningWeekChangeRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanningWeekService {
    private final PlanningWeekChangeRepository changes;

    public PlanningWeekService(PlanningWeekChangeRepository changes) {
        this.changes = changes;
    }

    public record Period(LocalDate start, LocalDate end) {
        public boolean transition() { return end.isBefore(start.plusDays(6)); }
    }

    public static Period period(User user, LocalDate date) {
        DayOfWeek firstDay = DayOfWeek.SATURDAY;
        LocalDate nextChange = null;
        for (var change : user.getPlanningWeekChanges()) {
            if (change.getEffectiveDate().isAfter(date)) {
                nextChange = change.getEffectiveDate();
                break;
            }
            firstDay = change.getEffectiveDate().getDayOfWeek();
        }
        LocalDate start = date.with(TemporalAdjusters.previousOrSame(firstDay));
        LocalDate end = start.plusDays(6);
        if (nextChange != null && !nextChange.isAfter(end)) end = nextChange.minusDays(1);
        return new Period(start, end);
    }

    // Profile updates hold the user row lock. Only the final, still-future change can be edited.
    void schedule(User owner, DayOfWeek requested, LocalDate today, LocalDate expectedEffectiveDate) {
        var history = owner.getPlanningWeekChanges();
        var pending = history.isEmpty() ? null : history.getLast();
        boolean hasPending = pending != null && pending.getEffectiveDate().isAfter(today);
        LocalDate effectiveDate = requested == period(owner, today).start().getDayOfWeek()
            ? null : today.with(TemporalAdjusters.next(requested));
        if (!Objects.equals(effectiveDate, expectedEffectiveDate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Planning dates changed. Review the new dates before saving.");
        }
        if (effectiveDate == null) {
            if (hasPending) {
                history.removeLast();
                changes.delete(pending);
            }
            return;
        }
        if (hasPending) {
            pending.setEffectiveDate(effectiveDate);
        } else {
            history.add(changes.save(new PlanningWeekChange(owner, effectiveDate)));
        }
    }
}
