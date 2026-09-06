<template>
  <div class="recipe-form">
    <div><label for="recipe-name">Dish name</label><InputText id="recipe-name" v-model="draft.name" maxlength="255" /></div>
    <div><label for="recipe-servings">Recipe makes (servings)</label><InputNumber inputId="recipe-servings" v-model="draft.servings" :min="0.001" :max="99999999.999" :maxFractionDigits="3" :useGrouping="false" /></div>
    <p>Ingredient quantities describe the whole recipe.</p>
    <div v-for="(food, index) in draft.ingredients" :key="food.key" class="ingredient-row">
      <div class="ingredient-summary"><strong>{{ food.name }}</strong><span>{{ quantity_label(food) }} · {{ food.calories }} kcal</span><small>{{ macro_summary(food) }}</small></div>
      <div class="recipe-actions"><Button icon="pi pi-pencil" :aria-label="`Edit ingredient ${index + 1}`" class="p-button-text" @click="edit_food(index)" /><Button icon="pi pi-trash" :aria-label="`Remove ingredient ${index + 1}`" class="p-button-text p-button-danger" @click="draft.ingredients.splice(index, 1)" /></div>
    </div>
    <template v-if="editableIngredients">
      <FoodPicker :foods="foods" inputId="recipe-food" label="Add a saved food" @select="reuse_food" />
      <Button label="Add food" icon="pi pi-plus" class="p-button-outlined" @click="add_food" />
    </template>
    <p role="status">Whole recipe: {{ totals.calories }} kcal · {{ macro_summary(totals) }}</p>
    <p v-if="independent">Saving this dish does not save or change the meal.</p>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <div class="recipe-actions"><Button label="Save dish" icon="pi pi-check" :loading="saving" :disabled="!!food_draft" @click="save" /><Button label="Cancel" class="p-button-secondary" :disabled="saving" @click="$emit('close')" /></div>
    <DishForm v-if="food_draft" :dish="food_draft" @apply="apply_food" @close="food_draft = null" />
  </div>
</template>
<script>
import DishForm from './DishForm.vue';
import FoodPicker from './FoodPicker.vue';
import service from '../services/DishRecipeService';
import {normalizeDish, quantityLabel, macroSummary, foodTotals} from '../model/Dish';
export default {
  components: {DishForm, FoodPicker},
  props: {recipe: {type: Object, required: true}, foods: {type: Array, default: () => []}, independent: Boolean, editableIngredients: Boolean},
  emits: ['saved', 'close'],
  data() {
    const draft = {...this.recipe, ingredients: this.recipe.ingredients.map(food => ({...normalizeDish(food), key: crypto.randomUUID()}))};
    return {draft, snapshot: JSON.stringify(draft), food_draft: null, food_index: null, saving: false, error: ''};
  },
  computed: {dirty() { return !!this.food_draft || JSON.stringify(this.draft) !== this.snapshot; }, totals() { return foodTotals(this.draft.ingredients); }},
  methods: {
    quantity_label: quantityLabel, macro_summary: macroSummary,
    edit_food(index) { this.food_index = index; this.food_draft = normalizeDish(this.draft.ingredients[index]); },
    reuse_food(food) { this.food_index = null; this.food_draft = {...normalizeDish(food), key: crypto.randomUUID()}; },
    add_food() { this.reuse_food({name: '', calories: null, proteinGrams: null, carbohydrateGrams: null, fatGrams: null}); },
    apply_food(food) { if (this.food_index === null) this.draft.ingredients.push(food); else this.draft.ingredients.splice(this.food_index, 1, food); this.food_draft = null; },
    async save() {
      this.error = '';
      if (!this.draft.name.trim() || !(this.draft.servings > 0) || !this.draft.ingredients.length) { this.error = 'Enter a dish name, a positive serving count, and at least one ingredient.'; return; }
      this.saving = true;
      const snapshot = JSON.stringify(this.draft);
      try { const result = await service.save(this.draft); this.snapshot = snapshot; this.$emit('saved', result); }
      catch (error) { this.error = 'Unable to save the dish. Your changes are still here. ' + error.message; }
      finally { this.saving = false; }
    }
  }
};
</script>
<style scoped>
.recipe-form { display: flex; flex-direction: column; gap: 1rem; }
.recipe-form > .p-button { align-self: flex-start; }
label { display: block; margin-bottom: .5rem; }
p { margin: 0; }
.recipe-form :deep(.p-inputtext), .recipe-form :deep(.p-inputnumber) { width: 100%; min-width: 0; }
.ingredient-row { display: flex; align-items: center; justify-content: space-between; gap: .5rem; border-bottom: 1px solid var(--surface-border); padding-bottom: .75rem; }
.ingredient-summary { display: flex; flex-direction: column; gap: .3rem; min-width: 0; overflow-wrap: anywhere; }
.recipe-actions { display: flex; flex-wrap: wrap; gap: .5rem; flex-shrink: 0; }
</style>
