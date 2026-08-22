<template>
  <Dialog id="back-pain-episode-form" appendTo="body" header="Back Pain Episode" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" :style="{width: '42rem'}" :breakpoints="{'960px': '75vw', '640px': '95vw'}" data-toggle="validator" ref="form">
    <p v-if="!fixed_date" class="back-pain-date"><strong>Date:</strong> {{ date_label }}</p>
    <div class="back-pain-field back-pain-period-field">
      <label for="period">Period</label>
      <Dropdown id="period" v-model="vv.period.$model" :options="periods" optionLabel="label" optionValue="value" placeholder="Select period" :disabled="!!period" />
      <span class="error">{{ vv.period?.$errors[0]?.$message }}</span>
    </div>
    <p class="back-pain-help">Choose where you feel pain. Left and right refer to your body.</p>
    <div class="back-pain-location-grid" role="group" aria-label="Pain location">
      <div></div>
      <div v-for="side in sides" :key="side.value" class="back-pain-column-label">{{ side.label }}</div>
      <template v-for="region in regions" :key="region.value">
        <div class="back-pain-row-label">{{ region.label }}</div>
        <button v-for="side in sides" :key="side.value" type="button" class="back-pain-location" :class="{selected: is_selected(region.value, side.value)}" :aria-pressed="is_selected(region.value, side.value)" @click="select_location(region.value, side.value)">
          {{ region.label }} {{ side.label }}
        </button>
      </template>
    </div>
    <span v-if="vv.region.$error || vv.side.$error" class="error">Choose one pain location.</span>
    <Message v-if="has_unknown_location" severity="warn" :closable="false">This migrated entry has no recorded side. Choose an exact location before saving.</Message>
    <div class="back-pain-field">
      <label for="severity">Severity</label>
      <Dropdown id="severity" v-model="vv.severity.$model" :options="severities" optionLabel="label" optionValue="value" placeholder="Select severity" />
      <span class="error">{{ vv.severity?.$errors[0]?.$message }}</span>
    </div>
    <div class="back-pain-field">
      <span class="p-float-label">
        <InputText id="note" v-model="vv.note.$model" maxlength="500" />
        <label for="note">Note (optional)</label>
      </span>
      <span class="error">{{ vv.note?.$errors[0]?.$message }}</span>
    </div>
    <template #footer>
      <div class="back-pain-actions">
        <Button label="Save" icon="pi pi-check" @click="save" />
        <Button v-if="!episode" label="Save & add" icon="pi pi-plus" class="p-button-outlined" @click="save_and_continue" />
        <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
      </div>
    </template>
  </Dialog>
</template>

<script>
import {reactive} from 'vue';
import {useVuelidate} from '@vuelidate/core';
import {maxLength, required} from '@vuelidate/validators';
import dayjs from 'dayjs';
import service from '../services/BackPainEpisodeService';
import BackPainEpisode, {BACK_PAIN_SEVERITIES, BACK_REGIONS, BACK_SIDES} from '@/model/BackPainEpisode';
import {getMoodPeriodOptions} from '@/model/Mood';

