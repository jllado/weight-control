const sleepWarning = () => ({
    id: 7, type: 'SLEEP_DISRUPTION', status: 'RESOLVED', version: 2,
    createdAt: '2026-09-25T22:30:00Z', updatedAt: '2026-10-26T09:00:00Z',
    content: {explanation: 'Earlier sleep disruption.', evidence: 'Recorded sleep.', action: 'Record sleep.', reviewedDate: '2026-10-26'},
    progress: {
        reviewedDate: '2026-10-26', reviewedAt: '2026-10-26T09:15:00Z', sourceVersion: 2, goalStatus: 'MET',
        metricsReview: {
            windowDays: 7, windowEnd: '2026-10-25', todayComplete: false,
            shortStatus: 'Both sleep goals met over the last 7 days.',
            metrics: [
                {key: 'asleep', label: 'Asleep', source: 'RECOVERY.totalSleepSeconds', aggregation: 'SUM', unit: 'seconds', value: 151200, minimum: 151200, maximum: null, coveredDays: 7, goalBasis: 'Personal minimum: 6h per day.'},
                {key: 'in-bed', label: 'In bed', source: 'RECOVERY.inBedSeconds', aggregation: 'SUM', unit: 'seconds', value: 205140, minimum: 176400, maximum: null, coveredDays: 7, goalBasis: 'Personal minimum: 7h per day.'}
            ]
        }
    }
});

const nutritionWarning = () => ({
    ...sleepWarning(), id: 8, type: 'NUTRITION_IMBALANCE', status: 'ACTIVE', createdAt: '2026-09-30T22:30:00Z',
    progress: {
        reviewedDate: '2026-10-26', reviewedAt: '2026-10-26T09:15:00Z', sourceVersion: 2, goalStatus: 'NOT_MET',
        metricsReview: {
            windowDays: 7, windowEnd: '2026-10-26', todayComplete: true,
            shortStatus: 'Average intake over the last 7 days is above your maximum.',
            metrics: [{key: 'calories', label: 'Calories', source: 'NUTRITION.calories', aggregation: 'MEAN', unit: 'kcal/day', value: 2882.71, minimum: null, maximum: 2500, coveredDays: 7, goalBasis: 'Agreed personal maximum.'}]
        }
    }
});

module.exports = {sleepWarning, nutritionWarning};
