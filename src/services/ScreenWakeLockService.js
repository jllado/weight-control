export function createScreenWakeLockController(onStatusChange) {
    let enabled = false;
    let active = false;
    let disposed = false;
    let generation = 0;
    let sentinel = null;
    let pendingRequest = null;
    let status = 'off';

    function setStatus(nextStatus) {
        if (status === nextStatus) return;
        status = nextStatus;
        onStatusChange(nextStatus);
    }

    function canKeepScreenOn() {
        return !disposed && enabled && active && document.visibilityState === 'visible';
    }

    async function releaseCurrent() {
        const current = sentinel;
        sentinel = null;
        if (current && !current.released) await current.release();
    }

    function onRelease(event) {
        if (sentinel !== event.currentTarget) return;
        sentinel = null;
        setStatus(canKeepScreenOn() ? 'released' : enabled && active ? 'paused' : 'off');
    }

    async function reconcile() {
        const requestGeneration = generation;
        if (!enabled || !active) {
            await releaseCurrent();
            setStatus('off');
            return;
        }
        if (document.visibilityState === 'hidden') {
            await releaseCurrent();
            setStatus('paused');
            return;
        }
        if (sentinel && !sentinel.released) {
            setStatus('active');
            return;
        }
        if (pendingRequest) return;
        if (!navigator.wakeLock) {
            setStatus('unsupported');
            return;
        }

        setStatus('requesting');
        const request = navigator.wakeLock.request('screen');
        pendingRequest = request;
        try {
            const nextSentinel = await request;
            if (pendingRequest === request) pendingRequest = null;
            if (requestGeneration !== generation || !canKeepScreenOn()) {
                await nextSentinel.release();
                if (!disposed) void reconcile();
                return;
            }
            sentinel = nextSentinel;
            sentinel.addEventListener('release', onRelease, {once: true});
            setStatus(sentinel.released ? 'released' : 'active');
        } catch {
            if (pendingRequest === request) pendingRequest = null;
            if (requestGeneration !== generation) {
                if (!disposed) void reconcile();
                return;
            }
            setStatus('unavailable');
        }
    }

    function update() {
        generation += 1;
        void reconcile();
    }

    function onVisibilityChange() { update(); }

    document.addEventListener('visibilitychange', onVisibilityChange);

    return {
        setEnabled(value) { enabled = value; update(); },
        setActive(value) { active = value; update(); },
        dispose() {
            disposed = true;
            generation += 1;
            document.removeEventListener('visibilitychange', onVisibilityChange);
            void releaseCurrent();
        }
    };
}