export default {
  name: 'BackPainEpisodeForm',
  emits: ['onSave', 'onSaveAndContinue', 'onClose'],
  props: {
    show: Boolean,
    episode: Object,
    initial_date: Date,
    period: String,
    fixed_date: Boolean
  },
  data() {
    const fform = reactive({
      date: this.initial_date || new Date(),
      period: this.period || null,
      region: null,
      side: null,
      severity: null,
      note: ''
    });
    const rules = {
      date: {required},
      period: {required},
      region: {required},
      side: {required},
      severity: {required},
      note: {maxLength: maxLength(500)}
    };
    return {
      vv: useVuelidate(rules, fform),
      fform,
      regions: BACK_REGIONS,
      sides: BACK_SIDES,
      severities: BACK_PAIN_SEVERITIES,
      periods: getMoodPeriodOptions(),
      display_modal: this.show
    };
  },
  computed: {
    date_label() {
      return dayjs(this.fform.date).format('DD/MM/YYYY');
    },
    has_unknown_location() {
      return Boolean(this.episode && !this.episode.side && !this.fform.side);
    }
  },
  watch: {
    show(value) {
      this.display_modal = value;
      if (value) {
        this.load_form();
      }
    },
    episode() {
      if (this.display_modal) {
        this.load_form();
      }
    },
    initial_date(value) {
      if (this.display_modal && !this.episode) {
        this.vv.date.$model = value || new Date();
      }
    },
    period() {
      if (this.display_modal) {
        this.load_form();
      }
    }
  },
  methods: {
    load_form() {
      if (this.episode) {
        this.vv.date.$model = this.episode.date;
        this.vv.period.$model = this.period || this.episode.period;
        this.vv.region.$model = this.episode.region;
        this.vv.side.$model = this.episode.side;
        this.vv.severity.$model = this.episode.severity;
        this.vv.note.$model = this.episode.note || '';
        this.vv.$reset();
        return;
      }
      this.clear();
    },
    clear() {
      this.vv.date.$model = this.initial_date || new Date();
      this.vv.period.$model = this.period || null;
      this.vv.region.$model = null;
      this.vv.side.$model = null;
      this.vv.severity.$model = null;
      this.vv.note.$model = '';
      this.vv.$reset();
    },
    is_selected(region, side) {
      return this.fform.region === region && this.fform.side === side;
    },
    select_location(region, side) {
      this.vv.region.$model = region;
      this.vv.side.$model = side;
    },
    async save() {
      await this.save_episode(false);
    },
    async save_and_continue() {
      await this.save_episode(true);
    },
    async save_episode(continue_adding) {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      const episode = new BackPainEpisode();
      episode.id = this.episode ? this.episode.id : null;
      episode.date = this.vv.date.$model;
      episode.period = this.vv.period.$model;
      episode.region = this.vv.region.$model;
      episode.side = this.vv.side.$model;
      episode.severity = this.vv.severity.$model;
      episode.note = this.vv.note.$model || null;
      await service.save(episode.toObject())
          .then(() => {
            this.$toast.add({severity: 'success', summary: 'Back pain episode saved', life: 3000});
            if (continue_adding) {
              this.$emit('onSaveAndContinue');
              this.clear_episode_details();
              return;
            }
            this.$emit('onSave');
            this.close_modal();
          })
          .catch(e => this.handle_error(e));
    },
    clear_episode_details() {
      this.vv.region.$model = null;
      this.vv.side.$model = null;
      this.vv.severity.$model = null;
      this.vv.note.$model = '';
      this.vv.$reset();
    },
    close_modal() {
      this.clear();
      this.$emit('onClose');
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
};
</script>

<style scoped>
.back-pain-date {
  margin: 0 0 0.5rem;
}
.back-pain-help {
  margin: 0 0 1rem;
  color: #666;
}
.back-pain-period-field {
  margin: 1rem 0;
}
.back-pain-location-grid {
  display: grid;
  grid-template-columns: 5rem repeat(3, minmax(0, 1fr));
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}
.back-pain-column-label,
.back-pain-row-label {
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}
.back-pain-location {
  min-height: 4rem;
  padding: 0.5rem;
  border: 1px solid #b8c2cc;
  border-radius: 0.4rem;
  color: #333;
  background: #fff;
  cursor: pointer;
}
.back-pain-location:hover {
  border-color: #2196f3;
  background: #f3f9fe;
}
.back-pain-location.selected {
  border: 2px solid #1976d2;
  color: #0d47a1;
  background: #e3f2fd;
  font-weight: 700;
}
.back-pain-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-top: 1.5rem;
}
.back-pain-field .p-inputtext {
  width: 100%;
}
.back-pain-field .p-dropdown {
  width: 100%;
}
.back-pain-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
}
.back-pain-actions .p-button {
  margin: 0;
}
@media (max-width: 640px) {
  .back-pain-location-grid {
    grid-template-columns: 4rem repeat(3, minmax(0, 1fr));
    gap: 0.3rem;
  }
  .back-pain-location {
    min-height: 4.5rem;
    padding: 0.25rem;
    font-size: 0.75rem;
  }
}
</style>
