export function completedFastingPeriods(periods) {
    const ranges = periods
        .filter(period => period.endTime)
        .map(period => ({startTime: new Date(period.startTime), endTime: new Date(period.endTime)}))
        .sort((left, right) => left.startTime - right.startTime);
    return ranges.reduce((merged, range) => {
        const previous = merged.at(-1);
        if (previous && range.startTime <= previous.endTime) {
            if (range.endTime > previous.endTime) previous.endTime = range.endTime;
        } else {
            merged.push(range);
        }
        return merged;
    }, []);
}

export function fastingDurationMinutes(period) {
    return Math.round((period.endTime - period.startTime) / 60000);
}

export function fastingSummary(periods) {
    const completed = completedFastingPeriods(periods);
    const durations = completed.map(fastingDurationMinutes);
    return {
        periods: completed,
        averageMinutes: durations.length ? Math.round(durations.reduce((total, duration) => total + duration, 0) / durations.length) : null,
        recordMinutes: durations.length ? Math.max(...durations) : null
    };
}
