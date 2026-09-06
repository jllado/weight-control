<template>
  <div class="food-picker">
    <label :for="inputId">{{ label }}</label>
    <AutoComplete :inputId="inputId" v-model="selected" :suggestions="suggestions" field="label" dropdown forceSelection appendTo="body" emptySearchMessage="No matching foods" :panelStyle="{maxWidth: 'calc(100vw - 2rem)'}" @complete="search" @item-select="pick">
      <template #item="{item}"><span class="food-option">{{ item.label }}</span></template>
    </AutoComplete>
  </div>
</template>
<script>
import AutoComplete from 'primevue/autocomplete';
export default {
  components: {AutoComplete},
  props: {foods: {type: Array, required: true}, inputId: {type: String, required: true}, label: {type: String, default: 'Reuse a saved food'}},
  emits: ['select'],
  data() { return {selected: null, suggestions: []}; },
  methods: {
    search({query}) { this.suggestions = this.foods.filter(food => food.name.toLowerCase().includes(query.trim().toLowerCase())); },
    pick({value}) { this.$emit('select', value); this.selected = null; }
  }
};
</script>
<style scoped>
label { display: block; margin-bottom: .5rem; }
.food-picker :deep(.p-autocomplete) { display: flex; width: 100%; min-width: 0; }
.food-picker :deep(.p-autocomplete-input) { flex: 1; width: 0; min-width: 0; }
.food-option { white-space: normal; overflow-wrap: anywhere; }
</style>
