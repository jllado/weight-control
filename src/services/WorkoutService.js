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
        breaths: segment.breaths,
        weight: segment.weight,
        speedKph: segment.speedKph,
        cadenceRpm: segment.cadenceRpm,
        distanceKm: segment.distanceKm,
        inclinePercent: segment.inclinePercent,
        resistanceLevel: segment.resistanceLevel,
        skipped: segment.skipped || false
    };
}

function toPayload(workout) {
    return {
        workoutDate: dayjs(workout.workoutDate).format('YYYY-MM-DD'),
        recordingKey: workout.recordingKey || undefined,
        note: workout.note,
        plannedSessionName: workout.plannedSessionName,
        plannedTargets: workout.plannedTargets,
        saunaSession: workout.saunaSession,
        saunaRoundsMinutes: workout.saunaRoundsMinutes,
        plannedSaunaRoundsMinutes: workout.plannedSaunaRoundsMinutes,
        startTime: workout.startTime,
        endTime: workout.endTime,
        durationMinutes: workout.durationMinutes,
        warmUpMinutes: workout.warmUpMinutes,
        trainingMinutes: workout.trainingMinutes,
        stretchingMinutes: workout.stretchingMinutes,
        cardioMinutes: workout.cardioMinutes,
        lines: workout.lines.map(line => ({
            exerciseId: line.exerciseId,
            stretchingUnit: line.stretchingUnit,
            supersetGroupId: line.supersetGroupId || undefined,
            calories: line.calories,
            averageHeartRate: line.averageHeartRate,
            exerciseDurationSeconds: line.exerciseDurationSeconds ?? null,
            segments: line.segments.map(toSegmentPayload)
        }))
    };
}

export default {
    async get_latest_sauna(through) {
        const data = await get(`/workouts/sauna/latest?through=${dayjs(through).format('YYYY-MM-DD')}`);
        return data ? toWorkout(data) : null;
    },
    copy_sauna(source, date) {
        const lines = [...source.lines].sort((a, b) => a.position - b.position).map(line => ({
            ...line,
            segments: [...(line.trackingMode === 'CARDIO' ? line.intervals : line.sets)]
                .filter(segment => !segment.skipped).sort((a, b) => a.position - b.position)
        })).filter(line => line.segments.length);
        const groups = new Map();
        lines.filter(line => line.supersetGroupId).forEach(line => {
            const members = groups.get(line.supersetGroupId) || [];
            members.push(line);
            groups.set(line.supersetGroupId, members);
        });
        groups.forEach(members => {
            if (members.length < 2 || members.some(line => line.segments.length !== members[0].segments.length)) {
                members.forEach(line => line.supersetGroupId = null);
            }
        });
        return toPayload({
            ...source,
            workoutDate: date,
            recordingKey: crypto.randomUUID(),
            note: null,
            plannedSessionName: null,
            plannedTargets: null,
            plannedSaunaRoundsMinutes: null,
            startTime: null,
            endTime: null,
            saunaRoundsMinutes: [...source.saunaRoundsMinutes],
            lines
        });
    },
    get_training_balance(date) { return get(`/workouts/training-balance?date=${date}`); },
    async get_diary(page = 0, size = 10) {
        const data = await get(`/workouts/diary?page=${page}&size=${size}`);
        const days = data.items.map(day => ({...day, sessions: day.sessions.map(toWorkout)}));
        attachRecordEvents(days.flatMap(day => day.sessions), data.recordEvents);
        return {...data, items: days};
    },
    async get_preloads(through) {
        return (await get(`/workouts/preload?through=${dayjs(through).format('YYYY-MM-DD')}`)).map(toWorkout);
    },
    async get_all() {
        const workouts = (await get('/workouts')).map(toWorkout);
        const events = await personalRecordService.getWorkoutEvents(workouts.map(workout => workout.id));
        attachRecordEvents(workouts, events);
        return workouts;
    },
    async get_dashboard(date) {
        const data = await get(`/workouts/dashboard?date=${dayjs(date).format('YYYY-MM-DD')}`);
        const currentWorkouts = data.currentWorkouts.map(toWorkout);
        const previousWeekWorkouts = data.previousWeekWorkouts.map(toWorkout);
        const preloadWorkouts = data.preloadWorkouts.map(toWorkout);
        attachRecordEvents([...currentWorkouts, ...previousWeekWorkouts], data.recordEvents);
        return {currentWorkouts, previousWeekWorkouts, preloadWorkouts, days: data.days};
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
