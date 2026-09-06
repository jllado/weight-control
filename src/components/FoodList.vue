<template>
  <section aria-label="Foods">
    <p>Changes apply to future uses. Existing meals and saved dishes stay unchanged.</p>
    <div class="food-toolbar"><div class="food-search"><label for="food-search">Search foods</label><InputText id="food-search" v-model="search" /></div><Button label="Add food" icon="pi pi-plus" @click="add" /></div>
    <p v-if="error" role="alert">{{ error }} <Button label="Retry" class="p-button-text" @click="load" /></p>
    <DataTable :value="filtered" :loading="loading" :paginator="true" :rows="10" v-model:first="first" responsiveLayout="scroll">
      <template #empty>{{ search.trim() ? 'No matching foods.' : 'No saved foods.' }}</template>
      <Column field="name" header="Food"><template #body="{data}"><span class="food-name">{{ data.name }}</span><small class="mobile-food-details">{{ quantity_label(data) }} · {{ data.calories }} kcal<br>{{ macro_summary(data) }}</small></template></Column>
      <Column header="Quantity" headerClass="food-detail-column" bodyClass="food-detail-column"><template #body="{data}">{{ quantity_label(data) }}</template></Column>
      <Column header="Calories" headerClass="food-detail-column" bodyClass="food-detail-column"><template #body="{data}">{{ data.calories }} kcal</template></Column>
      <Column v-for="field in macros" :key="field.key" :header="field.label" headerClass="food-detail-column" bodyClass="food-detail-column"><template #body="{data}">{{ data[field.key] === null ? '—' : `${data[field.key]} g` }}</template></Column>
      <Column header="Actions" headerStyle="width: 6rem"><template #body="{data}"><div class="food-actions"><Button icon="pi pi-pencil" :aria-label="`Edit ${data.name}`" class="p-button-text" @click="edit(data)" /><Button icon="pi pi-trash" :aria-label="`Delete ${data.name}`" class="p-button-text p-button-danger" @click="remove(data)" /></div></template></Column>
    </DataTable>
    <DishForm v-if="draft" :dish="draft" saveLabel="Save" :saving="saving" :error="save_error" @apply="save" @close="draft = null" />
  </section>
</template>
<script>
import DishForm from './DishForm.vue';
import service from '../services/FoodService';
import {normalizeDish, quantityLabel, macroSummary} from '../model/Dish';
export default {
  components: {DishForm},
  data() { return {foods: [], search: '', first: 0, loading: false, error: '', draft: null, saving: false, save_error: '', macros: [{key: 'proteinGrams', label: 'Protein'}, {key: 'carbohydrateGrams', label: 'Carbohydrates'}, {key: 'fatGrams', label: 'Fat'}]}; },
  computed: {filtered() { return this.foods.filter(food => food.name.toLowerCase().includes(this.search.trim().toLowerCase())).sort((a, b) => a.name.localeCompare(b.name)); }},
  watch: {search() { this.first = 0; }},
  mounted() { this.load(); },
  methods: {
    quantity_label: quantityLabel,
    macro_summary: macroSummary,
    async load() { this.loading = true; this.error = ''; try { this.foods = await service.get_all(); } catch { this.error = 'Unable to load foods.'; } finally { this.loading = false; } },
    add() { this.edit(normalizeDish({name: '', calories: null, proteinGrams: null, carbohydrateGrams: null, fatGrams: null})); },
    edit(food) { this.save_error = ''; this.draft = food; },
    async save(food) {
      this.saving = true; this.save_error = '';
      try {
        const saved = await service.save(food);
        this.foods = [...this.foods.filter(item => item.id !== saved.id), saved];
        this.first = 0; this.draft = null;
        this.$toast.add({severity: 'success', summary: 'Food saved', life: 3000});
      } catch (error) { this.save_error = 'Unable to save the food. ' + error.message; } finally { this.saving = false; }
    },
    async remove(food) {
      if (!confirm(`Delete “${food.name}”? Existing meals and saved dishes will stay unchanged.`)) return;
      try { await service.delete(food.id); this.foods = this.foods.filter(item => item.id !== food.id); this.first = 0; this.error = ''; } catch (error) { this.error = 'Unable to delete the food. ' + error.message; }
    }
  }
};
</script>
<style scoped>
.food-toolbar { display: flex; align-items: flex-end; gap: 1rem; margin-bottom: 1rem; }
.food-search { flex: 1; min-width: 0; }
label { display: block; margin-bottom: .5rem; }
.food-search :deep(.p-inputtext) { width: 100%; }
.food-name { overflow-wrap: anywhere; }
.food-actions { display: flex; flex-wrap: wrap; }
.mobile-food-details { display: none; }
@media (max-width: 960px) {
  :deep(.food-detail-column) { display: none; }
  .mobile-food-details { display: block; margin-top: .5rem; }
}
@media (max-width: 575px) {
  .food-toolbar { align-items: stretch; flex-direction: column; }
}
</style>
