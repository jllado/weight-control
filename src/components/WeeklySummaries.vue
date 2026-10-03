<template>
  <loading v-model:active="loading" :can-cancel="false" :is-full-page="true" />
  <main class="weekly-page">
    <header class="weekly-header">
      <div>
        <div class="weekly-kicker">Personal review</div>
        <h1>Weekly summaries</h1>
        <p>Saved Saturday–Friday records with weekend outcome measurements.</p>
      </div>
      <ActionButton v-if="preview"
              :label="preview.alreadySaved ? 'Open latest summary' : 'Create weekly summary'"
              :icon="preview.alreadySaved ? 'pi pi-folder-open' : 'pi pi-plus'"
              :disabled="!preview.alreadySaved && !preview.canCreate || creating"
              :loading="creating"
              busyLabel="Saving…"
              :action="create_or_open_latest" />
    </header>

    <Dialog v-model:visible="showMissingOutcomeConfirm" appendTo="body" modal header="Save with missing outcome measurements?" :closable="!creating" :closeOnEscape="!creating" :style="{width: 'min(28rem, calc(100vw - 2rem))'}">
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      <p>The selected week is missing one or more Friday–Sunday outcome readings. Save the available evidence with the missing-data warnings?</p>
      <template #footer>
        <div class="action-group">
          <Button label="Cancel" class="p-button-text" :disabled="creating" @click="showMissingOutcomeConfirm = false" />
          <ActionButton label="Save anyway" icon="pi pi-save" :loading="creating" :action="save_latest" />
        </div>
      </template>
    </Dialog>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <section v-if="preview && preview.snapshot && !is_selected(preview.fridayDate)" class="preview-card">
      <div class="preview-heading">
        <div>
          <div class="weekly-kicker">Latest eligible period</div>
          <h2>{{ format_period(preview.periodStart, preview.fridayDate) }}</h2>
        </div>
        <span class="period-state" :class="{'period-state--saved': preview.alreadySaved}">
          {{ preview.alreadySaved ? 'Saved' : preview.canCreate ? 'Ready to save' : 'Sunday measurement window still open' }}
        </span>
      </div>
      <div class="outcome-grid">
        <div class="outcome-item">
          <span>Weight</span>
          <strong>{{ outcome_weight(preview.snapshot.outcomes.weight) }}</strong>
        </div>
        <div class="outcome-item">
          <span>Blood pressure</span>
          <strong>{{ outcome_blood_pressure(preview.snapshot.outcomes.bloodPressure) }}</strong>
        </div>
      </div>
      <ul v-if="preview.snapshot.warnings.length" class="warning-list">
        <li v-for="warning in preview.snapshot.warnings" :key="warning">{{ warning }}</li>
      </ul>
      <p v-if="!preview.canCreate && !preview.alreadySaved" class="preview-note">
        You can save this period from Monday, after Friday through Sunday outcome measurements are complete.
      </p>
    </section>

    <section class="weekly-layout">
      <aside class="archive-card" aria-label="Saved weekly summaries">
        <div class="section-heading">
          <div>
            <div class="weekly-kicker">Archive</div>
            <h2>Saved weeks</h2>
          </div>
          <span>{{ archive?.summaries.length || 0 }}</span>
        </div>
        <div v-if="archive?.summaries.length" class="archive-list">
          <button v-for="item in archive.summaries"
                  :key="item.fridayDate"
                  type="button"
                  class="archive-item"
                  :class="{selected: is_selected(item.fridayDate)}"
                  :aria-current="is_selected(item.fridayDate) ? 'page' : null"
                  @click="select_summary(item.fridayDate)">
            <span class="archive-period">{{ format_period(item.periodStart, item.fridayDate) }}</span>
            <span class="archive-status">{{ item.reflectionSaved ? 'Weekly reflection saved' : 'No reflection yet' }}</span>
            <i class="pi pi-arrow-right" aria-hidden="true"></i>
          </button>
        </div>
        <div v-else class="archive-empty">Saved summaries will appear here.</div>
      </aside>

      <section v-if="detail" class="detail-card" aria-label="Weekly summary details">
        <header class="detail-heading">
          <div>
            <div class="weekly-kicker">Saturday–Friday summary</div>
            <h2>{{ format_period(detail.periodStart, detail.fridayDate) }}</h2>
            <p>Saved {{ format_timestamp(detail.createdAt) }}. Activity totals stop on Friday; weight and blood pressure outcomes use Friday, then Saturday, then Sunday.</p>
          </div>
          <span class="informational-badge">Recorded evidence only</span>
        </header>

        <div v-if="detail.snapshot.warnings.length" class="missing-data" role="status">
          <strong>Missing measurements</strong>
          <ul><li v-for="warning in detail.snapshot.warnings" :key="warning">{{ warning }}</li></ul>
        </div>

        <section class="detail-section">
          <div class="section-heading">
            <div>
              <div class="weekly-kicker">Weekend outcomes</div>
              <h3>Selected measurements</h3>
            </div>
          </div>
          <div class="outcome-grid">
            <article class="outcome-item">
              <span>Weight</span>
              <strong>{{ outcome_weight(detail.snapshot.outcomes.weight) }}</strong>
              <small v-if="detail.snapshot.outcomes.weight">
                {{ body_composition(detail.snapshot.outcomes.weight) }}
              </small>
            </article>
            <article class="outcome-item">
              <span>Blood pressure</span>
              <strong>{{ outcome_blood_pressure(detail.snapshot.outcomes.bloodPressure) }}</strong>
            </article>
          </div>
        </section>

        <section class="detail-section">
          <div class="weekly-kicker">Compared periods</div>
          <h3>Recorded metrics</h3>
          <p class="comparison-caption">Current week compared with the preceding Saturday–Friday week and the matching week 52 weeks earlier. Missing records remain unknown.</p>
          <div class="metrics-table" role="table" aria-label="Weekly recorded metrics">
            <div class="metrics-row metrics-row--heading" role="row">
              <span role="columnheader">Metric</span><span role="columnheader">This week</span><span role="columnheader">Last week</span><span role="columnheader">52 weeks ago</span>
            </div>
            <div v-for="row in metric_rows" :key="row.label" class="metrics-row" role="row">
              <strong role="rowheader">{{ row.label }}</strong><span role="cell" data-period="This week" :aria-label="`This week: ${row.current}`">{{ row.current }}</span><span role="cell" data-period="Last week" :aria-label="`Last week: ${row.previous}`">{{ row.previous }}</span><span role="cell" data-period="52 weeks ago" :aria-label="`52 weeks ago: ${row.yearAgo}`">{{ row.yearAgo }}</span>
            </div>
          </div>
        </section>

        <section class="detail-section">
          <div class="weekly-kicker">Routine watch-outs</div>
          <h3>Eligible check-in days</h3>
          <p class="comparison-caption">Each local calendar day counts once. Days before a routine started do not count.</p>
          <div v-if="detail.snapshot.routines.length" class="routine-list">
            <div v-for="routine in detail.snapshot.routines" :key="routine.name" class="routine-item" :class="{'routine-item--watch': Number(routine.percentage) < 60}">
              <strong>{{ routine.name }}</strong>
              <span>{{ routine.completedDays }}/{{ routine.eligibleDays }} days · {{ Number(routine.percentage).toFixed(0) }}%</span>
              <span v-if="Number(routine.percentage) < 60" class="watch-label">Below 60%</span>
            </div>
          </div>
          <p v-else class="empty-note">No active routine opportunities this week.</p>
        </section>

        <section class="detail-section">
          <div class="weekly-kicker">Goal context</div>
          <h3>Plan evidence</h3>
          <p v-if="detail.snapshot.goalEvidence.available">{{ detail.snapshot.goalEvidence.goal }}<span v-if="detail.snapshot.goalEvidence.startDate"> · Started {{ format_date(detail.snapshot.goalEvidence.startDate) }}</span><span v-if="detail.snapshot.goalEvidence.reviewDate"> · Review {{ format_date(detail.snapshot.goalEvidence.reviewDate) }}</span></p>
          <p v-else class="empty-note">{{ detail.snapshot.goalEvidence.unavailableReason }}</p>
        </section>

        <section class="detail-section">
          <div class="weekly-kicker">Personal records</div>
          <h3>New records</h3>
          <ul v-if="detail.snapshot.personalRecords.length" class="record-list">
            <li v-for="record in detail.snapshot.personalRecords" :key="`${record.label}-${record.date}`">
              <strong>{{ record.label }}</strong><span>{{ format_record_value(record.value, record.unit) }} · {{ format_date(record.date) }}</span>
            </li>
          </ul>
          <p v-else class="empty-note">No new personal records this week.</p>
        </section>

        <section class="weekly-reflection">
          <header class="section-heading">
            <div>
              <div class="weekly-kicker">Weekly reflection</div>
              <h3>{{ detail.reflection ? detail.reflection.title : 'No reflection saved yet' }}</h3>
            </div>
            <span v-if="detail.reflection" class="saved-label">Saved {{ format_timestamp(detail.reflection.generatedAt) }}</span>
          </header>
          <p v-if="detail.reflection" class="reflection-summary">{{ detail.reflection.summary }}</p>
          <p v-else-if="archive.actionConfigured" class="empty-note">Ask the Coach to review this saved snapshot. The reflection is stored separately from daily reflections.</p>
          <p v-else class="empty-note">Weekly reflection Actions are not configured yet. This saved snapshot remains available for review.</p>
          <div v-if="detail.reflection" class="reflection-sections">
            <article v-for="section in reflection_sections" :key="section.title" class="reflection-section">
              <h4>{{ section.title }}</h4>
              <p>{{ section.content.summary }}</p>
              <p><strong>Next action:</strong> {{ section.content.nextAction }}</p>
            </article>
          </div>
          <div v-if="detail.reflection" class="next-week-actions">
            <h4>Next week</h4>
            <ul><li v-for="action in detail.reflection.nextWeekActions" :key="action">{{ action }}</li></ul>
          </div>
          <div class="reflection-actions action-group">
            <Button :label="detail.reflection ? 'Update reflection' : 'Add reflection'"
                    icon="chatgpt-icon"
                    :pt="{icon: {'aria-hidden': true}}"
                    :disabled="!archive.actionConfigured"
                    @click="open_coach" />
            <ActionButton label="Refresh reflection" busyLabel="Refreshing…" icon="pi pi-refresh" class="p-button-outlined" :action="refresh_summary" />
          </div>
        </section>
      </section>

      <section v-else-if="!preview?.snapshot" class="empty-state">
        <i class="pi pi-calendar-times" aria-hidden="true"></i>
        <h2>No weekly summary data yet</h2>
        <p>Recorded health data will appear here after a Saturday–Friday period closes.</p>
      </section>
    </section>
  </main>
