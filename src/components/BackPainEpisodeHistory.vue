<template>
  <div>
    <DataTable :value="episodes" :paginator="true" :rows="10" :loading="state.loading" responsiveLayout="scroll"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}">
      <template #header>
        <div class="table-header">
          Back Pain Episodes
          <CreateBackPainEpisode @onSave="load_episodes" />
        </div>
      </template>
      <Column header="Date" headerStyle="width: 110px">
        <template #body="episode">{{ episode.data.dateFormat }}</template>
      </Column>
      <Column header="Time" headerStyle="width: 140px">
        <template #body="episode">{{ format_time(episode.data) }}</template>
      </Column>
      <Column header="Location" headerStyle="min-width: 180px">
        <template #body="episode">{{ format_location(episode.data) }}</template>
      </Column>
      <Column header="Severity" headerStyle="min-width: 140px">
        <template #body="episode">
          <span :class="severity_option(episode.data.severity).className">{{ format_severity(episode.data.severity) }}</span>
        </template>
      </Column>
      <Column header="Note" headerStyle="min-width: 180px">
        <template #body="episode">{{ episode.data.note || 'No note' }}</template>
      </Column>
      <Column headerStyle="width: 100px">
        <template #body="episode">
          <div style="width: 100px; text-align: center">
            <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(episode.data)" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(episode.data)" />
          </div>
        </template>
      </Column>
    </DataTable>
    <BackPainEpisodeForm @onSave="load_episodes" @onClose="close_edit" v-model:show="display_edit_modal" :episode="episode" />
  </div>
</template>

<script>
import service from '../services/BackPainEpisodeService';
import CreateBackPainEpisode from '@/components/CreateBackPainEpisode';
import BackPainEpisodeForm from '@/components/BackPainEpisodeForm';
import {formatBackPainLocation, formatBackPainSeverity, formatBackPainTime, getBackPainSeverityOption} from '@/model/BackPainEpisode';
import {userState} from '../state';

export default {
  name: 'BackPainEpisodeHistory',
  components: {CreateBackPainEpisode, BackPainEpisodeForm},
  data() {
    return {
      episode: null,
      episodes: [],
      display_edit_modal: false,
      state: userState()
    };
  },
  async created() {
    await this.load_episodes();
  },
  methods: {
    async load_episodes() {
      this.state.loading = true;
      this.episodes = await service.get_all();
      this.state.loading = false;
    },
    async remove(episode) {
      if (!confirm('Are you sure you want to delete this episode?')) {
        return;
      }
      service.delete(episode)
          .then(() => this.load_episodes())
          .catch(e => this.handle_error(e));
    },
    edit(episode) {
      this.episode = Object.assign({}, episode);
      this.display_edit_modal = true;
    },
    close_edit() {
      this.display_edit_modal = false;
      this.episode = null;
    },
    severity_option(value) {
      return getBackPainSeverityOption(value);
    },
    format_severity(value) {
      return formatBackPainSeverity(value);
    },
    format_location(episode) {
      return formatBackPainLocation(episode);
    },
    format_time(episode) {
      return formatBackPainTime(episode);
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
};
</script>
