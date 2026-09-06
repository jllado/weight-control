<template>
  <section aria-label="Dishes">
    <p>Save selected foods from a meal as a dish, then reuse its ingredients in other meals.</p>
    <label for="dish-search">Search dishes</label><InputText id="dish-search" v-model="search" class="dish-search" />
    <p v-if="error" role="alert">{{ error }} <Button label="Retry" class="p-button-text" @click="load" /></p>
    <DataTable :value="filtered" :loading="loading" :paginator="true" :rows="10" responsiveLayout="scroll">
      <template #empty>No saved dishes.</template>
      <Column field="name" header="Dish"><template #body="{data}"><span class="dish-name">{{ data.name }}</span><small class="mobile-dish-details">{{ data.servings }} servings · {{ calories(data) }} / serving</small></template></Column>
      <Column field="servings" header="Recipe servings" headerClass="recipe-detail-column" bodyClass="recipe-detail-column" />
      <Column header="Calories per serving" headerClass="recipe-detail-column" bodyClass="recipe-detail-column"><template #body="{data}">{{ calories(data) }}</template></Column>
      <Column header="Actions" headerStyle="width: 6rem"><template #body="{data}"><div class="dish-actions"><Button icon="pi pi-pencil" aria-label="Edit dish" class="p-button-text" @click="$router.push(`/dishes/${data.id}/edit`)" /><Button icon="pi pi-trash" aria-label="Delete dish" class="p-button-text p-button-danger" @click="remove(data)" /></div></template></Column>
    </DataTable>
  </section>
</template>
<script>
import service from '../services/DishRecipeService';
import {scaleRecipe, foodTotals} from '../model/Dish';
export default {
  data() { return {recipes: [], search: '', loading: false, error: ''}; },
  mounted() { this.load(); },
  computed: {filtered() { return this.recipes.filter(recipe => recipe.name.toLowerCase().includes(this.search.trim().toLowerCase())); }},
  methods: {
    async load() { this.loading = true; this.error = ''; try { this.recipes = await service.get_all(); } catch { this.error = 'Unable to load dishes.'; } finally { this.loading = false; } },
    calories(recipe) { try { return `${foodTotals(scaleRecipe(recipe, 1)).calories} kcal`; } catch { return 'Outside supported portion range'; } },
    async remove(recipe) { if (!confirm(`Delete “${recipe.name}”? Existing meals will stay unchanged.`)) return; try { await service.delete(recipe.id); this.recipes = this.recipes.filter(item => item.id !== recipe.id); } catch (error) { this.error = 'Unable to delete the dish. ' + error.message; } }
  }
};
</script>
<style scoped>
label { display: block; margin-bottom: .5rem; }
.dish-search { width: 100%; margin-bottom: 1rem; }
.dish-name { overflow-wrap: anywhere; }
.mobile-dish-details { display: none; }
@media (max-width: 575px) {
  :deep(.recipe-detail-column) { display: none; }
  .mobile-dish-details { display: block; margin-top: .5rem; }
}
.dish-actions { display: flex; flex-wrap: wrap; }
</style>
