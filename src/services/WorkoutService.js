import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Workout from '../model/Workout';
import personalRecordService from './PersonalRecordService';
import {celebratePersonalRecords} from './CelebrationService';

function toWorkout(data) {
    return new Workout(data);
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
    async get_all() {
        const workouts = (await get('/workouts')).map(toWorkout);
        const events = await personalRecordService.getWorkoutEvents(workouts.map(workout => workout.id));
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
        return workouts;
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
