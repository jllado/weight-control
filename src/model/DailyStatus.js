export default class DailyStatus {

    constructor(fbDoc) {
        if (fbDoc === undefined) {
            return;
        }
        let fbData = fbDoc.data();
        this.id = fbDoc.id;
        this.date = fbData.date;
        this.weight = fbData.weight;
        this.blood_pressure = fbData.blood_pressure;
        this.total_routines = fbData.total_routines;
        this.total_weight_routines = fbData.total_weight_routines;
        this.total_blood_pressure_routines = fbData.total_blood_pressure_routines;
        this.total_flexibility_routines = fbData.total_flexibility_routines;
        this.total_mind_routines = fbData.total_mind_routines;
        this.score_trend = fbData.score_trend;
        this.weight_score_trend = fbData.weight_score_trend;
        this.blood_pressure_score_trend = fbData.blood_pressure_score_trend;
        this.flexibility_score_trend = fbData.flexibility_score_trend;
        this.mind_score_trend = fbData.mind_score_trend;
        this.score = fbData.score;
        this.weight_score = fbData.weight_score;
        this.blood_pressure_score = fbData.blood_pressure_score;
        this.flexibility_score = fbData.flexibility_score;
        this.mind_score = fbData.mind_score;
        this.user = fbData.user;
    }

}

