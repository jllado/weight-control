<template>
  <Panel header="Active coaching plan" class="p-mt-3">
    <p>Keep your current goal and agreed coaching actions available across conversations and reflections.</p>
    <div class="coaching-plan-form">
      <div class="p-field">
        <label for="coaching-plan-goal">Primary goal</label>
        <InputText id="coaching-plan-goal" v-model="plan.goal" maxlength="255" />
        <span class="error">{{ errors.goal }}</span>
      </div>
      <div class="p-field">
        <label for="coaching-plan-principles">Principles</label>
        <textarea id="coaching-plan-principles" v-model="principlesText" class="p-inputtext p-component plan-list" rows="4" placeholder="One principle per line" />
      </div>
      <div class="p-field">
        <label for="coaching-plan-priorities">Ordered priorities</label>
        <textarea id="coaching-plan-priorities" v-model="prioritiesText" class="p-inputtext p-component plan-list" rows="4" placeholder="One priority per line" />
      </div>
      <div class="p-field">
        <label for="coaching-plan-actions">Agreed actions</label>
        <textarea id="coaching-plan-actions" v-model="actionsText" class="p-inputtext p-component plan-list" rows="4" placeholder="One action per line" />
      </div>
      <div class="plan-dates">
        <div class="p-field">
          <label for="coaching-plan-start-date">Start date</label>
          <Calendar inputId="coaching-plan-start-date" v-model="plan.startDate" dateFormat="dd/mm/yy" appendTo="body" />
          <span class="error">{{ errors.startDate }}</span>
        </div>
        <div class="p-field">
          <label for="coaching-plan-review-date">Review date (optional)</label>
          <Calendar inputId="coaching-plan-review-date" v-model="plan.reviewDate" dateFormat="dd/mm/yy" appendTo="body" showButtonBar />
          <span class="error">{{ errors.reviewDate }}</span>
        </div>
      </div>
      <div class="p-field">
        <label for="coaching-plan-notes">Notes (optional)</label>
        <textarea id="coaching-plan-notes" v-model="plan.notes" class="p-inputtext p-component plan-notes" rows="4" />
      </div>
      <Button label="Save coaching plan" icon="pi pi-check" @click="save" :loading="saving || loading" />
    </div>
  </Panel>
</template>

<script>
import dayjs from 'dayjs';
import CoachingPlan from '../model/CoachingPlan';
import coachingPlanService from '../services/CoachingPlanService';

export default {
  name: 'CoachingPlanSettings',
  data() {
    return {
      plan: new CoachingPlan(),
      principlesText: '',
      prioritiesText: '',
      actionsText: '',
      errors: {},
      loading: false,
      saving: false
    };
  },
  async created() {
    await this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const plan = await coachingPlanService.get();
        if (plan) {
          this.setPlan(plan);
        }
      } catch (e) {
        this.handleError(e);
      } finally {
        this.loading = false;
      }
    },
    setPlan(plan) {
      this.plan = plan;
      this.principlesText = plan.principles.join('\n');
      this.prioritiesText = plan.priorities.join('\n');
      this.actionsText = plan.actions.join('\n');
    },
    async save() {
      if (!this.validate()) {
        return;
      }
      const plan = new CoachingPlan({
        ...this.plan,
        principles: this.lines(this.principlesText),
        priorities: this.lines(this.prioritiesText),
        actions: this.lines(this.actionsText)
      });
      this.saving = true;
      try {
        this.setPlan(await coachingPlanService.save(plan));
        this.$toast.add({severity: 'success', summary: 'Coaching plan saved', life: 3000});
      } catch (e) {
        this.handleError(e);
      } finally {
        this.saving = false;
      }
    },
    validate() {
      const errors = {};
      if (!this.plan.goal.trim()) {
        errors.goal = 'Primary goal is required';
      }
      if (!this.plan.startDate) {
        errors.startDate = 'Start date is required';
      }
      if (this.plan.reviewDate && dayjs(this.plan.startDate).isAfter(this.plan.reviewDate, 'day')) {
        errors.reviewDate = 'Review date must not be before the start date';
      }
      this.errors = errors;
      return Object.keys(errors).length === 0;
    },
    lines(value) {
      return value.split('\n').map(line => line.trim()).filter(Boolean);
    },
    handleError(e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Coaching plan failed', detail: e, life: 3000});
    }
  }
};
</script>

<style scoped>
.coaching-plan-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.coaching-plan-form .p-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 0;
}
.plan-list,
.plan-notes {
  resize: vertical;
  width: 100%;
}
.plan-dates {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}
.coaching-plan-form .p-button {
  align-self: flex-start;
}
@media (max-width: 640px) {
  .plan-dates {
    grid-template-columns: 1fr;
  }
}
</style>
