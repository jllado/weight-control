import {reactive} from 'vue';
import dayjs from 'dayjs';
import {createPhaseTimer, switchPhaseTimer} from './WorkoutTimerService';

export const guidedWorkoutState = reactive({draft: null, editor: null, resumeRequest: 0, discardRequest: 0, startRequest: 0, startSource: null});
let storageKey;
let releaseEditor;
let editorReleased = Promise.resolve();

function persist() {
    if (guidedWorkoutState.draft) localStorage.setItem(storageKey, JSON.stringify(guidedWorkoutState.draft));
    else localStorage.removeItem(storageKey);
}

export function selectGuidedWorkoutAccount(email) {
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
    await editorReleased;
    return new Promise((resolve, reject) => {
        editorReleased = navigator.locks.request(`${storageKey}:editor`, {ifAvailable: true}, async lock => {
            if (!lock) { resolve(false); return; }
            guidedWorkoutState.draft = JSON.parse(localStorage.getItem(storageKey) || 'null');
            guidedWorkoutState.editor = editor;
            await new Promise(release => { releaseEditor = release; resolve(true); });
        }).catch(reject);
    });
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

export function guidedWorkoutSteps(workout) {
    const lines = workout.lines;
    const steps = [];
    for (let index = 0; index < lines.length;) {
        const line = lines[index];
        if (!line.supersetGroupId) {
            line.segments.forEach((_, segmentIndex) => steps.push({lineIndex: index, segmentIndex}));
            index += 1;
            continue;
        }
        const members = [];
        while (index < lines.length && lines[index].supersetGroupId === line.supersetGroupId) { members.push(index); index += 1; }
        for (let round = 0; round < line.segments.length; round += 1) members.forEach(lineIndex => steps.push({lineIndex, segmentIndex: round}));
    }
    return steps;
}

export function guidedWorkoutProgressLabel(draft) {
    const steps = guidedWorkoutSteps(draft.workout);
    if (!steps.length) return 'Sauna rounds · Ready to review';
    return `${Math.min((draft.currentStep || 0) + 1, steps.length)} of ${steps.length} sets`;
}

export function createGuidedWorkoutDraft(workout, now = Date.now()) {
    Object.assign(workout, {startTime: dayjs(now).format('HH:mm'), durationMinutes: null,
        warmUpMinutes: null, trainingMinutes: null, cardioMinutes: null, stretchingMinutes: null});
    const timer = createPhaseTimer({});
    if (workout.lines.length) switchPhaseTimer(timer, guidedPhaseKey(workout.lines[0]), now);
    guidedWorkoutState.draft = {
        workout,
        currentStep: 0,
        timer,
        recordingKey: crypto.randomUUID()
    };
    persist();
}

export function saveGuidedWorkoutDraft() { persist(); }
export function discardGuidedWorkoutDraft() { guidedWorkoutState.draft = null; persist(); }
export function resumeGuidedWorkout() { guidedWorkoutState.resumeRequest += 1; }
export function requestGuidedWorkoutDiscard() { guidedWorkoutState.discardRequest += 1; }
export function startGuidedWorkout(source) { guidedWorkoutState.startSource = source; guidedWorkoutState.startRequest += 1; }
