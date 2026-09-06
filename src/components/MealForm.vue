<template>
  <section id="meal-form" class="meal-editor-form" aria-label="Meal">
    <h1>{{ meal ? 'Edit meal' : 'New meal' }}</h1>
    <p v-if="fixed_date">{{ format_date(fform.date) }}</p>
    <br>
    <div v-if="!fixed_date" class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar inputId="meal-date" v-model="vv.date.$model" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :maxDate="max_date" />
        <label for="meal-date">Date</label>
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
    <div v-if="!meal" class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Dropdown inputId="reuse-meal" v-model="selected_meal" :options="previous_meals" optionLabel="label" appendTo="body" :disabled="!fform.mealType" placeholder="Start from scratch" emptyMessage="No earlier meals of this type." :panelStyle="{maxWidth: 'calc(100vw - 2rem)'}" @change="copy_previous_meal">
          <template #option="{option}"><div class="meal-preload-option"><strong>{{ option.title }}</strong><small>{{ option.dateFormat }}</small></div></template>
        </Dropdown>
        <label for="reuse-meal">Preload a previous meal</label>
      </span>
      <small v-if="!fform.mealType">Choose a meal type to see previous meals.</small>
    </div>
    <div v-if="!fform.dishes.length && calorie_shortcuts.length" class="p-flex-row p-pb-5">
      <div class="meal-shortcut-label">Shortcuts</div>
      <div class="meal-shortcut-buttons">
        <Button v-for="shortcut in calorie_shortcuts" :key="shortcut.key" :label="shortcut.label" class="p-button-sm p-button-outlined" @click="apply_shortcut(shortcut.calories)" />
      </div>
    </div>
    <div class="meal-timing p-pb-5">
      <div>
        <span class="p-float-label">
          <Calendar inputId="meal-time" v-model="vv.mealTime.$model" appendTo="body" :timeOnly="true" hourFormat="24" :stepMinute="5" showButtonBar />
          <label for="meal-time">{{ has_ongoing_fast ? 'Start time' : 'Start time (optional)' }}</label>
        </span>
        <span class="error">{{ vv.mealTime?.$errors[0]?.$message }}</span>
      </div>
      <div>
        <span class="p-float-label">
          <InputNumber inputId="meal-duration" v-model="vv.durationMinutes.$model" :min="1" :maxFractionDigits="0" :useGrouping="false" />
          <label for="meal-duration">Duration (minutes)</label>
        </span>
        <span class="error">{{ vv.durationMinutes?.$errors[0]?.$message }}</span>
      </div>
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
        <strong>Foods (optional)</strong>
        <Button label="Add food" icon="pi pi-plus" class="p-button-sm p-button-outlined" @click="add_dish" />
      </div>
      <FoodPicker class="meal-dish-reuse" :foods="previous_dishes" inputId="reuse-dish" @select="reuse_dish" />
      <DishRecipePicker ref="recipePicker" class="meal-dish-reuse" @apply="fform.dishes.push(...$event)" />
      <Button label="Save as dish" class="p-button-outlined" :disabled="!selected_foods.length" @click="create_recipe" />
      <p v-if="!fform.dishes.length">Add foods to calculate the meal total automatically.</p>
      <div v-for="(dish, index) in fform.dishes" :key="dish.key" class="meal-dish-row">
        <Checkbox v-model="selected_foods" :value="dish.key" :inputId="`select-food-${index}`" :ariaLabel="`Select food ${index + 1}`" />
        <div class="meal-dish-summary"><label :for="`select-food-${index}`"><strong>{{ dish.name }}</strong></label><span>{{ quantity_label(dish) }} · {{ dish.calories }} kcal</span><small>{{ macro_summary(dish) }}</small></div>
        <div class="meal-dish-actions"><Button icon="pi pi-pencil" :aria-label="`Edit food ${index + 1}`" class="p-button-text" @click="edit_dish(index)" /><Button icon="pi pi-trash" :aria-label="`Remove food ${index + 1}`" class="p-button-text p-button-danger" @click="remove_dish(index)" /></div>
      </div>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <InputText id="meal-notes" v-model="vv.notes.$model" />
        <label for="meal-notes">Notes (optional)</label>
      </span>
    </div>
    <p v-if="save_error" role="alert" class="error">{{ save_error }}</p>
    <footer class="meal-editor-footer">
      <div role="status"><strong>{{ fform.dishes.length ? calculated_calories : (fform.calories ?? 0) }} kcal</strong><div>{{ fform.dishes.length ? calculated_macro_summary : macro_summary(fform) }}</div></div>
      <div class="meal-dish-actions">
      <Button label="Save" icon="pi pi-check" :loading="saving" :disabled="!!dish_draft" @click="save" />
      <Button label="Cancel" icon="pi pi-times" :disabled="saving" @click="close_modal" class="p-button-secondary" />
      </div>
    </footer>
    <Dialog v-if="recipe_draft" header="Save as dish" :visible="true" modal appendTo="body" :closable="false" :closeOnEscape="false" :style="{width: 'min(40rem, 95vw)'}">
      <DishRecipeForm :recipe="recipe_draft" independent @saved="recipe_saved" @close="recipe_draft = null" />
    </Dialog>
    <DishForm v-if="dish_draft" :dish="dish_draft" @apply="apply_dish" @close="dish_draft = null" />
  </section>
