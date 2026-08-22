import {get, put} from './api';

function queryString(filters = {}) {
    const parameters = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
        if (Array.isArray(value)) {
            value.forEach(item => parameters.append(key, item));
        } else if (value !== null && value !== undefined && value !== '') {
            parameters.set(key, value);
        }
    });
    const query = parameters.toString();
    return query ? `?${query}` : '';
}

export function formatRecordValue(record) {
    const value = Number(record.value);
    switch (record.unit) {
        case 'KG': return `${value} kg`;
        case 'PERCENT': return `${value}%`;
        case 'REPETITIONS': return `${value} reps`;
        case 'SECONDS': {
            const minutes = Math.floor(value / 60);
            const seconds = value % 60;
            return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
        }
        case 'KM_PER_HOUR': return `${value} km/h`;
        case 'KM': return `${value} km`;
        case 'LEVEL': return `Level ${value}`;
        case 'MM_HG': return `${value} mm Hg`;
        case 'MG_PER_DL': return `${value} mg/dL`;
        case 'KCAL': return `${value} kcal`;
        case 'GRAMS': return `${value} g`;
        case 'BPM': return `${value} bpm`;
        case 'MILLISECONDS': return `${value} ms`;
        case 'SCORE_OUT_OF_FIVE': return `${value}/5`;
        case 'COMPLETIONS': return `${value} completion${value === 1 ? '' : 's'}`;
        case 'DAYS': return `${value} day${value === 1 ? '' : 's'}`;
        case 'DECISIONS': return `${value} decision${value === 1 ? '' : 's'}`;
    }
}

async function getWorkoutEvents(workoutIds) {
    if (!workoutIds.length) {
        return [];
    }
    const events = [];
    for (let offset = 0; offset < workoutIds.length; offset += 50) {
        const batch = workoutIds.slice(offset, offset + 50);
        let page = 0;
        let response;
        do {
            response = await get(`/personal-records/history${queryString({domain: 'WORKOUT', workoutId: batch, page, size: 100})}`);
            events.push(...response.items);
            page += 1;
        } while (page < response.totalPages);
    }
    return events;
}

export default {
    getCatalog() {
        return get('/personal-records/catalog');
    },
    replaceSettings(overrides) {
        return put('/personal-records/settings', {overrides});
    },
    getCurrent(filters = {}) {
        return get(`/personal-records/current${queryString(filters)}`);
    },
    getHistory(filters = {}) {
        return get(`/personal-records/history${queryString(filters)}`);
    },
    getWorkoutEvents
};
