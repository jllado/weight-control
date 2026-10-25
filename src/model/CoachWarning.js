const labels = {
    RECOVERY_STRAIN: 'Recovery strain', SLEEP_DISRUPTION: 'Disrupted sleep',
    MOOD_DECLINE: 'Lower mood', ROUTINE_DISRUPTION: 'Disrupted routines',
    NUTRITION_IMBALANCE: 'Nutrition imbalance', TRAINING_STRAIN: 'Training strain',
    PAIN_INCREASE: 'Increased pain', HEALTH_CHANGE: 'Health change'
};

export function warningLabel(type) { return labels[type]; }

export default class CoachWarning {
    constructor(data) { Object.assign(this, data); }
    get label() { return warningLabel(this.type); }
}
