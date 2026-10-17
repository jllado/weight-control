import {reactive} from 'vue';
import dayjs from 'dayjs';
import {createPhaseTimer, pausePhaseTimer, switchPhaseTimer} from './WorkoutTimerService';

export const guidedWorkoutState = reactive({draft: null, editor: null, resumeRequest: 0, discardRequest: 0, startRequest: 0, startSource: null});
let storageKey;
let accountGeneration = 0;
let pendingEditor;
let releaseEditor;
let editorReleased = Promise.resolve();

function persist() {
    if (guidedWorkoutState.draft) localStorage.setItem(storageKey, JSON.stringify(guidedWorkoutState.draft));
    else localStorage.removeItem(storageKey);
}

export function selectGuidedWorkoutAccount(email) {
    accountGeneration += 1;
    closeGuidedWorkoutEditor();
    storageKey = email ? `guided-workout-v1:${email}` : null;
    guidedWorkoutState.draft = storageKey ? JSON.parse(localStorage.getItem(storageKey) || 'null') : null;
}

window.addEventListener('storage', event => {
    if (storageKey && event.key === storageKey && !guidedWorkoutState.editor) guidedWorkoutState.draft = JSON.parse(event.newValue || 'null');
});

export async function openGuidedWorkoutEditor(editor) {
    if (guidedWorkoutState.editor === editor) return true;
    if (guidedWorkoutState.editor) return false;
    const key = storageKey;
    const generation = accountGeneration;
    if (pendingEditor) {
        if (pendingEditor.editor === editor && pendingEditor.key === key && pendingEditor.generation === generation) return pendingEditor.promise;
        await pendingEditor.promise;
        if (generation !== accountGeneration) return false;
        return openGuidedWorkoutEditor(editor);
    }
    const acquisition = (async () => {
        await editorReleased;
        if (generation !== accountGeneration) return false;
        return new Promise((resolve, reject) => {
            editorReleased = navigator.locks.request(`${key}:editor`, {ifAvailable: true}, async lock => {
                if (!lock || generation !== accountGeneration) { resolve(false); return; }
                guidedWorkoutState.draft = JSON.parse(localStorage.getItem(key) || 'null');
                guidedWorkoutState.editor = editor;
                await new Promise(release => { releaseEditor = release; resolve(true); });
            }).catch(reject);
        });
    })();
    pendingEditor = {editor, key, generation, promise: acquisition.finally(() => { pendingEditor = null; })};
    return pendingEditor.promise;
}

export function closeGuidedWorkoutEditor(editor) {
    if (editor && guidedWorkoutState.editor !== editor) return;
    if (releaseEditor) releaseEditor();
    releaseEditor = null;
    guidedWorkoutState.editor = null;
}

export function guidedPhaseKey(line) {
    if (line.exerciseType === 'WARM_UP') return 'warmUpMinutes';
    if (line.exerciseType === 'STRETCHING') return 'stretchingMinutes';
    return line.trackingMode === 'CARDIO' ? 'cardioMinutes' : 'trainingMinutes';
}

export function estimateGuidedWorkout(workout, overrides = []) {
    return workout.plannedTargets.map((line, lineIndex) => line.segments.map(segment => {
        if (overrides[lineIndex] != null) return overrides[lineIndex];
        if (line.stretchingUnit === 'BREATHS') return 45;
        if (segment.durationSeconds > 0) return segment.durationSeconds;
        if (segment.repetitions > 0) return segment.repetitions * 3;
        return 45;
    }));
}

export function guidedWorkoutSteps(workout) {
    const lines = workout.lines;
    const steps = [];
    for (let index = 0; index < lines.length;) {
        const line = lines[index];
        if (!line.supersetGroupId) {
            line.segments.forEach((segment, segmentIndex) => { if (!segment.skipped && !line.omitFromActual) steps.push({lineIndex: index, segmentIndex}); });
            index += 1;
            continue;
        }
        const members = [];
        while (index < lines.length && lines[index].supersetGroupId === line.supersetGroupId) { members.push(index); index += 1; }
        const rounds = Math.max(...members.map(lineIndex => lines[lineIndex].segments.length));
        for (let round = 0; round < rounds; round += 1) members.forEach(lineIndex => {
            const segment = lines[lineIndex].segments[round];
            if (segment && !segment.skipped && !lines[lineIndex].omitFromActual) steps.push({lineIndex, segmentIndex: round});
        });
    }
    return steps;
}

