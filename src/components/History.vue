<template>
  <div>
    <DataTable :value="this.weights" :paginator="true" :rows="10" :loading="loading" >
      <template #header>
        <div class="table-header">
          Weights
          <CreateWeight @onSave="load_weights" />
        </div>
      </template>
      <Column field="date" header="Date" headerStyle="width: 111px"/>
      <Column field="weight" header="Weight" headerStyle="width: 100%" />
      <Column header="Fat" headerStyle="width: 100%" >
        <template #body="weight" >
          {{ weight.data.fat }} <span class="percentage">{{ weight.data.fat_percentage }}%</span>
        </template>
      </Column>
      <Column header="Muscle" headerStyle="width: 100%" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="weight">
          {{ weight.data.muscle }} <span class="percentage">{{ weight.data.muscle_percentage }}%</span>
        </template>
      </Column>
      <Column headerStyle="width: 112px" bodyStyle="text-align: center" >
        <template #body="weight">
          <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(weight.data)" />
          <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(weight.data)" />
        </template>
      </Column>
    </DataTable>
    <WeightForm @onSave="load_weights" @onClose="close_edit" v-model:show="display_edit_modal" v-model:weight="weight" />

  </div>
</template>

<script>
import service from '../services/WeightService';
import CreateWeight from "@/components/CreateWeight";
import WeightForm from "@/components/WeightForm";

export default {
  components: {CreateWeight, WeightForm},
  data() {
    return {
      weight: null,
      weights: [],
      display_edit_modal: false,
      loading: true
    }
  },
  async created () {
    await this.load_weights();
  },
  methods: {
    async load_weights() {
      this.loading = true;
      this.weights = await service.get_all();
      this.loading = false;
    },
    async remove(weight) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      service.delete(weight)
          .then(() => {
            this.load_weights();
          })
          .catch(e => {
            this.handle_error(e)
          });
    },
    async edit(weight) {
      this.weight = Object.assign({}, weight);
      this.display_edit_modal = true;
    },
    close_edit() {
      this.display_edit_modal = false;
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
}
</script>
