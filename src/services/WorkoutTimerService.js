import {reactive} from 'vue';

export const workoutPhases = [
    {key: 'warmUpMinutes', label: 'Warm-up'},
    {key: 'trainingMinutes', label: 'Training'},
    {key: 'cardioMinutes', label: 'Cardio'},
    {key: 'stretchingMinutes', label: 'Stretching'}
];
export const timerState = reactive({draft: null, editor: null, resumeRequest: 0});
let storageKey;
let releaseEditor;
let editorReleased = Promise.resolve();

function persist() {
    if (timerState.draft) localStorage.setItem(storageKey, JSON.stringify(timerState.draft));
    else localStorage.removeItem(storageKey);
}

export function selectTimerAccount(email) {
    closeTimerEditor();
    storageKey = email ? `workout-timer-v1:${email}` : null;
    timerState.draft = storageKey ? JSON.parse(localStorage.getItem(storageKey) || 'null') : null;
}

window.addEventListener('storage', event => {
    if (storageKey && event.key === storageKey && !timerState.editor) timerState.draft = JSON.parse(event.newValue || 'null');
});

export async function openTimerEditor(editor) {
    if (timerState.editor === editor) return true;
    if (timerState.editor) return false;
    await editorReleased;
    return new Promise((resolve, reject) => {
        editorReleased = navigator.locks.request(`${storageKey}:editor`, {ifAvailable: true}, async lock => {
            if (!lock) { resolve(false); return; }
            // Read after acquiring the lock: another tab may have just updated the draft.
            timerState.draft = JSON.parse(localStorage.getItem(storageKey) || 'null');
            timerState.editor = editor;
            await new Promise(release => { releaseEditor = release; resolve(true); });
        }).catch(reject);
    });
}

export function closeTimerEditor(editor) {
    if (editor && timerState.editor !== editor) return;
    if (releaseEditor) releaseEditor();
    releaseEditor = null;
    timerState.editor = null;
}

export function createTimerDraft(form) {
    timerState.draft = {
        form: JSON.parse(JSON.stringify(form)),
        elapsed: Object.fromEntries(workoutPhases.map(({key}) => [key, (form[key] || 0) * 60000])),
        runningPhase: null,
        startedAt: null
    };
    persist();
}

export function saveTimerForm(form) {
    timerState.draft.form = JSON.parse(JSON.stringify(form));
    persist();
}

export function phaseMilliseconds(draft, key, now = Date.now()) {
    return draft.elapsed[key] + (draft.runningPhase === key ? Math.max(0, now - draft.startedAt) : 0);
}

export function stopPhase(now = Date.now()) {
    const draft = timerState.draft;
    if (draft.runningPhase) draft.elapsed[draft.runningPhase] = phaseMilliseconds(draft, draft.runningPhase, now);
    draft.runningPhase = null;
    draft.startedAt = null;
    persist();
}

export function startPhase(key, now = Date.now()) {
    stopPhase(now);
    timerState.draft.runningPhase = key;
    timerState.draft.startedAt = now;
    persist();
}

export function setPhaseMinutes(key, minutes) {
    timerState.draft.elapsed[key] = minutes * 60000;
    persist();
}

export function discardTimer() {
    timerState.draft = null;
    persist();
}

export function resumeTimer() { timerState.resumeRequest += 1; }

export function formatElapsed(milliseconds) {
    const seconds = Math.floor(milliseconds / 1000);
    return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60].map(value => String(value).padStart(2, '0')).join(':');
}
