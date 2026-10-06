const madridDateTimeFormatter = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
});

function dateTimeParts(instant) {
    return Object.fromEntries(madridDateTimeFormatter.formatToParts(new Date(instant))
        .filter(part => part.type !== 'literal').map(part => [part.type, Number(part.value)]));
}

export function madridWallDate(instant) {
    const {year, month, day, hour, minute} = dateTimeParts(instant);
    return new Date(year, month - 1, day, hour, minute);
}

export function madridInstant(wallDate) {
    const requested = Date.UTC(wallDate.getFullYear(), wallDate.getMonth(), wallDate.getDate(), wallDate.getHours(), wallDate.getMinutes());
    let candidate = requested;
    for (let attempt = 0; attempt < 3; attempt += 1) {
        const {year, month, day, hour, minute} = dateTimeParts(candidate);
        const represented = Date.UTC(year, month - 1, day, hour, minute);
        const adjustment = requested - represented;
        if (!adjustment) return new Date(candidate).toISOString();
        candidate += adjustment;
    }
    return null;
}

export function formatMadridWorkoutEnd(instant, workoutDate) {
    if (!instant) return null;
    const {year, month, day, hour, minute} = dateTimeParts(instant);
    const endDate = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    const sourceDate = workoutDate instanceof Date
        ? `${workoutDate.getFullYear()}-${String(workoutDate.getMonth() + 1).padStart(2, '0')}-${String(workoutDate.getDate()).padStart(2, '0')}`
        : String(workoutDate).slice(0, 10);
    const time = `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
    return endDate === sourceDate ? time : `${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}/${year} ${time}`;
}