</template>

<script>
import dayjs from 'dayjs';
import weeklySummaryService from '@/services/WeeklySummaryService';
import {buildWeeklyReflectionPrompt} from '@/model/Reflection';
import {openCoach} from '@/services/CoachService';

export default {
  name: 'WeeklySummaries',
  data() {
    return {
      archive: null,
      preview: null,
      detail: null,
      loading: true,
      creating: false,
      showMissingOutcomeConfirm: false,
      error: null
    };
  },
  computed: {
    metric_rows() {
      if (!this.detail) return [];
      const {currentPeriod, previousComparablePeriod, yearAgoComparablePeriod} = this.detail.snapshot.progress;
      const row = (label, current, previous, yearAgo) => ({label, current, previous, yearAgo});
      const averageWeight = value => {
        if (value?.weightKg == null) return 'Not recorded';
        const parts = [`${Number(value.weightKg).toFixed(1)} kg`];
        if (value.fatKg != null) parts.push(`fat ${Number(value.fatKg).toFixed(1)} kg`);
        if (value.fatPercentage != null) parts.push(`${Number(value.fatPercentage).toFixed(1)}% fat`);
        if (value.muscleKg != null) parts.push(`muscle ${Number(value.muscleKg).toFixed(1)} kg`);
        if (value.musclePercentage != null) parts.push(`${Number(value.musclePercentage).toFixed(1)}% muscle`);
        return parts.join(' · ');
      };
      const bloodPressure = value => value == null ? 'Not recorded' : `${Math.round(value.systolic)} / ${Math.round(value.diastolic)} mmHg · ${value.measurementCount} readings`;
      const mood = period => period.moodAverage == null ? 'Not recorded' : `${Number(period.moodAverage).toFixed(1)} / 5 · ${period.moodDayCount} days`;
      const sleep = value => {
        if (value == null) return 'Not recorded';
        const parts = [`${(Number(value.totalSleepSeconds) / 3600).toFixed(1)} h · ${value.nightCount} nights`];
        const duration = (label, seconds) => seconds == null ? null : `${label} ${(Number(seconds) / 3600).toFixed(1)} h`;
        for (const item of [
          duration('Deep', value.deepSleepSeconds),
          duration('REM', value.remSleepSeconds),
          duration('Light', value.lightSleepSeconds),
          duration('Awake', value.awakeSeconds),
          value.averageHeartRate == null ? null : `Average heart rate ${Math.round(value.averageHeartRate)} bpm`,
          value.averageHrv == null ? null : `Average HRV ${Math.round(value.averageHrv)} ms`
        ]) if (item) parts.push(item);
        return parts.join(' · ');
      };
      const calories = value => {
        const parts = [];
        if (value.averageCalories != null) parts.push(`${Math.round(value.averageCalories)} kcal/day · ${value.entryCount} days`);
        const macro = (label, average, count) => average == null ? null : `${label} ${Number(average).toFixed(1)} g/day · ${count} days`;
        for (const item of [
          macro('Protein', value.averageProteinGrams, value.proteinDayCount),
          macro('Carbohydrate', value.averageCarbohydrateGrams, value.carbohydrateDayCount),
          macro('Fat', value.averageFatGrams, value.fatDayCount)
        ]) if (item) parts.push(item);
        return parts.length ? parts.join(' · ') : 'Not recorded';
      };
      const workouts = value => {
        if (value.workoutCount === 0) return 'No workouts recorded';
        const recorded = (count, label, valueText) => count === 0 ? `${label} not recorded` : `${valueText} (${count} records)`;
        return [
          `${value.workoutCount} sessions`,
          recorded(value.durationReadingCount, 'Duration', `${Math.round(value.totalDurationSeconds / 60)} timed min`),
          recorded(value.distanceReadingCount, 'Distance', `${Number(value.totalDistanceKm).toFixed(1)} km`),
          recorded(value.calorieReadingCount, 'Workout calories', `${value.totalCalories} kcal`),
          recorded(value.strengthSetCount, 'Strength volume', `${Number(value.strengthVolumeKg).toFixed(1)} kg`)
        ].join(' · ');
      };
      const sickness = value => {
        const types = Object.entries(value.sicknessesByType || {}).map(([type, count]) => `${type}: ${count}`).join(', ');
        const severities = Object.entries(value.sicknessesBySeverity || {}).map(([level, count]) => `${level}: ${count}`).join(', ');
        return types ? `Types ${types}${severities ? ` · Severity ${severities}` : ''}` : 'No sicknesses recorded';
      };
      const decisions = value => value.decisions.winRate == null ? 'No wins or misses recorded' : `${value.decisions.wins} wins · ${value.decisions.misses} misses · ${Math.round(value.decisions.winRate)}% wins`;
      const routines = value => value.routineCompletion.percentage == null ? 'No opportunities' : `${value.routineCompletion.completed}/${value.routineCompletion.opportunities} · ${Math.round(value.routineCompletion.percentage)}%`;
      const averageStatus = value => value == null ? 'Not recorded' : [
        ['Routines', value.routinesPercentage],
        ['Weight', value.weightPercentage],
        ['Blood pressure', value.bloodPressurePercentage],
        ['Flexibility', value.flexibilityPercentage],
        ['Mind', value.mindPercentage]
      ].map(([label, percentage]) => `${label} ${percentage == null ? 'not recorded' : `${Math.round(percentage)}%`}`).join(' · ');
      return [
        row('Routine completion', routines(currentPeriod), routines(previousComparablePeriod), routines(yearAgoComparablePeriod)),
        row('Average weight and body composition', averageWeight(currentPeriod.weight), averageWeight(previousComparablePeriod.weight), averageWeight(yearAgoComparablePeriod.weight)),
        row('Average blood pressure', bloodPressure(currentPeriod.bloodPressure), bloodPressure(previousComparablePeriod.bloodPressure), bloodPressure(yearAgoComparablePeriod.bloodPressure)),
        row('Mood', mood(currentPeriod), mood(previousComparablePeriod), mood(yearAgoComparablePeriod)),
        row('Sleep', sleep(currentPeriod.sleep), sleep(previousComparablePeriod.sleep), sleep(yearAgoComparablePeriod.sleep)),
        row('Nutrition', calories(currentPeriod.calories), calories(previousComparablePeriod.calories), calories(yearAgoComparablePeriod.calories)),
        row('Workouts', workouts(currentPeriod.workouts), workouts(previousComparablePeriod.workouts), workouts(yearAgoComparablePeriod.workouts)),
        row('Sickness', sickness(currentPeriod), sickness(previousComparablePeriod), sickness(yearAgoComparablePeriod)),
        row('Wins and misses', decisions(currentPeriod), decisions(previousComparablePeriod), decisions(yearAgoComparablePeriod)),
        row('Daily status completion', averageStatus(currentPeriod.dashboard), averageStatus(previousComparablePeriod.dashboard), averageStatus(yearAgoComparablePeriod.dashboard))
      ];
    },
    reflection_sections() {
      if (!this.detail?.reflection) return [];
      return [
        {title: 'Body composition', content: this.detail.reflection.bodyComposition},
        {title: 'Blood pressure', content: this.detail.reflection.bloodPressure},
        {title: 'Routines', content: this.detail.reflection.routines},
        {title: 'Nutrition', content: this.detail.reflection.nutrition},
        {title: 'Training and recovery', content: this.detail.reflection.trainingRecovery},
        {title: 'Goal progress', content: this.detail.reflection.goalProgress}
      ];
    }
  },
  async mounted() {
    await this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      try {
        [this.archive, this.preview] = await Promise.all([weeklySummaryService.getArchive(), weeklySummaryService.getPreview()]);
        const selectedDate = this.$route.query.date || (this.preview.alreadySaved ? this.preview.fridayDate : this.archive.summaries[0]?.fridayDate);
        if (selectedDate) await this.load_detail(selectedDate, false);
      } catch (error) {
        this.error = error.message || 'Weekly summaries could not be loaded.';
      } finally {
        this.loading = false;
      }
    },
    async load_detail(fridayDate, updateRoute = true) {
      this.detail = await weeklySummaryService.getSummary(fridayDate);
      if (updateRoute) await this.$router.replace({name: 'WeeklySummaries', query: {date: fridayDate}});
    },
    async select_summary(fridayDate) {
      this.loading = true;
      this.error = null;
      try {
        await this.load_detail(fridayDate);
      } catch (error) {
        this.error = error.message || 'The saved summary could not be loaded.';
      } finally {
        this.loading = false;
      }
    },
    is_selected(fridayDate) {
      return this.detail?.fridayDate === fridayDate;
    },
    async create_or_open_latest() {
      if (this.preview.alreadySaved) {
        await this.select_summary(this.preview.fridayDate);
        return;
      }
      if (this.preview.snapshot.warnings.length) {
        this.showMissingOutcomeConfirm = true;
        return;
      }
      await this.save_latest();
    },
    async save_latest() {
      this.creating = true;
      this.error = null;
      try {
        this.detail = await weeklySummaryService.createLatest();
        await this.$router.replace({name: 'WeeklySummaries', query: {date: this.detail.fridayDate}});
        await this.reload_archive_preview();
        this.showMissingOutcomeConfirm = false;
      } catch (error) {
        this.error = error.message || 'The weekly summary could not be saved.';
      } finally {
        this.creating = false;
      }
    },
    async reload_archive_preview() {
      [this.archive, this.preview] = await Promise.all([weeklySummaryService.getArchive(), weeklySummaryService.getPreview()]);
    },
    async refresh_summary() {
      await this.select_summary(this.detail.fridayDate);
      await this.reload_archive_preview();
    },
    open_coach() {
      const prompt = buildWeeklyReflectionPrompt(this.detail.fridayDate);
      const copied = navigator.clipboard.writeText(prompt);
      openCoach();
      copied.then(() => this.$toast.add({severity: 'info', summary: 'Weekly reflection prompt copied', detail: 'Paste it into ChatGPT to continue.', life: 5000}))
        .catch(error => this.$toast.add({severity: 'error', summary: 'Prompt copy failed', detail: error.message || error, life: 5000}));
    },
    format_period(start, end) {
      return `${this.format_date(start)} – ${dayjs(`${end}T00:00:00`).format('D MMM YYYY')}`;
    },
    format_date(value) {
      return dayjs(`${value}T00:00:00`).format('D MMM YYYY');
    },
    format_timestamp(value) {
      return dayjs(value).format('D MMM YYYY, HH:mm');
    },
    outcome_weight(value) {
      return value ? `${Number(value.weightKg).toFixed(1)} kg · ${this.format_date(value.measuredDate)}` : 'Not recorded';
    },
    outcome_blood_pressure(value) {
      return value ? `${value.systolic} / ${value.diastolic} mmHg · ${this.format_date(value.measuredDate)}` : 'Not recorded';
    },
    body_composition(value) {
      const format = (amount, unit) => amount == null ? 'Not recorded' : `${Number(amount).toFixed(1)} ${unit}`;
      return `Fat ${format(value.fatKg, 'kg')} · ${format(value.fatPercentage, '%')} · muscle ${format(value.muscleKg, 'kg')} · ${format(value.musclePercentage, '%')}`;
    },
    format_record_value(value, unit) {
      const suffix = {KG: 'kg', PERCENT: '%', MM_HG: 'mmHg', KCAL: 'kcal', GRAMS: 'g', BPM: 'bpm', MILLISECONDS: 'ms'}[unit] || unit;
      return `${value} ${suffix}`;
    }
  }
};
</script>

