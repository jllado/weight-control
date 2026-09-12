const googleClientScript = `
window.google = {
    accounts: {
        id: {
            initialize(configuration) {
                window.googleCredentialCallback = configuration.callback;
            },
            renderButton(element) {
                const button = document.createElement('button');
                button.textContent = 'Sign in with Google';
                button.addEventListener('click', () => window.googleCredentialCallback({
                    credential: 'android-id-token',
                    select_by: 'btn'
                }));
                element.appendChild(button);
            },
            cancel() {}
        }
    }
};
`;

const profile = {
    birthDate: null,
    heightCm: 180,
    sex: 'MALE',
    fitnessLevel: 'ACTIVE',
    takesMedication: false,
    weeklyAverageCalorieMaximum: 2500,
    typicalCaloriesPerDay: {
        saturday: 2983,
        sunday: 2983,
        monday: 1853,
        tuesday: 1853,
        wednesday: 1853,
        thursday: 1853,
        friday: 1122
    },
    calorieShortcuts: {
        onPlan: 1850,
        flexible: 3000,
        offPlan: 4000,
        binge: 5000
    }
};

function dashboardDailyStatus(date) {
    return {
        id: date,
        date,
        weight: null,
        bloodPressure: null,
        totalRoutines: 0,
        totalWeightRoutines: 0,
        totalBloodPressureRoutines: 0,
        totalFlexibilityRoutines: 0,
        totalMindRoutines: 0,
        routinesDone: 0,
        weightDone: 0,
        bloodPressureDone: 0,
        flexibilityDone: 0,
        mindDone: 0,
        mood: {average: null, morning: null, midday: null, evening: null},
        routinesPercentage: 0,
        weightPercentage: 0,
        bloodPressurePercentage: 0,
        flexibilityPercentage: 0,
        mindPercentage: 0,
        moodTrend: null,
        routinesScore: 0,
        weightScore: 0,
        bloodPressureScore: 0,
        flexibilityScore: 0,
        mindScore: 0,
        routinesStatus: 0,
        weightStatus: 0,
        bloodPressureStatus: 0,
        flexibilityStatus: 0,
        mindStatus: 0
    };
}

function dashboardWeek() {
    return {
        saturday: null,
        sunday: null,
        monday: null,
        tuesday: null,
        wednesday: null,
        thursday: null,
        friday: null,
        routinesPercentage: 0,
        weightPercentage: 0,
        bloodPressurePercentage: 0,
        flexibilityPercentage: 0,
        mindPercentage: 0,
        moodAverage: null
    };
}

const noDecisionMetrics = {wins: 0, misses: 0, winRate: null};
const dashboard = {
    anchorDate: '2026-08-12',
    lastCompletedDashboardDate: null,
    dailyStatus: dashboardDailyStatus('2026-08-12'),
    lastWeekDailyStatus: dashboardDailyStatus('2026-08-05'),
    weekStatus: dashboardWeek(),
    weekAgoStatus: dashboardWeek(),
    winsAndMissesStatus: {
        selectedDate: noDecisionMetrics,
        rolling30Days: noDecisionMetrics,
        previous30Days: noDecisionMetrics,
        allTime: noDecisionMetrics,
        winRateChange: null,
        currentWinStreak: 0
    }
};

module.exports = {googleClientScript, profile, dashboard, dashboardDailyStatus, dashboardWeek, noDecisionMetrics};
