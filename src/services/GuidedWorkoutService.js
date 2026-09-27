import {reactive} from 'vue';

export const guidedWorkoutState = reactive({draft: null, editor: null, resumeRequest: 0, startRequest: 0, startSource: null});
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

export function createGuidedWorkoutDraft(workout) {
    guidedWorkoutState.draft = {
        workout,
        currentStep: 0,
        recordingKey: crypto.randomUUID()
    };
    persist();
}

export function saveGuidedWorkoutDraft() { persist(); }
export function discardGuidedWorkoutDraft() { guidedWorkoutState.draft = null; persist(); }
export function resumeGuidedWorkout() { guidedWorkoutState.resumeRequest += 1; }
export function startGuidedWorkout(source) { guidedWorkoutState.startSource = source; guidedWorkoutState.startRequest += 1; }
