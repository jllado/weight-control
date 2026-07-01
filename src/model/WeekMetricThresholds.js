import dayjs from 'dayjs';
import {UserFitnessLevel, UserSex} from './UserProfile';

const DEFAULT_CALORIE_LIMIT = 2500;
const PRIOR_SLEEP_BASELINE_WINDOW = 30;
const MIN_PRIOR_SLEEP_BASELINE_ENTRIES = 7;
const ADULT_SLEEP_BANDS = {
    perfectMin: 23400,
    perfectMax: 28800,
    goodLowMin: 21600,
    goodLowMax: 23399,
    goodHighMin: 28801,
    goodHighMax: 30600,
    normalLowMin: 19800,
    normalLowMax: 21599,
    normalHighMin: 30601,
    normalHighMax: 32400,
    failLowMin: 18000,
    failLowMax: 19799,
    failHighMin: 32401,
    failHighMax: 34200
};

const MALE_ACTIVITY_FACTORS = {
    [UserFitnessLevel.SEDENTARY]: 1,
    [UserFitnessLevel.LOW_ACTIVE]: 1.11,
    [UserFitnessLevel.ACTIVE]: 1.25,
    [UserFitnessLevel.VERY_ACTIVE]: 1.48
};

const FEMALE_ACTIVITY_FACTORS = {
    [UserFitnessLevel.SEDENTARY]: 1,
    [UserFitnessLevel.LOW_ACTIVE]: 1.12,
    [UserFitnessLevel.ACTIVE]: 1.27,
    [UserFitnessLevel.VERY_ACTIVE]: 1.45
};

export function getSleepMetricColor(totalSleepDuration, profile, metricDate) {
    if (totalSleepDuration === null || totalSleepDuration === undefined) {
        return '';
    }
    const bands = resolveSleepBands(profile, metricDate);
    if (totalSleepDuration >= bands.perfectMin && totalSleepDuration <= bands.perfectMax) {
        return 'perfect';
    }
    if (
        (totalSleepDuration >= bands.goodLowMin && totalSleepDuration <= bands.goodLowMax)
        || (totalSleepDuration >= bands.goodHighMin && totalSleepDuration <= bands.goodHighMax)
    ) {
        return 'good';
    }
    if (
        (totalSleepDuration >= bands.normalLowMin && totalSleepDuration <= bands.normalLowMax)
        || (totalSleepDuration >= bands.normalHighMin && totalSleepDuration <= bands.normalHighMax)
    ) {
        return 'normal';
    }
    if (
        (totalSleepDuration >= bands.failLowMin && totalSleepDuration <= bands.failLowMax)
        || (totalSleepDuration >= bands.failHighMin && totalSleepDuration <= bands.failHighMax)
    ) {
        return 'fail';
    }
    return 'bad';
}

export function getCalorieMetricColor(calories, profile, latestWeight, referenceDate) {
    if (calories === null || calories === undefined) {
        return '';
    }
    const limit = getCalorieTarget(profile, latestWeight, referenceDate);
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

function resolveSleepBands(profile, metricDate) {
    const age = getAgeOn(profile?.birthDate, metricDate);
    if (age !== null && age >= 65) {
        return ADULT_SLEEP_BANDS;
    }
    return ADULT_SLEEP_BANDS;
}

function getCalorieTarget(profile, latestWeight, referenceDate) {
    if (!profile?.birthDate || !profile?.heightCm || !profile?.sex || !profile?.fitnessLevel || !latestWeight?.weight) {
        return DEFAULT_CALORIE_LIMIT;
    }
    const age = getAgeOn(profile.birthDate, referenceDate);
    if (age === null) {
        return DEFAULT_CALORIE_LIMIT;
    }
    const heightMeters = profile.heightCm / 100;
    const weightKg = Number(latestWeight.weight);
    const factor = profile.sex === UserSex.MALE
        ? MALE_ACTIVITY_FACTORS[profile.fitnessLevel]
        : FEMALE_ACTIVITY_FACTORS[profile.fitnessLevel];
    if (!factor) {
        return DEFAULT_CALORIE_LIMIT;
    }
    const maintenanceCalories = profile.sex === UserSex.MALE
        ? 662 - 9.53 * age + factor * (15.91 * weightKg + 539.6 * heightMeters)
        : 354 - 6.91 * age + factor * (9.36 * weightKg + 726 * heightMeters);
    return Math.round((maintenanceCalories - 500) / 50) * 50;
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

function getAgeOn(birthDate, referenceDate) {
    if (!birthDate || !referenceDate) {
        return null;
    }
    return dayjs(referenceDate).diff(dayjs(birthDate), 'year');
}