export function guidedWorkoutProgressLabel(draft) {
    const steps = guidedWorkoutSteps(draft.workout);
    if (!steps.length) return draft.workout.saunaSession ? 'Sauna rounds · Ready to review' : 'Ready to review';
    return `${Math.min((draft.currentStep || 0) + 1, steps.length)} of ${steps.length} sets`;
}

export function guidedWorkoutExerciseProgress(draft) {
    const rows = [
        {key: 'warmUp', label: 'Warm-up', phase: 'warmUpMinutes', done: 0, now: 0, ahead: 0},
        {key: 'training', label: 'Training', phase: 'trainingMinutes', done: 0, now: 0, ahead: 0},
        {key: 'cardio', label: 'Cardio', phase: 'cardioMinutes', done: 0, now: 0, ahead: 0},
        {key: 'stretching', label: 'Stretching', phase: 'stretchingMinutes', done: 0, now: 0, ahead: 0}
    ];
    const stepsByLine = new Map();
    const steps = guidedWorkoutSteps(draft.workout);
    steps.forEach((step, stepIndex) => {
        if (!stepsByLine.has(step.lineIndex)) stepsByLine.set(step.lineIndex, []);
        stepsByLine.get(step.lineIndex).push(stepIndex);
    });
    const cursor = draft.currentStep || 0;

    stepsByLine.forEach((stepIndexes, lineIndex) => {
        const line = draft.workout.lines[lineIndex];
        const phase = guidedPhaseKey(line);
        const row = rows.find(candidate => candidate.phase === phase);
        if (lineIndex === steps[cursor]?.lineIndex) row.now += 1;
        else if (stepIndexes.some(stepIndex => stepIndex > cursor)) row.ahead += 1;
        else if (stepIndexes.some(stepIndex => stepIndex < cursor)) row.done += 1;
    });

    return rows;
}

export function createGuidedWorkoutDraft(workout, now = Date.now()) {
    const estimatedSecondsByLine = estimateGuidedWorkout(workout, workout.estimatedSecondsPerSetByLine);
    delete workout.estimatedSecondsPerSetByLine;
    Object.assign(workout, {startTime: dayjs(now).format('HH:mm'), endTime: null, durationMinutes: null,
        warmUpMinutes: null, trainingMinutes: null, cardioMinutes: null, stretchingMinutes: null});
    workout.lines.forEach(line => {
        line.exerciseDurationMilliseconds = 0;
        line.exerciseElapsedBySegment = [];
        line.omitFromActual = false;
    });
    const timer = createPhaseTimer({});
    if (workout.lines.length) switchPhaseTimer(timer, guidedPhaseKey(workout.lines[0]), now);
    guidedWorkoutState.draft = {
        workout,
        estimatedSecondsByLine,
        currentStep: 0,
        timer,
        exerciseTimer: {lineIndex: workout.lines.length ? 0 : null, segmentIndex: workout.lines.length ? 0 : null, pendingMilliseconds: 0, startedAt: workout.lines.length && timer.runningPhase ? now : null},
        restTimer: null,
        recordingKey: crypto.randomUUID()
    };
    ensureGuidedDraftIdentity(guidedWorkoutState.draft);
    persist();
}

const keyForGuidedEdit = () => crypto.randomUUID();

