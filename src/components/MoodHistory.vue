<template>
  <div>
    <p v-if="refresh_error" role="alert">{{ refresh_error }} <Button label="Retry" class="p-button-text" @click="load_moods" /></p>
    <DataTable :value="this.moods" :paginator="true" :rows="10" :loading="this.state.loading" responsiveLayout="scroll"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}" >
      <template #header>
        <div class="table-header">
          Moods
          <CreateMood @onSave="load_moods" />
        </div>
      </template>
      <Column header="Date" headerStyle="width: 111px">
        <template #body="mood">
          {{ mood.data.dateFormat }}
        </template>
      </Column>
      <Column header="Period" headerStyle="width: 100px">
        <template #body="mood">
          {{ mood.data.periodLabel() }}
        </template>
      </Column>
      <Column header="Mood" headerStyle="width: 140px">
        <template #body="mood">
          {{ mood.data.emoji() }} {{ mood.data.label() }}
        </template>
      </Column>
      <Column header="Note">
        <template #body="mood">
          {{ mood.data.note }}
        </template>
      </Column>
      <Column headerStyle="width: 100px">
        <template #body="mood">
          <div class="action-group action-group--compact">
            <CompactAction icon="pi pi-pencil" @click="edit(mood.data)" aria-label="Edit" />
            <CompactAction icon="pi pi-trash" :action="() => remove(mood.data)" busyLabel="Deleting…" aria-label="Delete" destructive />
          </div>
        </template>
      </Column>
    </DataTable>
    <MoodForm @onSave="load_moods" @onClose="close_edit" v-model:show="display_edit_modal" v-model:mood="mood" />
  </div>
</template>

<script>
import service from '../services/MoodService';
import CreateMood from "@/components/CreateMood.vue";
import MoodForm from "@/components/MoodForm.vue";
import { userState } from '../state';

export default {
  components: {CreateMood, MoodForm},
  data() {
    return {
      mood: null,
      moods: [],
      display_edit_modal: false,
      refresh_error: '',
      state: userState()
    }
  },
  async created () {
    await this.load_moods();
  },
  methods: {
    async load_moods() {
      this.state.loading = true;
      this.refresh_error = '';
      try {
        this.moods = await service.get_all_by(this.state.user.mail);
      } catch (error) {
        this.refresh_error = 'Unable to refresh entries. ' + error.message;
      } finally {
        this.state.loading = false;
      }
    },
    async remove(mood) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      await service.delete(mood)
          .then(async () => {
            await this.load_moods();
          })
          .catch(e => {
            this.handle_error(e)
          });
    },
    async edit(mood) {
      this.mood = Object.assign({}, mood);
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
