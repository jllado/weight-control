import dayjs from 'dayjs';
const PRIOR_SLEEP_BASELINE_WINDOW = 30;
const MIN_PRIOR_SLEEP_BASELINE_ENTRIES = 7;
const PERFECT_SLEEP_SECONDS = 6 * 60 * 60;
const GOOD_SLEEP_SECONDS = 5.5 * 60 * 60;
const NORMAL_SLEEP_SECONDS = 5 * 60 * 60;
const FAIL_SLEEP_SECONDS = 4.5 * 60 * 60;

export function getSleepMetricColor(totalSleepDuration) {
    if (totalSleepDuration === null || totalSleepDuration === undefined) {
        return '';
    }
    if (totalSleepDuration >= PERFECT_SLEEP_SECONDS) {
        return 'perfect';
    }
    if (totalSleepDuration >= GOOD_SLEEP_SECONDS) {
        return 'good';
    }
    if (totalSleepDuration >= NORMAL_SLEEP_SECONDS) {
        return 'normal';
    }
    if (totalSleepDuration >= FAIL_SLEEP_SECONDS) {
        return 'fail';
    }
    return 'bad';
}

export function getCalorieMetricColor(calories, profile, referenceDate) {
    if (calories === null || calories === undefined) {
        return '';
    }
    const limit = getCalorieTarget(profile, referenceDate);
    if (limit === null) {
        return '';
    }
    if (calories <= limit - 500) {
        return 'perfect';
    }
    if (calories <= limit - 250) {
        return 'good';
    }
    if (calories <= limit) {
        return 'normal';
    }
    if (calories <= limit + 250) {
        return 'fail';
    }
    return 'bad';
}

export function getHeartRateMetricColor(averageHeartRate, metricDate, sleeps) {
    if (averageHeartRate === null || averageHeartRate === undefined || !metricDate) {
        return '';
    }
    const baseline = getMedianBaseline(metricDate, sleeps, sleep => sleep.averageHeartRate);
    if (baseline === null) {
        return '';
    }
    const difference = Number(averageHeartRate) - baseline;
    if (difference <= -6) {
        return 'perfect';
    }
    if (difference <= -3) {
        return 'good';
    }
    if (difference <= 2) {
        return 'normal';
    }
    if (difference <= 5) {
        return 'fail';
    }
    return 'bad';
}

export function getHrvMetricColor(averageHrv, metricDate, sleeps) {
    if (averageHrv === null || averageHrv === undefined || !metricDate) {
        return '';
    }
    const baseline = getMedianBaseline(metricDate, sleeps, sleep => sleep.averageHrv);
    if (baseline === null) {
        return '';
    }
    const difference = Number(averageHrv) - baseline;
    if (difference >= 8) {
        return 'perfect';
    }
    if (difference >= 4) {
        return 'good';
    }
    if (difference >= -3) {
        return 'normal';
    }
    if (difference >= -7) {
        return 'fail';
    }
    return 'bad';
}

export function getTypicalCaloriesForDate(profile, referenceDate) {
    if (!profile?.typicalCaloriesPerDay || !referenceDate) {
        return null;
    }
    return profile.typicalCaloriesPerDay[getTypicalCaloriesDayKey(referenceDate)] ?? null;
}

function getCalorieTarget(profile, referenceDate) {
    return getTypicalCaloriesForDate(profile, referenceDate);
}

function getTypicalCaloriesDayKey(referenceDate) {
    switch (dayjs(referenceDate).day()) {
        case 6:
            return 'saturday';
        case 0:
            return 'sunday';
        case 1:
            return 'monday';
        case 2:
            return 'tuesday';
        case 3:
            return 'wednesday';
        case 4:
            return 'thursday';
        default:
            return 'friday';
    }
}

function getMedianBaseline(metricDate, sleeps, valueSelector) {
    const priorValues = sleeps
        .filter(sleep => dayjs(sleep.date).isBefore(metricDate, 'day'))
        .sort((a, b) => dayjs(b.date).valueOf() - dayjs(a.date).valueOf())
        .map(valueSelector)
        .filter(value => value !== null && value !== undefined)
        .slice(0, PRIOR_SLEEP_BASELINE_WINDOW)
        .map(Number);
    if (priorValues.length < MIN_PRIOR_SLEEP_BASELINE_ENTRIES) {
        return null;
    }
    const sorted = priorValues.slice().sort((a, b) => a - b);
    const middle = Math.floor(sorted.length / 2);
    if (sorted.length % 2 === 0) {
        return (sorted[middle - 1] + sorted[middle]) / 2;
    }
    return sorted[middle];
}