export function ensureGuidedDraftIdentity(draft) {
    draft.workout.lines.forEach(line => {
        line.guidedLineKey ||= keyForGuidedEdit();
        line.segments.forEach(segment => { segment.guidedSegmentKey ||= keyForGuidedEdit(); });
    });
    if (!draft.plannedTargetsBySegmentKey) {
        draft.plannedTargetsBySegmentKey = {};
        draft.workout.lines.forEach((line, lineIndex) => line.segments.forEach((segment, segmentIndex) => {
            const target = draft.workout.plannedTargets[lineIndex]?.segments[segmentIndex];
            if (target) draft.plannedTargetsBySegmentKey[segment.guidedSegmentKey] = {...target};
        }));
    }
    if (!draft.completedSegmentKeys) {
        const steps = guidedWorkoutSteps(draft.workout);
        draft.completedSegmentKeys = steps.slice(0, draft.currentStep || 0).map(step => draft.workout.lines[step.lineIndex].segments[step.segmentIndex].guidedSegmentKey);
    }
    draft.preparedRest ??= null;
    if (draft.preparedRest?.completedStep != null) {
        const step = guidedWorkoutSteps(draft.workout)[draft.preparedRest.completedStep];
        draft.preparedRest = {phase: guidedPhaseKey(draft.workout.lines[step.lineIndex]), phaseWasRunning: draft.preparedRest.phaseWasRunning ?? true};
    }
}

function guidedEditEstimate(line, segment, planned) {
    if (planned?.durationSeconds > 0) return planned.durationSeconds;
    if (planned?.repetitions > 0) return planned.repetitions * 3;
    if (line.stretchingUnit === 'BREATHS') return 45;
    if (segment.durationSeconds > 0) return segment.durationSeconds;
    if (segment.repetitions > 0) return segment.repetitions * 3;
    return 45;
}

