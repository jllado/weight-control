import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Workout from '../model/Workout';
import personalRecordService from './PersonalRecordService';
import {celebratePersonalRecords} from './CelebrationService';

function toWorkout(data) {
    return new Workout(data);
}

function attachRecordEvents(workouts, events) {
    const eventsBySegment = new Map();
    events.forEach(event => {
        const source = event.source;
        const key = `${source.id}:${source.linePosition}:${source.segmentPosition}`;
        eventsBySegment.set(key, [...(eventsBySegment.get(key) || []), event]);
    });
    workouts.forEach(workout => workout.lines.forEach(line => {
        const segments = line.trackingMode === 'CARDIO' ? line.intervals : line.sets;
        segments.forEach(segment => {
            segment.recordEvents = eventsBySegment.get(`${workout.id}:${line.position}:${segment.position}`) || [];
        });
    }));
}

function toSegmentPayload(segment) {
    return {
        repetitions: segment.repetitions,
        durationSeconds: segment.durationSeconds,
        weight: segment.weight,
        speedKph: segment.speedKph,
        distanceKm: segment.distanceKm,
        inclinePercent: segment.inclinePercent,
        resistanceLevel: segment.resistanceLevel
    };
}

function toPayload(workout) {
    return {
        workoutDate: dayjs(workout.workoutDate).format('YYYY-MM-DD'),
        note: workout.note,
        lines: workout.lines.map(line => ({
            exerciseId: line.exerciseId,
            calories: line.calories,
            averageHeartRate: line.averageHeartRate,
            segments: line.segments.map(toSegmentPayload)
        }))
    };
}

export default {
    async get_diary(page = 0, size = 10) {
        const data = await get(`/workouts/diary?page=${page}&size=${size}`);
        const workouts = data.items.map(toWorkout);
        attachRecordEvents(workouts, data.recordEvents);
        return {...data, items: workouts};
    },
    async get_preloads(before) {
        return (await get(`/workouts/preload?before=${dayjs(before).format('YYYY-MM-DD')}`)).map(toWorkout);
    },
    async get_all() {
        const workouts = (await get('/workouts')).map(toWorkout);
        const events = await personalRecordService.getWorkoutEvents(workouts.map(workout => workout.id));
        attachRecordEvents(workouts, events);
        return workouts;
    },
    async get_dashboard(date) {
        const data = await get(`/workouts/dashboard?date=${dayjs(date).format('YYYY-MM-DD')}`);
        const currentWorkout = data.currentWorkout ? toWorkout(data.currentWorkout) : null;
        const previousWeekWorkout = data.previousWeekWorkout ? toWorkout(data.previousWeekWorkout) : null;
        const preloadWorkouts = data.preloadWorkouts.map(toWorkout);
        attachRecordEvents([currentWorkout, previousWeekWorkout].filter(Boolean), data.recordEvents);
        return {currentWorkout, previousWeekWorkout, preloadWorkouts};
    },
    async save(workout) {
        const response = workout.id
            ? await put(`/workouts/${workout.id}`, toPayload(workout))
            : await post('/workouts', toPayload(workout));
        celebratePersonalRecords(response.recordAchievements);
        return toWorkout(response.result);
    },
    delete(workout) {
        return del(`/workouts/${workout.id}`);
    }
}
