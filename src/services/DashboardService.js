import {get, post} from './api';
import DailyStatus from "@/model/DailyStatus";
import WeekStatus from "@/model/WeekStatus";
import Mood from "@/model/Mood";

function normalizeDailyStatus(data) {
    if (!data) {
        return undefined;
    }
    return new DailyStatus({
        id: data.id,
        date: data.date,
        weight: data.weight,
        blood_pressure: data.bloodPressure,
        total_routines: data.totalRoutines,
        total_weight_routines: data.totalWeightRoutines,
        total_blood_pressure_routines: data.totalBloodPressureRoutines,
        total_flexibility_routines: data.totalFlexibilityRoutines,
        total_mind_routines: data.totalMindRoutines,
        routines_done: data.routinesDone,
        weight_done: data.weightDone,
        blood_pressure_done: data.bloodPressureDone,
        flexibility_done: data.flexibilityDone,
        mind_done: data.mindDone,
        mood: data.mood ? new Mood(data.mood) : null,
        routines_percentage: data.routinesPercentage,
        weight_percentage: data.weightPercentage,
        blood_pressure_percentage: data.bloodPressurePercentage,
        flexibility_percentage: data.flexibilityPercentage,
        mind_percentage: data.mindPercentage,
        mood_trend: data.moodTrend,
        routines_score: data.routinesScore,
        weight_score: data.weightScore,
        blood_pressure_score: data.bloodPressureScore,
        flexibility_score: data.flexibilityScore,
        mind_score: data.mindScore,
        routines_status: data.routinesStatus,
        weight_status: data.weightStatus,
        blood_pressure_status: data.bloodPressureStatus,
        flexibility_status: data.flexibilityStatus,
        mind_status: data.mindStatus
    });
}

function normalizeWeekStatus(data) {
    return new WeekStatus(
        [
            normalizeDailyStatus(data.saturday),
            normalizeDailyStatus(data.sunday),
            normalizeDailyStatus(data.monday),
            normalizeDailyStatus(data.tuesday),
            normalizeDailyStatus(data.wednesday),
            normalizeDailyStatus(data.thursday),
            normalizeDailyStatus(data.friday)
        ],
        {
            routines_percentage: data.routinesPercentage,
            weight_percentage: data.weightPercentage,
            blood_pressure_percentage: data.bloodPressurePercentage,
            flexibility_percentage: data.flexibilityPercentage,
            mind_percentage: data.mindPercentage,
            mood_average: data.moodAverage
        }
    );
}

function normalizeOutcomeMetrics(data) {
    return {
        wins: data.wins,
        misses: data.misses,
        winRate: data.winRate
    };
}

function normalizeWinsAndMissesStatus(data) {
    return {
        selectedDate: normalizeOutcomeMetrics(data.selectedDate),
        rolling30Days: normalizeOutcomeMetrics(data.rolling30Days),
        previous30Days: normalizeOutcomeMetrics(data.previous30Days),
        allTime: normalizeOutcomeMetrics(data.allTime),
        winRateChange: data.winRateChange,
        currentWinStreak: data.currentWinStreak
    };
}

function normalizeDashboard(data) {
    return {
        anchorDate: data.anchorDate,
        lastCompletedDashboardDate: data.lastCompletedDashboardDate,
        dailyStatus: normalizeDailyStatus(data.dailyStatus),
        lastWeekDailyStatus: normalizeDailyStatus(data.lastWeekDailyStatus),
        weekStatus: normalizeWeekStatus(data.weekStatus),
        weekAgoStatus: normalizeWeekStatus(data.weekAgoStatus),
        winsAndMissesStatus: normalizeWinsAndMissesStatus(data.winsAndMissesStatus)
    };
}

export default {
    async get() {
        return normalizeDashboard(await get('/dashboard'));
    },
    async advance() {
        return normalizeDashboard(await post('/dashboard/advance', {}));
    },
    async retreat() {
        return normalizeDashboard(await post('/dashboard/retreat', {}));
    },
    async refresh() {
        return normalizeDashboard(await post('/dashboard/refresh', {}));
    },
    async setDashboardCompletion(completed) {
        return normalizeDashboard(await post('/dashboard/completion', {completed}));
    }
}