export function applyGuidedWorkoutEdit(draft, form, now = Date.now()) {
    ensureGuidedDraftIdentity(draft);
    const oldSteps = guidedWorkoutSteps(draft.workout);
    const oldExecutionKeys = oldSteps.map(step => draft.workout.lines[step.lineIndex].segments[step.segmentIndex].guidedSegmentKey);
    const oldCurrent = oldSteps[draft.currentStep] || null;
    const oldCurrentKey = oldCurrent && draft.workout.lines[oldCurrent.lineIndex].segments[oldCurrent.segmentIndex].guidedSegmentKey;
    const elapsedTimer = draft.exerciseTimer;
    if (elapsedTimer.lineIndex != null && elapsedTimer.segmentIndex != null) {
        const line = draft.workout.lines[elapsedTimer.lineIndex];
        const elapsed = elapsedTimer.pendingMilliseconds + (elapsedTimer.startedAt == null ? 0 : Math.max(0, now - elapsedTimer.startedAt));
        line.exerciseElapsedBySegment[elapsedTimer.segmentIndex] = elapsed;
        line.exerciseDurationMilliseconds = line.exerciseElapsedBySegment.reduce((sum, value) => sum + (value || 0), 0);
    }
    const oldSegmentByKey = new Map(draft.workout.lines.flatMap(line => line.segments.map((segment, index) => [segment.guidedSegmentKey, {line, segment, elapsed: line.exerciseElapsedBySegment[index] || 0, estimate: draft.estimatedSecondsByLine[draft.workout.lines.indexOf(line)][index]}])));
    const oldLineByKey = new Map(draft.workout.lines.map(line => [line.guidedLineKey, line]));
    const oldKeys = new Set(oldSegmentByKey.keys());
    const lines = form.lines.map(line => ({
        ...line,
        guidedLineKey: line.guidedLineKey || keyForGuidedEdit(),
        imageUrl: line.imageUrl ?? oldLineByKey.get(line.guidedLineKey)?.imageUrl,
        exerciseDurationMilliseconds: oldLineByKey.get(line.guidedLineKey)?.exerciseDurationMilliseconds || 0,
        exerciseElapsedBySegment: [],
        omitFromActual: false,
        segments: line.segments.map(segment => {
            const guidedSegmentKey = segment.guidedSegmentKey || keyForGuidedEdit();
            return {
                guidedSegmentKey,
                skipped: segment.skipped || false,
                repetitions: segment.repetitions,
                breaths: segment.breaths,
                durationSeconds: line.trackingMode === 'REPS' || line.stretchingUnit === 'BREATHS' ? null : (segment.durationMinutes || 0) * 60 + (segment.durationRemainder || 0),
                guidedMinutes: segment.durationMinutes || 0,
                guidedSeconds: segment.durationRemainder || 0,
                weight: segment.weight,
                speedKph: segment.speedKph,
                cadenceRpm: segment.cadenceRpm,
                distanceKm: segment.distanceKm,
                inclinePercent: segment.inclinePercent,
                resistanceLevel: segment.resistanceLevel,
            };
        })
    }));
    const estimateByLine = lines.map(line => line.segments.map(segment => {
        const original = oldSegmentByKey.get(segment.guidedSegmentKey);
        if (original) return original.estimate;
        return guidedEditEstimate(line, segment, draft.plannedTargetsBySegmentKey[segment.guidedSegmentKey]);
    }));
    lines.forEach(line => {
        line.exerciseElapsedBySegment = line.segments.map(segment => oldSegmentByKey.get(segment.guidedSegmentKey)?.elapsed || 0);
        line.exerciseDurationMilliseconds = line.exerciseElapsedBySegment.reduce((sum, value) => sum + value, 0);
    });
    const completed = new Set(draft.completedSegmentKeys);
    const nextDraft = {
        ...draft,
        workout: {...draft.workout, note: form.note || null, plannedSessionName: form.plannedSessionName || null,
            saunaSession: form.saunaSession, saunaRoundsMinutes: [...form.saunaRoundsMinutes],
            plannedSaunaRoundsMinutes: form.plannedSaunaRoundsMinutes, lines, plannedTargets: draft.workout.plannedTargets},
        estimatedSecondsByLine: estimateByLine,
        completedSegmentKeys: [...completed].filter(key => lines.some(line => line.segments.some(segment => segment.guidedSegmentKey === key)))
    };
    const steps = guidedWorkoutSteps(nextDraft.workout);
    const segmentKeyAt = step => nextDraft.workout.lines[step.lineIndex].segments[step.segmentIndex].guidedSegmentKey;
    let chosenKey = oldCurrentKey && steps.some(step => segmentKeyAt(step) === oldCurrentKey) ? oldCurrentKey : null;
    if (!chosenKey) chosenKey = oldExecutionKeys.find(key => oldKeys.has(key) && !completed.has(key) && steps.some(step => segmentKeyAt(step) === key)) || null;
    if (!chosenKey) chosenKey = steps.map(segmentKeyAt).find(key => !oldKeys.has(key) && !completed.has(key)) || null;
    const currentIndex = chosenKey ? steps.findIndex(step => segmentKeyAt(step) === chosenKey) : steps.length;
    nextDraft.currentStep = currentIndex;
    nextDraft.reviewing = !chosenKey;
    nextDraft.workout.endTime = chosenKey || nextDraft.workout.saunaSession ? null : new Date(now).toISOString();
    const current = steps[currentIndex];
    const currentPhase = current && guidedPhaseKey(nextDraft.workout.lines[current.lineIndex]);
    nextDraft.exerciseTimer = {
        lineIndex: current?.lineIndex ?? null,
        segmentIndex: current?.segmentIndex ?? null,
        pendingMilliseconds: current ? nextDraft.workout.lines[current.lineIndex].exerciseElapsedBySegment[current.segmentIndex] || 0 : 0,
        startedAt: current && draft.timer?.runningPhase && !draft.restTimer ? now : null
    };
    if (draft.timer?.runningPhase && !current) pausePhaseTimer(draft.timer, now);
    else if (draft.timer?.runningPhase && draft.timer.runningPhase !== currentPhase) switchPhaseTimer(draft.timer, currentPhase, now);
    Object.assign(draft, nextDraft);
    persist();
}

export function saveGuidedWorkoutDraft() { persist(); }
export function discardGuidedWorkoutDraft() { guidedWorkoutState.draft = null; persist(); }
export function discardSavedGuidedWorkoutDraft(email, recordingKey) {
    const key = `guided-workout-v1:${email}`;
    const stored = JSON.parse(localStorage.getItem(key));
    if (stored?.recordingKey === recordingKey) localStorage.removeItem(key);
    if (storageKey === key && guidedWorkoutState.draft?.recordingKey === recordingKey) guidedWorkoutState.draft = null;
}
export function resumeGuidedWorkout() { guidedWorkoutState.resumeRequest += 1; }
export function requestGuidedWorkoutDiscard() { guidedWorkoutState.discardRequest += 1; }
export function startGuidedWorkout(source) { guidedWorkoutState.startSource = source; guidedWorkoutState.startRequest += 1; }