<style scoped>
.weekly-page {
  --ink: #21313c;
  --muted: #63717b;
  --paper: #f4f0e8;
  --card: #fffdf8;
  --line: #d8d1c5;
  --green: #39745a;
  --amber: #855a18;
  max-width: 1240px;
  margin: 0 auto;
  padding: 1rem 1rem 2rem;
  color: var(--ink);
}
.weekly-header,
.preview-heading,
.section-heading,
.detail-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem; }
.weekly-header { margin-bottom: 0.8rem; }
.weekly-header h1,
.preview-heading h2,
.section-heading h2,
.detail-heading h2 { margin: 0; font-family: Georgia, 'Times New Roman', serif; }
.weekly-header h1 { font-size: clamp(2rem, 5vw, 3rem); line-height: 1; }
.weekly-header p,
.detail-heading p,
.comparison-caption,
.empty-note,
.preview-note { margin: 0.4rem 0 0; color: var(--muted); line-height: 1.5; }
.weekly-kicker { margin-bottom: 0.4rem; color: var(--green); font-size: 0.72rem; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.preview-card,
.archive-card,
.detail-card,
.empty-state { border: 1px solid var(--line); border-radius: 1rem; background: var(--card); box-shadow: 0 0.5rem 2rem rgba(41, 48, 52, 0.06); }
.preview-card { padding: 1rem; margin-bottom: 0.8rem; }
.preview-heading h2 { font-size: 1.35rem; }
.period-state,
.saved-label { color: var(--muted); font-size: 0.8rem; }
.period-state--saved { color: var(--green); font-weight: 700; }
.outcome-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.65rem; margin-top: 0.8rem; }
.outcome-item { display: grid; gap: 0.3rem; min-width: 0; padding: 0.8rem; border-radius: 0.7rem; background: var(--paper); }
.outcome-item > span { color: var(--muted); font-size: 0.76rem; font-weight: 700; text-transform: uppercase; }
.outcome-item strong { line-height: 1.4; overflow-wrap: anywhere; }
.outcome-item small { color: #43525c; line-height: 1.4; }
.warning-list { margin: 0.65rem 0 0; padding-left: 1.2rem; color: var(--amber); }
.weekly-layout { display: grid; grid-template-columns: minmax(230px, 0.34fr) minmax(0, 1fr); align-items: start; gap: 0.8rem; }
.archive-card { position: sticky; top: 0.8rem; padding: 0.85rem; }
.section-heading h2 { font-size: 1.2rem; }
.section-heading > span { color: var(--muted); font-size: 0.85rem; }
.archive-list { display: grid; gap: 0.35rem; margin-top: 0.65rem; }
.archive-item { display: grid; grid-template-columns: 1fr 1.1rem; gap: 0.2rem 0.5rem; width: 100%; padding: 0.65rem; border: 1px solid transparent; border-radius: 0.65rem; background: var(--paper); color: var(--ink); text-align: left; cursor: pointer; }
.archive-item:hover,
.archive-item.selected { border-color: var(--green); background: #edf3ed; }
.archive-period { font-weight: 700; }
.archive-status { color: var(--muted); font-size: 0.78rem; }
.archive-item > i { grid-column: 2; grid-row: 1 / 3; align-self: center; color: var(--green); }
.archive-empty { padding: 1rem 0.25rem; color: var(--muted); font-size: 0.9rem; text-align: center; }
.detail-card { min-width: 0; overflow: hidden; }
.detail-heading { align-items: flex-start; padding: 1rem; background: var(--paper); border-bottom: 1px solid var(--line); }
.detail-heading h2 { font-size: clamp(1.2rem, 3vw, 1.7rem); }
.detail-heading p { max-width: 720px; font-size: 0.88rem; }
.informational-badge { flex: 0 0 auto; padding: 0.3rem 0.55rem; border: 1px solid #b5c9cf; border-radius: 999px; color: #315f78; font-size: 0.76rem; }
.missing-data { margin: 0.8rem 1rem 0; padding: 0.75rem; border-radius: 0.65rem; background: #fff7e6; color: #5c471f; }
.missing-data ul { margin: 0.3rem 0 0; padding-left: 1.2rem; }
.detail-section,
.weekly-reflection { padding: 1rem; border-bottom: 1px solid var(--line); }
.detail-section h3,
.weekly-reflection h3 { margin: 0; font-size: 1.12rem; }
.detail-section > h3 { margin-top: 0.15rem; }
.comparison-caption { margin-bottom: 0.7rem; font-size: 0.84rem; }
.metrics-table { display: grid; width: 100%; overflow-x: auto; }
.metrics-row { display: grid; grid-template-columns: minmax(145px, 1.15fr) repeat(3, minmax(125px, 1fr)); min-width: 560px; border-bottom: 1px solid #e5dfd5; }
.metrics-row > * { padding: 0.55rem 0.45rem; line-height: 1.4; overflow-wrap: anywhere; }
.metrics-row > strong { color: #35434b; }
.metrics-row--heading { color: var(--muted); font-size: 0.78rem; font-weight: 700; }
.routine-list { display: grid; gap: 0.4rem; margin-top: 0.6rem; }
.routine-item { display: flex; align-items: center; flex-wrap: wrap; gap: 0.4rem 0.7rem; padding: 0.55rem 0.65rem; border-radius: 0.55rem; background: var(--paper); }
.routine-item > span { color: var(--muted); font-size: 0.84rem; }
.routine-item--watch { border-left: 3px solid #b1682b; }
.watch-label { color: #855a18 !important; font-weight: 700; }
.record-list { display: grid; gap: 0.35rem; margin: 0.6rem 0 0; padding: 0; list-style: none; }
.record-list li { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 0.25rem 0.75rem; padding: 0.5rem 0; border-bottom: 1px solid #e5dfd5; }
.record-list span { color: var(--muted); }
.weekly-reflection { border-bottom: 0; background: #fbfaf6; }
.weekly-reflection > .section-heading { align-items: baseline; }
.reflection-summary { margin: 0.8rem 0; line-height: 1.55; }
.reflection-sections { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.55rem; }
.reflection-section { padding: 0.7rem; border-radius: 0.65rem; background: var(--paper); }
.reflection-section h4,
.next-week-actions h4 { margin: 0 0 0.35rem; font-size: 0.95rem; }
.reflection-section p { margin: 0.25rem 0; line-height: 1.45; }
.next-week-actions { margin-top: 0.8rem; }
.next-week-actions ul { margin: 0; padding-left: 1.2rem; line-height: 1.5; }
.reflection-actions { margin-top: 0.8rem; }
.empty-state { padding: 2rem 1rem; text-align: center; }
.empty-state > i { color: var(--green); font-size: 1.8rem; }
.empty-state h2 { margin: 0.5rem 0; font-family: Georgia, 'Times New Roman', serif; }
.empty-state p { color: var(--muted); }
@media (max-width: 760px) {
  .weekly-page { padding: 1rem 0 2rem; }
  .weekly-header { align-items: flex-start; flex-direction: column; }
  .weekly-layout { grid-template-columns: 1fr; }
  .archive-card { position: static; }
  .detail-heading { flex-direction: column; }
  .reflection-sections { grid-template-columns: 1fr; }
  .metrics-row { min-width: 0; grid-template-columns: minmax(0, 1fr) repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 480px) {
  .outcome-grid { grid-template-columns: 1fr; }
  .preview-heading { align-items: flex-start; flex-direction: column; }
  .detail-section,
  .weekly-reflection { padding: 0.8rem; }
  .missing-data { margin-right: 0.8rem; margin-left: 0.8rem; }
  .saved-label { display: block; margin-top: 0.3rem; }
  .metrics-row--heading { display: none; }
  .metrics-row { grid-template-columns: minmax(0, 1fr); padding: 0.45rem 0; }
  .metrics-row > * { padding: 0.25rem 0; }
  .metrics-row > [role="cell"]::before { content: attr(data-period) ': '; color: var(--muted); font-size: 0.76rem; font-weight: 700; }
}
</style>
