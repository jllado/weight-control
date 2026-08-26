import mitt from 'mitt';
import {notificationsChanged} from './InAppNotificationService';

const events = mitt();

export function celebrateDecisionWin() {
    events.emit('requested', {type: 'DECISION_WIN', achievements: []});
}

export function celebrateDecisionMiss() {
    events.emit('requested', {type: 'DECISION_MISS', achievements: []});
}

export function celebratePersonalRecords(achievements) {
    if (achievements.length) {
        notificationsChanged();
        events.emit('requested', {type: 'PERSONAL_RECORDS', achievements});
    }
}

export function onCelebrationRequested(handler) {
    events.on('requested', handler);
    return () => events.off('requested', handler);
}
