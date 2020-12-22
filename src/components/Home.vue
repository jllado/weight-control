<template>
  <loading v-model:active="loading" :can-cancel="false" :is-full-page="true" />
  <div class="center">
    <div class="p-grid p-mt-1" v-if="loaded()" >
      <div class="p-co-12 last-weight">
        <Panel>
          <template #header>
            <div class="table-header">
              Last Weight
              <CreateWeight @onSave="load_last_weight" />
            </div>
          </template>
          <div class="p-grid">
            <div class="p-col-6">Date: </div>
            <div class="p-col-6">{{ last_weight.date }}</div>
            <div class="p-col-6">Weight: </div>
            <div class="p-col-6">{{ last_weight.weight }} kg</div>
            <div class="p-col-6">Fat: </div>
            <div class="p-col-6">{{ last_weight.fat }} kg ( {{ last_weight.fat_percentage }}%)</div>
            <div class="p-col-6">Muscle: </div>
            <div class="p-col-6">{{ last_weight.muscle }} kg ( {{ last_weight.muscle_percentage }}%)</div>
          </div>
        </Panel>
      </div>
    </div>
  </div>
</template>

<script>
import weightService from '../services/WeightService';
import CreateWeight from "@/components/CreateWeight";

export default {
  components: {CreateWeight},
  data() {
    return {
      last_weight: {},
      weights: [],
      loading: true,
    }
  },
  async created () {
    await this.load_last_weight();
    this.loading = false;
  },
  methods: {
    async load_last_weight() {
      this.last_weight = await weightService.get_last();
    },
    loaded() {
      return !this.loading;
    }
  }
}
</script>

<style>
.last-weight {
  width: 100%;
}
@media (min-width: 1024px) {
  .center {
    display: block;
    margin-left: auto;
    margin-right: auto;
    width: 50%;
  }
}
</style>