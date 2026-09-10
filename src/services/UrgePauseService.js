import {get, post} from './api';
import {celebrateDecisionResponse} from './DecisionOutcomeService';
import {notificationsChanged} from './InAppNotificationService';

export default {
    current() { return get('/urge-pauses'); },
    start(description) { return post('/urge-pauses', {description}); },
    async action(id, action, body = {}) {
        const response = await post(`/urge-pauses/${id}/${action}`, body);
        notificationsChanged();
        return response;
    },
    async finish(id, outcome, reason) {
        const response = await this.action(id, 'finish', {outcome, reason});
        if (outcome) celebrateDecisionResponse(response, outcome);
        return response;
    }
};
