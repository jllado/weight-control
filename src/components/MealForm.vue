<template>
  <Dialog id="meal-form" appendTo="body" header="Meal" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
    <br>
    <div v-if="!fixed_date" class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar v-model="vv.date.$model" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :maxDate="max_date" />
        <label>Date</label>
      </span>
      <span class="error">{{ vv.date?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Dropdown inputId="meal-type" v-model="vv.mealType.$model" :options="available_meal_types" optionLabel="label" optionValue="value" appendTo="body" class="entry-dropdown" />
        <label for="meal-type">Meal</label>
      </span>
      <span class="error">{{ vv.mealType?.$errors[0]?.$message }}</span>
    </div>
    <div v-if="!meal && previous_meals.length" class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Dropdown inputId="reuse-meal" v-model="selected_meal" :options="previous_meals" optionLabel="label" appendTo="body" showClear @change="copy_previous_meal" />
        <label for="reuse-meal">Copy a previous meal</label>
      </span>
    </div>
    <div v-if="calorie_shortcuts.length" class="p-flex-row p-pb-5">
      <div class="meal-shortcut-label">Shortcuts</div>
      <div class="meal-shortcut-buttons">
        <Button v-for="shortcut in calorie_shortcuts" :key="shortcut.key" :label="shortcut.label" class="p-button-sm p-button-outlined" @click="apply_shortcut(shortcut.calories)" />
      </div>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar inputId="meal-time" v-model="vv.mealTime.$model" appendTo="body" :timeOnly="true" hourFormat="24" :stepMinute="5" showButtonBar />
        <label for="meal-time">{{ has_ongoing_fast ? 'Time' : 'Time (optional)' }}</label>
      </span>
      <span class="error">{{ vv.mealTime?.$errors[0]?.$message }}</span>
    </div>
    <div v-if="!vv.dishes.$model.length" class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <InputNumber inputId="calories" v-model="vv.calories.$model" :min="0" />
        <label for="calories">Calories</label>
      </span>
      <span class="error">{{ vv.calories?.$errors[0]?.$message }}</span>
    </div>
    <div v-if="!vv.dishes.$model.length" class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <InputNumber inputId="protein-grams" v-model="vv.proteinGrams.$model" mode="decimal" :min="0" :maxFractionDigits="2" />
        <label for="protein-grams">Protein (g)</label>
      </span>
      <span class="error">{{ vv.proteinGrams?.$errors[0]?.$message }}</span>
    </div>
    <div v-if="!vv.dishes.$model.length" class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <InputNumber inputId="carbohydrate-grams" v-model="vv.carbohydrateGrams.$model" mode="decimal" :min="0" :maxFractionDigits="2" />
        <label for="carbohydrate-grams">Carbohydrates (g)</label>
      </span>
      <span class="error">{{ vv.carbohydrateGrams?.$errors[0]?.$message }}</span>
    </div>
    <div v-if="!vv.dishes.$model.length" class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <InputNumber inputId="fat-grams" v-model="vv.fatGrams.$model" mode="decimal" :min="0" :maxFractionDigits="2" />
        <label for="fat-grams">Fat (g)</label>
      </span>
      <span class="error">{{ vv.fatGrams?.$errors[0]?.$message }}</span>
    </div>
    <div class="meal-dishes p-flex-row p-pb-5">
      <div class="meal-dishes-header">
        <strong>Dishes (optional)</strong>
        <Button label="Add dish" icon="pi pi-plus" class="p-button-sm p-button-outlined" @click="add_dish" />
      </div>
      <div v-if="previous_dishes.length" class="meal-dish-reuse">
        <Dropdown inputId="reuse-dish" v-model="selected_dish" :options="previous_dishes" optionLabel="label" placeholder="Reuse a previous dish" appendTo="body" showClear @change="reuse_dish" />
      </div>
      <div v-for="(dish, index) in vv.dishes.$model" :key="dish.key" class="meal-dish-card">
        <div class="meal-dish-card-header">
          <strong>Dish {{ index + 1 }}</strong>
          <Button icon="pi pi-trash" :aria-label="`Remove dish ${index + 1}`" class="p-button-rounded p-button-text p-button-danger" @click="remove_dish(index)" />
        </div>
        <span class="p-float-label meal-dish-name"><InputText :id="`dish-name-${index}`" v-model="dish.name" /><label :for="`dish-name-${index}`">Dish</label></span>
        <div class="meal-dish-nutrition-fields">
          <span class="p-float-label"><InputNumber :inputId="`dish-calories-${index}`" v-model="dish.calories" :min="0" /><label :for="`dish-calories-${index}`">Calories</label></span>
          <span class="p-float-label"><InputNumber :inputId="`dish-protein-${index}`" v-model="dish.proteinGrams" mode="decimal" :min="0" :maxFractionDigits="2" /><label :for="`dish-protein-${index}`">Protein (g)</label></span>
          <span class="p-float-label"><InputNumber :inputId="`dish-carbohydrates-${index}`" v-model="dish.carbohydrateGrams" mode="decimal" :min="0" :maxFractionDigits="2" /><label :for="`dish-carbohydrates-${index}`">Carbohydrates (g)</label></span>
          <span class="p-float-label"><InputNumber :inputId="`dish-fat-${index}`" v-model="dish.fatGrams" mode="decimal" :min="0" :maxFractionDigits="2" /><label :for="`dish-fat-${index}`">Fat (g)</label></span>
        </div>
      </div>
      <div v-if="vv.dishes.$model.length" class="meal-dish-total" role="status">Calculated total: {{ calculated_calories }} kcal<span v-if="calculated_macro_summary"> · {{ calculated_macro_summary }}</span></div>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <InputText id="meal-notes" v-model="vv.notes.$model" />
        <label for="meal-notes">Notes (optional)</label>
      </span>
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="save" />
      <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import service from '../services/MealService';
import {reactive, toRef} from "vue";
import {useVuelidate} from "@vuelidate/core";
import {minValue, required, requiredIf} from "@vuelidate/validators";
import Meal, {MealType, mealTypeOptions} from "@/model/Meal";
import {calorieShortcutOptions} from "@/model/UserProfile";
import {userState} from '../state';
import dayjs from 'dayjs';

export default {
  name: "MealForm",
  emits: ["onSave", "onClose"],
  props: {
    show: Boolean,
    meal: Object,
    meals: {
      type: Array,
      default: () => []
    },
    fasting_periods: {
      type: Array,
      default: () => []
    },
    initial_date: Date,
    fixed_date: Boolean
  },
  data() {
    const locale = {
      firstDayOfWeek: 1,
      dayNames: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"],
      dayNamesShort: ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"],
      dayNamesMin: ["Su","Mo","Tu","We","Th","Fr","Sa"],
      monthNames: ["January","February","March","April","May","June","July","August","September","October","November","December"],
      monthNamesShort: ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"],
      today: 'Today',
      clear: 'Clear',
      dateFormat: 'mm/dd/yy',
      weekHeader: 'Wk'
    };
    const fform = reactive({
      date: this.initial_date || new Date(),
      mealType: null,
      mealTime: null,
      calories: null,
      proteinGrams: null,
      carbohydrateGrams: null,
      fatGrams: null,
      notes: '',
      dishes: []
    });
    const rules = {
      date: {required},
      mealType: {required},
      mealTime: {required: requiredIf(() => this.has_ongoing_fast)},
      calories: {required, minValue: minValue(0)},
      proteinGrams: {minValue: minValue(0)},
      carbohydrateGrams: {minValue: minValue(0)},
      fatGrams: {minValue: minValue(0)},
      notes: {},
      dishes: {}
    };
    const vv = useVuelidate(rules, Object.fromEntries(Object.keys(fform).map(key => [key, toRef(fform, key)])));
    return {
      vv,
      fform,
      custom_locale: locale,
      display_modal: this.show,
      max_date: new Date(),
      state: userState(),
      selected_dish: null,
      selected_meal: null
    }
  },
  computed: {
    has_ongoing_fast() {
      return this.fasting_periods.some(period => period.source === 'AUTOMATIC' && !period.endTime);
    },
    available_meal_types() {
      return mealTypeOptions.filter(option => option.value === MealType.SNACK || !this.meals.some(meal =>
          meal.id !== this.meal?.id && meal.mealType === option.value && dayjs(meal.date).isSame(this.vv.date.$model, 'day')
      ));
    },
    calorie_shortcuts() {
      if (this.vv.mealType.$model !== MealType.LUNCH && this.vv.mealType.$model !== MealType.DINNER) {
        return [];
      }
      return calorieShortcutOptions.map(shortcut => {
        const dailyCalories = this.state.user.profile.calorieShortcuts[shortcut.key];
        const lunchCalories = Math.floor(dailyCalories / 2);
        const calories = this.vv.mealType.$model === MealType.LUNCH ? lunchCalories : dailyCalories - lunchCalories;
        return {...shortcut, calories, label: `${shortcut.label} · ${calories.toLocaleString('en-US')} kcal`};
      });
    }
    ,
    previous_dishes() {
      const dishes = new Map();
      this.meals.forEach(meal => (meal.dishes || []).forEach(dish => {
        const key = dish.name.trim().toLowerCase();
        if (!dishes.has(key)) {
          dishes.set(key, {...dish, label: `${dish.name} · ${dish.calories} kcal`});
        }
      }));
      return [...dishes.values()];
    },
    previous_meals() {
      return this.meals.filter(candidate => candidate.id !== this.meal?.id && this.available_meal_types.some(option => option.value === candidate.mealType))
          .map(candidate => ({...candidate, label: `${candidate.dateFormat} · ${candidate.label()}`}));
    },
    calculated_calories() {
      return this.vv.dishes.$model.reduce((total, dish) => total + (dish.calories || 0), 0);
    },
    calculated_macro_summary() {
      const dishes = this.vv.dishes.$model;
      const macro = (key, label) => dishes.some(dish => dish[key] === null || dish[key] === undefined) ? null : `${label} ${dishes.reduce((total, dish) => total + dish[key], 0)} g`;
      return [macro('proteinGrams', 'P'), macro('carbohydrateGrams', 'C'), macro('fatGrams', 'F')].filter(value => value).join(' · ');
    }
  },
  watch: {
    show(value) {
      this.display_modal = value;
      if (value) {
        this.load_form();
      }
    },
    meal() {
      if (this.display_modal) {
        this.load_form();
      }
    },
    initial_date() {
      if (this.display_modal && !this.meal) {
        this.load_form();
      }
    }
  },
  methods: {
    apply_shortcut(calories) {
      this.vv.calories.$model = calories;
    },
    load_form() {
      this.vv.date.$model = this.meal?.date || this.initial_date || new Date();
      this.vv.mealType.$model = this.meal?.mealType || null;
      this.vv.mealTime.$model = this.meal?.mealTime || null;
      this.vv.calories.$model = this.meal?.calories ?? null;
      this.vv.proteinGrams.$model = this.meal?.proteinGrams ?? null;
      this.vv.carbohydrateGrams.$model = this.meal?.carbohydrateGrams ?? null;
      this.vv.fatGrams.$model = this.meal?.fatGrams ?? null;
      this.vv.notes.$model = this.meal?.notes || '';
      this.vv.dishes.$model = (this.meal?.dishes || []).map(dish => ({...dish, key: dish.id || crypto.randomUUID()}));
    },
    clear() {
      this.vv.date.$model = this.initial_date || new Date();
      this.vv.mealType.$model = null;
      this.vv.mealTime.$model = null;
      this.vv.calories.$model = null;
      this.vv.proteinGrams.$model = null;
      this.vv.carbohydrateGrams.$model = null;
      this.vv.fatGrams.$model = null;
      this.vv.notes.$model = '';
      this.vv.dishes.$model = [];
      this.vv.$reset();
    },
    add_dish() {
      const firstDish = this.vv.dishes.$model.length === 0;
      this.vv.dishes.$model.push({
        key: crypto.randomUUID(), name: '', calories: firstDish ? this.vv.calories.$model : null,
        proteinGrams: firstDish ? this.vv.proteinGrams.$model : null,
        carbohydrateGrams: firstDish ? this.vv.carbohydrateGrams.$model : null,
        fatGrams: firstDish ? this.vv.fatGrams.$model : null
      });
    },
    remove_dish(index) {
      const calories = this.calculated_calories;
      const dishes = this.vv.dishes.$model;
      const sumMacro = key => dishes.some(dish => dish[key] === null || dish[key] === undefined) ? null : dishes.reduce((total, dish) => total + dish[key], 0);
      const protein = sumMacro('proteinGrams');
      const carbohydrates = sumMacro('carbohydrateGrams');
      const fat = sumMacro('fatGrams');
      this.vv.dishes.$model.splice(index, 1);
      if (this.vv.dishes.$model.length === 0) {
        this.vv.calories.$model = calories;
        this.vv.proteinGrams.$model = protein;
        this.vv.carbohydrateGrams.$model = carbohydrates;
        this.vv.fatGrams.$model = fat;
      }
    },
    reuse_dish() {
      if (!this.selected_dish) return;
      this.vv.dishes.$model.push({...this.selected_dish, key: crypto.randomUUID()});
      this.selected_dish = null;
    },
    copy_previous_meal() {
      if (!this.selected_meal) return;
      const source = this.selected_meal;
      this.vv.mealType.$model = source.mealType;
      this.vv.mealTime.$model = source.mealTime;
      this.vv.calories.$model = source.calories;
      this.vv.proteinGrams.$model = source.proteinGrams;
      this.vv.carbohydrateGrams.$model = source.carbohydrateGrams;
      this.vv.fatGrams.$model = source.fatGrams;
      this.vv.notes.$model = source.notes || '';
      this.vv.dishes.$model = (source.dishes || []).map(dish => ({...dish, key: crypto.randomUUID()}));
      this.selected_meal = null;
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      const meal = new Meal();
      meal.id = this.meal?.id || null;
      meal.date = this.vv.date.$model;
      meal.mealType = this.vv.mealType.$model;
      meal.mealTime = this.vv.mealTime.$model;
      meal.calories = this.vv.calories.$model;
      meal.proteinGrams = this.vv.proteinGrams.$model;
      meal.carbohydrateGrams = this.vv.carbohydrateGrams.$model;
      meal.fatGrams = this.vv.fatGrams.$model;
      meal.notes = this.vv.notes.$model || null;
      meal.dishes = this.vv.dishes.$model.map(dish => ({
        name: dish.name,
        calories: dish.calories,
        proteinGrams: dish.proteinGrams,
        carbohydrateGrams: dish.carbohydrateGrams,
        fatGrams: dish.fatGrams
      }));
      if (meal.dishes.length) {
        meal.calories = this.calculated_calories;
        meal.proteinGrams = this.vv.dishes.$model.some(dish => dish.proteinGrams === null) ? null : this.vv.dishes.$model.reduce((total, dish) => total + dish.proteinGrams, 0);
        meal.carbohydrateGrams = this.vv.dishes.$model.some(dish => dish.carbohydrateGrams === null) ? null : this.vv.dishes.$model.reduce((total, dish) => total + dish.carbohydrateGrams, 0);
        meal.fatGrams = this.vv.dishes.$model.some(dish => dish.fatGrams === null) ? null : this.vv.dishes.$model.reduce((total, dish) => total + dish.fatGrams, 0);
      }
      try {
        await service.save(meal.toObject());
        this.$emit('onSave');
        this.$toast.add({severity:'success', summary: 'Meal saved', life: 3000});
        this.close_modal();
      } catch (e) {
        this.handle_error(e);
      }
    },
    close_modal() {
      this.clear();
      this.$emit('onClose');
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
}
</script>

<style scoped>
.entry-dropdown {
  width: 100%;
}
#meal-form .p-inputnumber,
#meal-form .p-inputnumber-input {
  width: 100%;
}
.meal-shortcut-label {
  margin-bottom: 0.5rem;
}
.meal-shortcut-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.meal-dishes {
  width: 100%;
}
.meal-dishes-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  justify-content: space-between;
  margin-bottom: 1rem;
  width: 100%;
}
.meal-dish-reuse,
.meal-dish-card,
.meal-dish-total {
  margin-bottom: 1rem;
}
.meal-dish-card {
  border: 1px solid var(--surface-border);
  border-radius: 4px;
  padding: 1rem;
}
.meal-dish-card-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 1rem;
}
.meal-dish-name {
  display: block;
  margin-bottom: 1rem;
}
.meal-dish-name .p-inputtext {
  width: 100%;
}
.meal-dish-nutrition-fields {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.meal-dish-total {
  background: var(--surface-ground);
  border-radius: 4px;
  font-weight: 600;
  padding: 0.75rem 1rem;
}
@media (max-width: 960px) {
  .meal-dish-nutrition-fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