</template>

<script>
import service from '../services/MealService';
import {reactive, toRef} from "vue";
import {useVuelidate} from "@vuelidate/core";
import {integer, minValue, required, requiredIf} from "@vuelidate/validators";
import Meal, {MealType, mealTypeOptions} from "@/model/Meal";
import {calorieShortcutOptions} from "@/model/UserProfile";
import {userState} from '../state';
import dayjs from 'dayjs';
import DishForm from './DishForm.vue';
import FoodPicker from './FoodPicker.vue';
import DishRecipePicker from './DishRecipePicker.vue';
import DishRecipeForm from './DishRecipeForm.vue';
import {normalizeDish, quantityLabel, macroSummary, previousFoods} from '../model/Dish';

export default {
  name: "MealForm",
  components: {DishForm, FoodPicker, DishRecipePicker, DishRecipeForm},
  emits: ["onSave", "onClose"],
  props: {
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
      durationMinutes: null,
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
      durationMinutes: {required: requiredIf(() => !!fform.mealTime), integer, minValue: minValue(1)},
      calories: {required: requiredIf(() => !fform.dishes.length), integer, minValue: minValue(0)},
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
      max_date: new Date(),
      state: userState(),
      selected_foods: [],
      recipe_draft: null,
      selected_meal: null,
      dish_draft: null,
      dish_index: null,
      saving: false,
      save_error: '',
      saved_snapshot: ''
    }
  },
  mounted() { this.load_form(); this.saved_snapshot = JSON.stringify(this.fform); },
  computed: {
    dirty() { return !!this.dish_draft || !!this.recipe_draft || JSON.stringify(this.fform) !== this.saved_snapshot; },
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
    previous_dishes() { return previousFoods(this.meals); },
    previous_meals() {
      return this.meals.filter(candidate => candidate.mealType === this.fform.mealType && dayjs(candidate.date).isBefore(this.fform.date, 'day'))
          .sort((left, right) => dayjs(right.date).valueOf() - dayjs(left.date).valueOf() || right.id - left.id)
          .slice(0, 14)
          .map(candidate => {
            const count = candidate.dishes.length;
            const title = count ? candidate.dishes[0].name + (count > 1 ? ` + ${count - 1} ${count === 2 ? 'food' : 'foods'}` : '') : 'No foods';
            return {...candidate, title, label: `${title} · ${candidate.dateFormat}`};
          });
    },
    calculated_calories() {
      return this.vv.dishes.$model.reduce((total, dish) => total + (dish.calories || 0), 0);
    },
    calculated_macro_summary() {
      const dishes = this.vv.dishes.$model;
      return macroSummary(Object.fromEntries(['proteinGrams', 'carbohydrateGrams', 'fatGrams'].map(key => [key, dishes.some(dish => dish[key] === null) ? null : Math.round(dishes.reduce((total, dish) => total + dish[key], 0) * 100) / 100])));
    }
  },
  methods: {
    quantity_label: quantityLabel,
    macro_summary: macroSummary,
    format_date(date) { return dayjs(date).format('DD/MM/YYYY'); },
    edit_dish(index) { this.dish_index = index; this.dish_draft = normalizeDish(this.fform.dishes[index]); },
    apply_dish(dish) {
      if (this.dish_index === null) this.fform.dishes.push(dish);
      else this.fform.dishes.splice(this.dish_index, 1, dish);
      this.dish_draft = null;
    },
    apply_shortcut(calories) {
      this.vv.calories.$model = calories;
    },
    load_form() {
      this.vv.date.$model = this.meal?.date || this.initial_date || new Date();
      this.vv.mealType.$model = this.meal?.mealType || null;
      this.vv.mealTime.$model = this.meal?.mealTime || null;
      this.vv.durationMinutes.$model = this.meal?.durationMinutes ?? null;
      this.vv.calories.$model = this.meal?.calories ?? null;
      this.vv.proteinGrams.$model = this.meal?.proteinGrams ?? null;
      this.vv.carbohydrateGrams.$model = this.meal?.carbohydrateGrams ?? null;
      this.vv.fatGrams.$model = this.meal?.fatGrams ?? null;
      this.vv.notes.$model = this.meal?.notes || '';
      this.vv.dishes.$model = (this.meal?.dishes || []).map(dish => ({...normalizeDish(dish), key: dish.id || crypto.randomUUID()}));
    },
    add_dish() {
      const firstDish = this.vv.dishes.$model.length === 0;
      this.dish_index = null;
      this.dish_draft = normalizeDish({
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
      this.selected_foods = this.selected_foods.filter(key => key !== this.fform.dishes[index].key);
      this.vv.dishes.$model.splice(index, 1);
      if (this.vv.dishes.$model.length === 0) {
        this.vv.calories.$model = calories;
        this.vv.proteinGrams.$model = protein;
        this.vv.carbohydrateGrams.$model = carbohydrates;
        this.vv.fatGrams.$model = fat;
      }
    },
    create_recipe() {
      this.recipe_draft = {name: '', servings: 1, ingredients: this.fform.dishes.filter(food => this.selected_foods.includes(food.key)).map(normalizeDish)};
    },
    recipe_saved() {
      this.recipe_draft = null;
      this.selected_foods = [];
      this.$refs.recipePicker.load();
      this.$toast.add({severity: 'success', summary: 'Dish saved', life: 3000});
    },
    reuse_dish(value) {
      this.dish_index = null;
      this.dish_draft = {...normalizeDish(value), key: crypto.randomUUID()};
    },
    copy_previous_meal() {
      if (!this.selected_meal) return;
      const source = this.selected_meal;
      this.selected_foods = [];
      this.vv.mealTime.$model = source.mealTime;
      this.vv.durationMinutes.$model = source.durationMinutes;
      this.vv.calories.$model = source.calories;
      this.vv.proteinGrams.$model = source.proteinGrams;
      this.vv.carbohydrateGrams.$model = source.carbohydrateGrams;
      this.vv.fatGrams.$model = source.fatGrams;
      this.vv.notes.$model = source.notes || '';
      this.vv.dishes.$model = (source.dishes || []).map(dish => ({...normalizeDish(dish), key: crypto.randomUUID()}));
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
      meal.durationMinutes = this.vv.durationMinutes.$model;
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
        fatGrams: dish.fatGrams,
        quantity: dish.quantity, unit: dish.unit, reference: dish.reference
      }));
      if (meal.dishes.length) {
        meal.calories = this.calculated_calories;
        meal.proteinGrams = this.vv.dishes.$model.some(dish => dish.proteinGrams === null) ? null : Math.round(this.vv.dishes.$model.reduce((total, dish) => total + dish.proteinGrams, 0) * 100) / 100;
        meal.carbohydrateGrams = this.vv.dishes.$model.some(dish => dish.carbohydrateGrams === null) ? null : Math.round(this.vv.dishes.$model.reduce((total, dish) => total + dish.carbohydrateGrams, 0) * 100) / 100;
        meal.fatGrams = this.vv.dishes.$model.some(dish => dish.fatGrams === null) ? null : Math.round(this.vv.dishes.$model.reduce((total, dish) => total + dish.fatGrams, 0) * 100) / 100;
      }
      this.saving = true;
      this.save_error = '';
      const submitted_snapshot = JSON.stringify(this.fform);
      try {
        await service.save(meal.toObject());
        this.saved_snapshot = submitted_snapshot;
        this.$emit('onSave');
        this.$toast.add({severity:'success', summary: 'Meal saved', life: 3000});
      } catch (e) {
        this.save_error = 'Unable to save the meal. Your changes are still here. '+e.message;
      } finally {
        this.saving = false;
      }
    },
    close_modal() {
      this.$emit('onClose');
    }
  }
}
</script>

