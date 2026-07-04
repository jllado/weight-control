export const UserSex = {
    MALE: 'MALE',
    FEMALE: 'FEMALE'
};

export const UserFitnessLevel = {
    SEDENTARY: 'SEDENTARY',
    LOW_ACTIVE: 'LOW_ACTIVE',
    ACTIVE: 'ACTIVE',
    VERY_ACTIVE: 'VERY_ACTIVE'
};

export const userSexOptions = [
    {label: 'Male', value: UserSex.MALE},
    {label: 'Female', value: UserSex.FEMALE}
];

export const userFitnessLevelOptions = [
    {label: 'Sedentary', value: UserFitnessLevel.SEDENTARY},
    {label: 'Low Active', value: UserFitnessLevel.LOW_ACTIVE},
    {label: 'Active', value: UserFitnessLevel.ACTIVE},
    {label: 'Very Active', value: UserFitnessLevel.VERY_ACTIVE}
];

export const medicationOptions = [
    {label: 'No', value: false},
    {label: 'Yes', value: true}
];

export const typicalCaloriesDays = [
    'saturday',
    'sunday',
    'monday',
    'tuesday',
    'wednesday',
    'thursday',
    'friday'
];

function toTypicalCalories(source) {
    return {
        saturday: source?.saturday ?? null,
        sunday: source?.sunday ?? null,
        monday: source?.monday ?? null,
        tuesday: source?.tuesday ?? null,
        wednesday: source?.wednesday ?? null,
        thursday: source?.thursday ?? null,
        friday: source?.friday ?? null
    };
}

export default class UserProfile {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.birthDate = source.birthDate ? new Date(source.birthDate) : null;
        this.heightCm = source.heightCm ?? null;
        this.sex = source.sex ?? null;
        this.fitnessLevel = source.fitnessLevel ?? null;
        this.takesMedication = source.takesMedication ?? false;
        this.typicalCaloriesPerDay = toTypicalCalories(source.typicalCaloriesPerDay);
    }

    toObject() {
        return {
            birthDate: this.birthDate,
            heightCm: this.heightCm,
            sex: this.sex,
            fitnessLevel: this.fitnessLevel,
            takesMedication: this.takesMedication,
            typicalCaloriesPerDay: {...this.typicalCaloriesPerDay}
        };
    }
}
