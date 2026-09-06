<template>
  <div class="recipe-picker">
    <label for="add-recipe">Add dish</label>
    <AutoComplete inputId="add-recipe" v-model="selected" :suggestions="suggestions" field="name" dropdown forceSelection :disabled="loading" appendTo="body" emptySearchMessage="No saved dishes" :panelStyle="{maxWidth: 'calc(100vw - 2rem)'}" @complete="search" @item-select="pick">
      <template #item="{item}"><span class="recipe-option">{{ item.name }}</span></template>
    </AutoComplete>
    <p v-if="error" role="alert">{{ error }} <Button label="Retry" class="p-button-text" @click="load" /></p>
    <Dialog v-if="recipe" header="Add dish" :visible="true" modal appendTo="body" :closable="false" :style="{width: 'min(36rem, 95vw)'}">
      <div class="recipe-preview">
        <strong class="recipe-option">{{ recipe.name }}</strong>
        <label for="use-servings">Servings to add</label><InputNumber inputId="use-servings" v-model="servings" :min="0.001" :max="99999999.999" :maxFractionDigits="3" :useGrouping="false" />
        <p>Each ingredient will be added as a separate food.</p>
        <p v-if="preview.error" role="alert" class="error">{{ preview.error }}</p>
        <template v-else>
          <div v-for="(food, index) in preview.foods" :key="index" class="recipe-option">{{ food.name }} · {{ quantity_label(food) }} · {{ food.calories }} kcal</div>
          <p role="status">{{ preview.totals.calories }} kcal · {{ macro_summary(preview.totals) }}</p>
        </template>
      </div>
      <template #footer><Button label="Add foods" :disabled="!!preview.error" @click="apply" /><Button label="Cancel" class="p-button-secondary" @click="recipe = null" /></template>
    </Dialog>
  </div>
</template>
<script>
import AutoComplete from 'primevue/autocomplete';
import service from '../services/DishRecipeService';
import {scaleRecipe, foodTotals, quantityLabel, macroSummary} from '../model/Dish';
export default {
  components: {AutoComplete}, emits: ['apply'],
  data() { return {recipes: [], suggestions: [], selected: null, recipe: null, servings: 1, loading: false, error: ''}; },
  mounted() { this.load(); },
  computed: {
    preview() {
      try { const foods = scaleRecipe(this.recipe, this.servings); return {foods, totals: foodTotals(foods)}; }
      catch (error) { return {error: error.message}; }
    }
  },
  methods: {
    quantity_label: quantityLabel, macro_summary: macroSummary,
    async load() { this.loading = true; this.error = ''; try { this.recipes = await service.get_all(); } catch { this.error = 'Unable to load dishes.'; } finally { this.loading = false; } },
    search({query}) { this.suggestions = this.recipes.filter(recipe => recipe.name.toLowerCase().includes(query.trim().toLowerCase())); },
    pick({value}) { this.recipe = value; this.servings = 1; this.selected = null; },
    apply() { this.$emit('apply', this.preview.foods); this.recipe = null; }
  }
};
</script>
<style scoped>
label { display: block; margin-bottom: .5rem; }
.recipe-picker :deep(.p-autocomplete) { display: flex; width: 100%; min-width: 0; }
.recipe-picker :deep(.p-autocomplete-input) { flex: 1; width: 0; min-width: 0; }
.recipe-option { white-space: normal; overflow-wrap: anywhere; }
.recipe-preview { display: flex; flex-direction: column; gap: .5rem; }
.recipe-preview :deep(.p-inputnumber) { width: 100%; }
</style>