<style scoped>
.meal-editor-form { max-width: 60rem; margin: 0 auto; padding: 1rem; }
h1 { font-size: 1.5rem; margin-top: 0; }
.meal-editor-form :deep(.p-inputnumber), .meal-editor-form :deep(.p-inputtext), .meal-editor-form :deep(.p-dropdown), .meal-editor-form :deep(.p-calendar) { width: 100%; min-width: 0; }
.meal-timing { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1.5rem; }
.meal-shortcut-label { margin-bottom: .5rem; }
.meal-preload-option { display: flex; flex-direction: column; gap: .35rem; white-space: normal; overflow-wrap: anywhere; min-width: 0; }
.meal-shortcut-buttons, .meal-dish-actions { display: flex; gap: .5rem; flex-wrap: wrap; }
.meal-dishes-header, .meal-dish-row, .meal-editor-footer { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.meal-dishes-header, .meal-dish-reuse { margin-bottom: 1rem; }
.meal-dish-row { padding: .75rem 0; border-bottom: 1px solid var(--surface-border); }
.meal-dish-summary { flex: 1; display: flex; flex-direction: column; gap: .35rem; min-width: 0; overflow-wrap: anywhere; }
.meal-dish-actions { flex-shrink: 0; }
.meal-editor-footer { position: sticky; bottom: 0; background: var(--surface-a, white); border-top: 1px solid var(--surface-border); padding: 1rem 0; z-index: 1; flex-wrap: wrap; }
@media (max-width: 575px) { .meal-timing { grid-template-columns: 1fr; gap: 2rem; } .meal-editor-footer > .meal-dish-actions { width: 100%; } .meal-editor-footer > .meal-dish-actions > * { flex: 1; } }
</style>
