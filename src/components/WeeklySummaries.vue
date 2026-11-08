<template>
  <loading v-model:active="loading" :can-cancel="false" :is-full-page="true" />
  <main class="weekly-page">
    <header class="weekly-header">
      <div>
        <div class="weekly-kicker">Personal review</div>
        <h1>Weekly summaries</h1>
      </div>
      <Button v-if="is_detail_page" label="Back to weekly summaries" icon="pi pi-arrow-left" class="p-button-text" @click="$router.push({name: 'WeeklySummaries'})" />
      <ActionButton v-else-if="preview"
              :label="preview.alreadySaved ? selected_friday_date ? 'Open saved summary' : 'Open latest summary' : 'Create weekly summary'"
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

    <section v-if="!is_detail_page && preview && preview.snapshot" class="preview-card">
      <div class="preview-heading">
        <div>
          <div class="weekly-kicker">{{ selected_friday_date ? 'Reminder week' : 'Latest eligible period' }}</div>
          <h2>{{ format_period(preview.periodStart, preview.fridayDate) }}</h2>
        </div>
        <span class="period-state" :class="{'period-state--saved': preview.alreadySaved}">
          {{ preview.alreadySaved ? 'Saved' : 'Ready to save' }}
        </span>
      </div>
      <p class="comparison-caption">Selected outcome readings · {{ outcome_window(preview.fridayDate) }}</p>
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
      <p v-if="!preview.alreadySaved" class="preview-note">
        Available from Saturday. Saving fixes the summary; later measurements won’t be included.
      </p>
    </section>

    <section class="weekly-layout" :class="{'weekly-layout--detail': is_detail_page}">
      <aside v-if="!is_detail_page" class="archive-card" aria-label="Saved weekly summaries">
        <div class="section-heading">
          <div>
            <div class="weekly-kicker">Archive</div>
            <h2>Saved weeks</h2>
          </div>
          <span class="archive-count">{{ archive_range }} of {{ archive?.totalElements || 0 }} weeks</span>
        </div>
        <div v-if="archive?.items.length" class="archive-list">
          <button v-for="item in archive.items"
                  :key="item.fridayDate"
                  type="button"
                  class="archive-item"
                  @click="select_summary(item.fridayDate)">
            <span class="archive-period">{{ format_period(item.periodStart, item.fridayDate) }}</span>
            <span class="archive-status">{{ item.reflectionSaved ? 'Weekly reflection saved' : 'No reflection yet' }}</span>
            <i class="pi pi-arrow-right" aria-hidden="true"></i>
          </button>
        </div>
        <div v-else class="archive-empty">Saved summaries will appear here.</div>
        <Paginator v-if="archive && archive.totalPages > 1"
                   :first="archive.page * archive.size"
                   :rows="archive.size"
                   :totalRecords="archive.totalElements"
                   :template="{ '640px': 'PrevPageLink CurrentPageReport NextPageLink', default: 'FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink' }"
                   currentPageReportTemplate="{first}–{last} of {totalRecords}"
                   @page="load_archive_page($event.page)" />
      </aside>

      <section v-if="is_detail_page && detail && detail.fridayDate === $route.params.fridayDate" :key="detail.fridayDate" ref="detail" class="detail-card" aria-label="Weekly summary details" tabindex="-1">
        <header class="detail-heading">
          <div>
            <div class="weekly-kicker">Saturday–Friday summary</div>
            <h2>{{ format_period(detail.periodStart, detail.fridayDate) }}</h2>
            <p>Saved {{ format_timestamp(detail.createdAt) }}</p>
          </div>
          <span class="informational-badge">Recorded evidence only</span>
        </header>

        <div v-if="detail.snapshot.warnings.length" class="missing-data" role="status">
          <strong>Missing measurements</strong>
          <ul><li v-for="warning in detail.snapshot.warnings" :key="warning">{{ warning }}</li></ul>
        </div>

        <div class="review-content">
          <ScrollableTabView v-model:activeIndex="review_index" scrollable selectOnFocus :pt="{nav: {'aria-label': 'Weekly review'}}">
          <!-- PrimeVue 3 selectOnFocus reads data-index from each header. -->
          <TabPanel header="Overview" :headerProps="{'data-index': 0}">
            <section class="routine-hero" aria-label="Daily routine completion">
              <div class="section-heading"><h3>Daily routines</h3><Button label="View all routines" class="p-button-outlined p-button-sm" @click="open_measurements('routines')" /></div>
              <div class="routine-hero-columns"><div><div class="routine-score"><strong>{{ routine_hero.value }}</strong><span>{{ routine_hero.completed }}/{{ routine_hero.opportunities }} opportunities</span></div>
              <div class="routine-comparisons"><span v-for="(change, index) in [routine_hero.previous, routine_hero.yearAgo]" :key="index" :style="{color: change?.color}">{{ change?.text }} <small>vs {{ index ? '52 weeks ago' : 'last week' }}</small></span></div>
              <div v-if="routine_hero.days.length" class="routine-days"><div v-for="day in routine_hero.days" :key="day.label"><span>{{ day.label }}</span><div class="routine-track"><span :style="{width: day.width + '%', backgroundColor: day.status.color}"></span></div><strong>{{ day.value }}</strong></div></div>
              </div><div><h4 v-if="routine_hero.watchouts.length">Needs attention</h4>
              <div class="routine-attention-list"><div v-for="routine in routine_hero.watchouts" :key="routine.name" class="routine-attention"><strong>{{ routine.name }}</strong><span>{{ routine.value }}</span></div></div>
              </div></div><p v-if="!routine_hero.opportunities" class="empty-note">No active routine opportunities this week.</p>
            </section>
            <section class="detail-section">
              <div class="section-heading"><h3>Your week at a glance</h3></div>
              <div v-if="visual" class="weekly-highlights"><article v-for="card in average_cards" :key="card.key" class="weekly-highlight"><span>{{ card.label }}</span><strong :style="{color: card.key === 'weight' ? undefined : card.status.color}">{{ card.value }}</strong><small>{{ card.detail }}</small><span class="review-status" :style="{color: card.status.color}">{{ card.key === 'weight' ? 'Body fat · ' : '' }}{{ card.status.label }}</span><small v-for="(change, index) in [card.previous, card.yearAgo]" :key="index" :style="{color: change.color}">{{ change.text }} · vs {{ index ? '52 weeks ago' : 'last week' }}</small></article></div>
              <div v-else-if="detail.presentation" class="weekly-highlights"><article v-for="highlight in detail.presentation.highlights.filter(row => !['records', 'routines'].includes(row.key))" :key="highlight.key" class="weekly-highlight"><span>{{ highlight.label }}</span><strong>{{ highlight.value }}</strong><small>{{ highlight.comparison }}</small><small>{{ highlight.coverage }}</small></article></div>
              <Button label="Explore measurements" icon="pi pi-arrow-right" class="p-button-outlined p-button-sm section-link" @click="open_measurements('body')" />
            </section>
            <section v-if="visual" class="detail-section"><div class="section-heading"><h3>Weekend readings</h3><span>{{ outcome_window(detail.fridayDate) }} · selected measurements</span></div><div class="weekly-highlights weekend-readings"><article v-for="card in weekend_cards" :key="card.key" class="weekly-highlight"><span>{{ card.label }}</span><strong :style="{color: card.key === 'weekendWeight' ? undefined : card.status.color}">{{ card.value }}</strong><small>{{ card.detail }}</small><span class="review-status" :style="{color: card.status.color}">{{ card.key === 'weekendWeight' ? 'Body fat · ' : '' }}{{ card.status.label }}</span><small v-for="(change, index) in [card.previous, card.yearAgo]" :key="index" :style="{color: change.color}">{{ change.text }} · vs {{ index ? '52 weeks ago' : 'last week' }}</small></article></div></section>
            <section class="detail-section"><div class="section-heading"><h3>Workout activity</h3><Button label="Training details" class="p-button-text p-button-sm" @click="open_measurements('training')" /></div><p v-if="workout_targets_missing" class="comparison-caption">Workout targets weren’t saved with this review.</p><WeeklyMetricTable label="Workout activity overview" :periods="comparison_periods" :rows="workout_table_rows.filter(row => row.label !== 'Total sessions')" /></section>
            <section class="detail-section"><div class="section-heading"><h3>Nutrition</h3><Button label="Comparisons & details" class="p-button-text p-button-sm" @click="open_measurements('nutrition')" /></div><WeeklyMetricTable label="Nutrition daily averages" :periods="comparison_periods" :rows="nutrition_table_rows" compactCurrent><template #target="{row}"><Button v-if="row.key === 'omega3'" label="View references" class="p-button-text p-button-sm" @click="open_omega_references" /><span v-else>{{ row.cells[0].target || 'Not available' }}</span></template></WeeklyMetricTable></section>
            <section class="detail-section reflection-teaser"><h3>{{ detail.reflection?.title || 'Weekly reflection' }}</h3><p>{{ detail.reflection?.summary || 'Review your saved evidence and choose next-week priorities.' }}</p><Button label="Reflection & actions" class="p-button-outlined" @click="review_tab = 'reflection'" /></section>
          </TabPanel>
          <TabPanel header="Measurements" :headerProps="{'data-index': 1}">
            <ScrollableTabView ref="measurementTabs" v-model:activeIndex="measurement_index" scrollable selectOnFocus :pt="{nav: {'aria-label': 'Measurement groups'}}">
            <TabPanel v-for="(tab, index) in measurement_tabs" :key="tab.key" :header="tab.label" :headerProps="{'data-index': index}">
              <p class="comparison-caption">Saturday–Friday averages · changes show this week minus each comparison period.</p>
              <template v-if="tab.key === 'body'">
                <WeeklyMetricTable label="Weekly recorded metrics" :periods="comparison_periods" :rows="measurement_groups.body" />
        <section class="detail-section">
          <h4 class="outcome-heading">Selected outcome readings</h4>
          <p class="comparison-caption">{{ outcome_window(detail.fridayDate) }}</p>
          <div class="outcome-grid">
            <article class="outcome-item">
              <span>Weight</span>
              <strong>{{ outcome_weight(detail.snapshot.outcomes.weight) }}</strong><small v-if="outcome_status(detail.snapshot.outcomes.weight?.fatPercentage, 'bodyFat')" :style="{color: outcome_status(detail.snapshot.outcomes.weight?.fatPercentage, 'bodyFat').color}">Body fat · {{ outcome_status(detail.snapshot.outcomes.weight?.fatPercentage, 'bodyFat').label }}</small>
              <small v-if="detail.snapshot.outcomes.weight">
                {{ body_composition(detail.snapshot.outcomes.weight) }}
              </small>
            </article>
            <article class="outcome-item">
              <span>Blood pressure</span>
              <strong>{{ outcome_blood_pressure(detail.snapshot.outcomes.bloodPressure) }}</strong><small v-if="outcome_status(detail.snapshot.outcomes.bloodPressure, 'pressure')" :style="{color: outcome_status(detail.snapshot.outcomes.bloodPressure, 'pressure').color}">{{ outcome_status(detail.snapshot.outcomes.bloodPressure, 'pressure').label }}</small>
            </article>
          </div>
        </section>


              </template>
              <template v-else-if="tab.key === 'sleep'"><WeeklyMetricTable label="Sleep and mood measurements" :periods="comparison_periods" :rows="measurement_groups.sleep" /></template>
              <template v-else-if="tab.key === 'training'">
                <h3>Workout activity</h3><p v-if="workout_targets_missing" class="comparison-caption">Workout targets weren’t saved with this review.</p><WeeklyMetricTable label="Weekly workout activity" group="Workout activity" :periods="comparison_periods" :rows="workout_table_rows" />
                <WeeklyMetricTable label="Workout measurements" :periods="comparison_periods" :rows="measurement_groups.training" />
        <section class="detail-section">
          <h3>Back pain</h3><h4>Check-ins and episodes</h4>
          <p v-if="!detail.snapshot.backPain" class="empty-note">Back-pain details were not included in this saved snapshot.</p>
          <p v-else-if="detail.snapshot.backPain.checkInCount === 0" class="empty-note">No back-pain check-ins recorded.</p>
          <template v-else>
            <p>{{ detail.snapshot.backPain.checkInCount }} check-ins · {{ detail.snapshot.backPain.episodeCount }} pain episodes across {{ detail.snapshot.backPain.painDayCount }} days</p>
            <dl class="back-pain-breakdown">
              <template v-for="group in back_pain_groups" :key="group.label">
                <div v-if="group.entries.length"><dt>{{ group.label }}</dt><dd>{{ group.entries.join(' · ') }}</dd></div>
              </template>
            </dl>
          </template>
        </section>


              </template>
              <template v-else-if="tab.key === 'nutrition'">
                <h3>Nutrition · daily averages</h3>
                <WeeklyMetricTable label="Weekly calories" :periods="comparison_periods" :rows="[measurement_groups.calories]" />
                <WeeklyMetricTable label="Weekly nutrition evidence" :periods="comparison_periods" :rows="nutrition_table_rows"><template #cell="{row, index}"><Button v-if="row.key === 'omega3' && index === 0" label="View references" class="p-button-text p-button-sm" @click="show_omega_references" /></template></WeeklyMetricTable>
                <details v-if="visual?.nutrition.references?.length" ref="omegaReferences" class="detail-section"><summary class="detail-section-summary"><strong>Omega-3 references</strong></summary><WeeklyMetricTable label="Omega-3 references" :periods="comparison_periods" :rows="omega_reference_rows" /></details>
                <details class="detail-section"><summary class="detail-section-summary"><strong>Targets, coverage & estimates</strong></summary><p class="comparison-caption">{{ visual?.nutrition.note || 'Current targets captured when saved' }}</p><p v-if="!detail.snapshot.nutrition" class="empty-note">Nutrition details were not included in this saved snapshot.</p><p v-for="row in visual?.nutrition.rows || []" :key="row.key">{{ row.label }} · {{ row.coverage }}</p></details>
              </template>
              <template v-else-if="tab.key === 'routines'">
                <h3>Daily routines</h3><div class="routine-search"><label for="weekly-routine-search">Find a routine</label><InputText id="weekly-routine-search" v-model="routine_search" placeholder="Find a routine" /></div>
                <div v-if="filtered_routines.length" class="routine-list"><div v-for="routine in filtered_routines" :key="routine.name" class="routine-item" :class="{'routine-item--watch': Number(routine.percentage) < 60}"><strong>{{ routine.name }}</strong><div class="routine-track"><span :style="{width: routine.percentage + '%'}"></span></div><span>{{ routine.completedDays }}/{{ routine.eligibleDays }} days · {{ Number(routine.percentage).toFixed(0) }}%</span><span v-if="Number(routine.percentage) < 60" class="watch-label">Below 60%</span></div></div><p v-else class="empty-note">{{ routine_search ? 'No matching routines.' : 'No active routine opportunities this week.' }}</p>
        <section v-if="detail.snapshot.weeklyRoutines" class="detail-section">
          <h3>Weekly routines</h3><p class="comparison-caption">Saturday–Friday opportunities · separate from daily completion</p>
          <div v-if="detail.snapshot.weeklyRoutines.length" class="routine-list">
            <div v-for="routine in detail.snapshot.weeklyRoutines" :key="routine.name" class="routine-item">
              <strong>{{ routine.name }}</strong>
              <span>{{ routine.completedWeeks }}/{{ routine.eligibleWeeks }} weeks · {{ Number(routine.percentage).toFixed(0) }}%</span>
              <span v-if="routine.checkinDates.length">Recorded {{ routine.checkinDates.map(format_date).join(', ') }}</span>
            </div>
          </div>
          <p v-else class="empty-note">No active weekly routines.</p>
        </section>


              </template>
              <template v-else><h3>Other recorded metrics</h3><WeeklyMetricTable label="Other weekly metrics" :periods="comparison_periods" :rows="other_table_rows" /></template>
            </TabPanel>
            </ScrollableTabView>
          </TabPanel>
          <TabPanel header="Reflection &amp; actions" :headerProps="{'data-index': 2}">
        <section class="weekly-reflection">
          <header class="section-heading"><div><div class="weekly-kicker">Weekly reflection</div><h3>{{ detail.reflection ? detail.reflection.title : 'No reflection saved yet' }}</h3></div><span v-if="detail.reflection" class="saved-label">Saved {{ format_timestamp(detail.reflection.generatedAt) }}</span></header>
          <p v-if="detail.reflection" class="reflection-summary">{{ detail.reflection.summary }}</p>
          <p v-else-if="archive.actionConfigured" class="empty-note">Ask the Coach to review this saved snapshot. The reflection is stored separately from daily reflections.</p>
          <p v-else class="empty-note">Weekly reflection Actions are not configured yet. This saved snapshot remains available for review.</p>
          <WarningReview v-if="detail.reflection" :review="detail.reflection.warningReview" />
          <details v-if="detail.reflection" class="reflection-details">
            <summary class="detail-section-summary"><strong>Reflection details</strong></summary>
            <div class="reflection-sections">
              <article v-for="section in reflection_sections" :key="section.title" class="reflection-section">
                <h4>{{ section.title }}</h4>
                <p>{{ section.content.summary }}</p>
              </article>
            </div>
          </details>
          <div class="reflection-actions action-group">
            <Button :label="detail.reflection ? 'Update reflection' : 'Add reflection'"
                    icon="chatgpt-icon"
                    :pt="{icon: {'aria-hidden': true}}"
                    :disabled="!archive.actionConfigured"
                    @click="open_coach" />
            <ActionButton label="Refresh reflection" busyLabel="Refreshing…" icon="pi pi-refresh" class="p-button-outlined" :action="refresh_summary" />
          </div>
        </section>


            <section v-if="review_tab === 'reflection'" class="detail-section" aria-label="Current actions"><DashboardActions :key="actions_revision" :date="actions_date" readOnly showAll /></section>
            <section class="detail-section"><h3>Next-week priorities</h3><p v-if="!priority_actions.length" class="empty-note">No next-week priorities in this saved report yet.</p><article v-for="action in priority_actions" :key="action.sourceKey" class="weekly-recommendation"><h4>{{ action.wording }}</h4><p>{{ action.provenance.join(' · ') }}</p><router-link :to="{path: '/actions', query: {source: action.sourceKey}}" class="p-button p-component p-button-outlined recommendation-link">Activate action</router-link></article>
              <details v-if="remaining_actions.length" class="more-recommendations"><summary class="detail-section-summary"><strong>More recommendations · {{ remaining_actions.length }}</strong></summary><article v-for="action in remaining_actions" :key="action.sourceKey" class="weekly-recommendation"><h4>{{ action.wording }}</h4><p>{{ action.provenance.join(' · ') }}</p><router-link :to="{path: '/actions', query: {source: action.sourceKey}}" class="p-button p-component p-button-outlined recommendation-link">Activate action</router-link></article></details>
            </section>
        <details class="detail-section">
          <summary class="detail-section-summary"><span class="weekly-kicker">Goal context</span><strong>Plan evidence</strong></summary>
          <p v-if="detail.snapshot.goalEvidence.available">{{ detail.snapshot.goalEvidence.goal }}<span v-if="detail.snapshot.goalEvidence.startDate"> · Started {{ format_date(detail.snapshot.goalEvidence.startDate) }}</span><span v-if="detail.snapshot.goalEvidence.reviewDate"> · Review {{ format_date(detail.snapshot.goalEvidence.reviewDate) }}</span></p>
          <p v-else class="empty-note">{{ detail.snapshot.goalEvidence.unavailableReason }}</p>
        </details>

        <section class="detail-section">
          <h3>Personal records <small v-if="detail.presentation">{{ detail.snapshot.personalRecords.length }} new</small></h3>
          <ul v-if="detail.snapshot.personalRecords.length" class="record-list">
            <li v-for="record in detail.snapshot.personalRecords" :key="`${record.label}-${record.subjectLabel}-${record.date}`">
              <strong>{{ record.label }}<span v-if="record.subjectLabel"> — {{ record.subjectLabel }}</span></strong><span>{{ format_record_value(record.value, record.unit) }} · {{ format_date(record.date) }}</span>
            </li>
          </ul>
          <p v-else class="empty-note">No new personal records this week.</p>
        </section>


          </TabPanel>
          </ScrollableTabView>
        </div>
      </section>

      <section v-else-if="!is_detail_page && !preview?.snapshot" class="empty-state">
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
import {nextTick} from 'vue';
import Paginator from 'primevue/paginator';
import WarningReview from './WarningReview.vue';
import ScrollableTabView from './ScrollableTabView.vue';
import DashboardActions from './DashboardActions.vue';
import {actionToday} from '@/services/ActionService';
import WeeklyMetricTable from './WeeklyMetricTable.vue';
import {weeklyMeasurementGroups, visualTableRows, weeklyClassification} from '@/model/WeeklyReview';

export default {
  name: 'WeeklySummaries',
  components: {Paginator, WarningReview, ScrollableTabView, DashboardActions, WeeklyMetricTable},
  data() {
    return {
      review_tab: 'overview', measurement_tab: 'body', routine_search: '', actions_date: actionToday(), actions_revision: 0,
      review_tabs: [{key: 'overview', label: 'Overview'}, {key: 'measurements', label: 'Measurements'}, {key: 'reflection', label: 'Reflection & actions'}],
      measurement_tabs: [{key: 'body', label: 'Body & pressure'}, {key: 'sleep', label: 'Sleep & mood'}, {key: 'training', label: 'Training & back pain'}, {key: 'nutrition', label: 'Nutrition'}, {key: 'routines', label: 'Routines'}, {key: 'other', label: 'Other metrics'}],
      archive: null,
      preview: null,
      detail: null,
      loading: true,
      creating: false,
      showMissingOutcomeConfirm: false,
      error: null
    };
  },
  watch: {
    review_tab(value) { if (value === 'reflection') this.actions_date = actionToday(); },
    '$route.fullPath'() {
      if (['WeeklySummaries', 'WeeklySummaryDetail'].includes(this.$route.name)) this.load();
    }
  },
  computed: {
    review_index: {get() { return this.review_tabs.findIndex(tab => tab.key === this.review_tab); }, set(index) { this.review_tab = this.review_tabs[index].key; }},
    measurement_index: {get() { return this.measurement_tabs.findIndex(tab => tab.key === this.measurement_tab); }, set(index) { this.measurement_tab = this.measurement_tabs[index].key; }},
    workout_targets_missing() { return this.detail.snapshot.workoutTargets == null; },
    average_cards() { return this.visual.overview.filter(card => !['weekendWeight', 'bloodPressure', 'weekendPressure'].includes(card.key)); },
    weekend_cards() { return this.visual.overview.filter(card => ['weekendWeight', 'bloodPressure', 'weekendPressure'].includes(card.key)); },
    visual() { return this.detail?.presentation?.visualSummary; },
    measurement_groups() { return weeklyMeasurementGroups(this.detail.snapshot); },
    filtered_routines() { return this.detail.snapshot.routines.filter(row => row.name.toLowerCase().includes(this.routine_search.toLowerCase())); },
    priority_actions() { return (this.detail.presentation?.recommendations || []).filter(action => action.provenance.includes('Next week')).slice(0, 3); },
    remaining_actions() { const keys = new Set(this.priority_actions.map(action => action.sourceKey)); return (this.detail.presentation?.recommendations || []).filter(action => !keys.has(action.sourceKey)); },
    routine_hero() {
      if (this.visual) return this.visual.routines;
      const progress = this.detail.snapshot.progress;
      const current = progress.currentPeriod.routineCompletion;
      const change = period => ({text: current.percentage == null || period.routineCompletion.percentage == null ? 'Not available' : `${Number((current.percentage - period.routineCompletion.percentage).toFixed(1))} pp`});
      return {value: current.percentage == null ? 'Not available' : `${Math.round(current.percentage)}%`, completed: current.completed, opportunities: current.opportunities, days: [], previous: change(progress.previousComparablePeriod), yearAgo: change(progress.yearAgoComparablePeriod), watchouts: [...this.detail.snapshot.routines].filter(row => Number(row.percentage) < 60).sort((a, b) => a.percentage - b.percentage || a.name.localeCompare(b.name)).slice(0, 3).map(row => ({name: row.name, value: `${row.completedDays}/${row.eligibleDays} days · ${Math.round(row.percentage)}%`}))};
    },
    workout_table_rows() {
      if (!this.visual) return this.workout_rows.map(row => ({label: row.label, cells: [row.current, row.previous, row.yearAgo].map(value => ({value}))}));
      return visualTableRows(this.visual.workouts).map(row => ({...row, cells: row.cells.map(cell => {
        const missingTarget = ['Plan unavailable', 'Target unavailable'].includes(cell.status?.label) || cell.target === 'Plan unavailable';
        return missingTarget ? {...cell, status: null, target: this.workout_targets_missing ? null : 'Target unavailable'} : cell;
      })}));
    },
    nutrition_table_rows() {
      if (this.visual) return visualTableRows(this.visual.nutrition.rows);
      if (this.detail.presentation?.nutritionRows?.length) return this.detail.presentation.nutritionRows.map(row => ({label: row.label, cells: [row.current, row.previous, row.yearAgo].map(value => ({value}))}));
      return this.measurement_groups.nutrition;
    },
    omega_reference_rows() { return visualTableRows(this.visual.nutrition.references); },
    other_table_rows() { return this.metric_rows.filter(row => ['Sickness', 'Wins and misses', 'Daily status completion', 'Routine completion'].includes(row.label)).map(row => ({label: row.label, cells: [row.current, row.previous, row.yearAgo].map(value => ({value}))})); },
    is_detail_page() {
      return Boolean(this.$route.params.fridayDate);
    },
    selected_friday_date() {
      return this.is_detail_page ? null : this.$route.query.selectedFridayDate || null;
    },
    back_pain_groups() {
      const pain = this.detail?.snapshot.backPain;
      if (!pain) return [];
      const entries = (values, order) => Object.entries(values || {})
        .sort(([first], [second]) => order.indexOf(first) - order.indexOf(second))
        .map(([key, count]) => `${key.toLowerCase()}: ${count}`);
      return [
        {label: 'Severity', entries: entries(pain.episodesBySeverity, ['MILD', 'MODERATE', 'SEVERE', 'EXTREME'])},
        {label: 'Region', entries: entries(pain.episodesByRegion, ['UPPER', 'MIDDLE', 'LOWER'])},
        {label: 'Side', entries: entries(pain.episodesBySide, ['LEFT', 'CENTER', 'RIGHT'])}
      ];
    },
    archive_range() {
      if (!this.archive?.totalElements) return 0;
      const first = this.archive.page * this.archive.size + 1;
      return `${first}–${Math.min(first + this.archive.size - 1, this.archive.totalElements)}`;
    },
    comparison_periods() {
      const {currentPeriod, previousComparablePeriod, yearAgoComparablePeriod} = this.detail.snapshot.progress;
      return [
        {label: 'This week', period: currentPeriod},
        {label: 'Last week', period: previousComparablePeriod},
        {label: '52 weeks ago', period: yearAgoComparablePeriod}
      ].map(({label, period}) => {
        const dates = this.format_period(period.startDate, period.endDate);
        return {label, dates, description: `${label} · ${dates}`};
      });
    },
    metric_rows() {
      const progress = this.detail.snapshot.progress;
      const periods = [progress.currentPeriod, progress.previousComparablePeriod, progress.yearAgoComparablePeriod];
      const row = (label, read) => { const values = periods.map(read); return {label, current: values[0], previous: values[1], yearAgo: values[2]}; };
      return [
        row('Routine completion', period => period.routineCompletion.percentage == null ? 'No opportunities' : `${period.routineCompletion.completed}/${period.routineCompletion.opportunities} · ${Math.round(period.routineCompletion.percentage)}%`),
        row('Sickness', period => {
          const types = Object.entries(period.sicknessesByType || {}).map(([type, count]) => `${type}: ${count}`).join(', ');
          const severities = Object.entries(period.sicknessesBySeverity || {}).map(([level, count]) => `${level}: ${count}`).join(', ');
          return types ? `Types ${types}${severities ? ` · Severity ${severities}` : ''}` : 'No sicknesses recorded';
        }),
        row('Wins and misses', period => period.decisions.winRate == null ? 'No wins or misses recorded' : `${period.decisions.wins} wins · ${period.decisions.misses} misses · ${Math.round(period.decisions.winRate)}% wins`),
        row('Daily status completion', period => period.dashboard == null ? 'Not recorded' : [['Routines', 'routinesPercentage'], ['Weight', 'weightPercentage'], ['Blood pressure', 'bloodPressurePercentage'], ['Flexibility', 'flexibilityPercentage'], ['Mind', 'mindPercentage']].map(([label, field]) => `${label} ${period.dashboard[field] == null ? 'not recorded' : `${Math.round(period.dashboard[field])}%`}`).join(' · '))
      ];
    },
    workout_rows() {
      if (!this.detail) return [];
      const {currentPeriod, previousComparablePeriod, yearAgoComparablePeriod} = this.detail.snapshot.progress;
      const current = currentPeriod.workouts;
      const previous = previousComparablePeriod.workouts;
      const yearAgo = yearAgoComparablePeriod.workouts;
      const minutes = seconds => Number((seconds / 60).toFixed(2));
      const partial = value => value.cardioDurationComplete === false ? ' · Partial coverage' : '';
      const format = (value, field) => {
        const count = value[field];
        if (count == null) return 'Not available';
        return field === 'activeCardioSeconds' ? `${minutes(count)} min${partial(value)}` : `${count} ${count === 1 ? 'session' : 'sessions'}`;
      };
      const comparison = (baseline, field, label) => {
        let change = 'Not available';
        if (current[field] != null && baseline[field] != null && !(field === 'activeCardioSeconds' && (current.cardioDurationComplete === false || baseline.cardioDurationComplete === false))) {
          const difference = current[field] - baseline[field];
          const number = field === 'activeCardioSeconds' ? minutes(difference) : difference;
          const unit = field === 'activeCardioSeconds' ? 'min' : Math.abs(difference) === 1 ? 'session' : 'sessions';
          change = `${number > 0 ? '+' : ''}${number} ${unit}`;
        }
        return `${format(baseline, field)} · ${change} vs ${label}${field === 'activeCardioSeconds' ? partial(current) : ''}`;
      };
      return [
        ['Strength sessions', 'strengthSessionCount'],
        ['Cardio sessions', 'cardioSessionCount'],
        ['Stretching sessions', 'stretchingSessionCount'],
        ['Active cardio minutes', 'activeCardioSeconds']
      ].map(([label, field]) => ({label, current: format(current, field), previous: comparison(previous, field, 'last week'), yearAgo: comparison(yearAgo, field, '52 weeks ago')}));
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
    outcome_status(value, type) { return weeklyClassification(value, type, this.detail.snapshot.emailGoals?.rules, this.detail.snapshot.emailGoals); },
    async open_measurements(tab) { this.measurement_tab = tab; this.review_tab = 'measurements'; await nextTick(); this.$refs.measurementTabs.$el.querySelectorAll('[role=tab]')[this.measurement_index].focus(); },
    async open_omega_references() { await this.open_measurements('nutrition'); await this.show_omega_references(); },
    async show_omega_references() { await nextTick(); this.$refs.omegaReferences[0].open = true; this.$refs.omegaReferences[0].querySelector('summary').focus(); this.$refs.omegaReferences[0].scrollIntoView({block: 'center'}); },
    async load() {
      this.loading = true;
      this.error = null;
      try {
        const legacySelectedDate = this.$route.query.date;
        if (legacySelectedDate && !this.$route.params.fridayDate) {
          await this.$router.replace({name: 'WeeklySummaryDetail', params: {fridayDate: legacySelectedDate}});
          return;
        }
        const selectedFridayDate = this.selected_friday_date;
        [this.archive, this.preview] = await Promise.all([
          weeklySummaryService.getArchive({selectedFridayDate: this.$route.params.fridayDate || selectedFridayDate}),
          weeklySummaryService.getPreview(selectedFridayDate)
        ]);
        if (this.is_detail_page) await this.load_detail(this.$route.params.fridayDate);
        else this.detail = null;
      } catch (error) {
        this.error = error.message || 'Weekly summaries could not be loaded.';
      } finally {
        this.loading = false;
      }
    },
    async load_detail(fridayDate) {
      if (this.detail?.fridayDate !== fridayDate) { this.review_tab = 'overview'; this.measurement_tab = 'body'; this.routine_search = ''; }
      this.detail = await weeklySummaryService.getSummary(fridayDate);
      if (this.is_detail_page) {
        await nextTick();
        if (window.matchMedia('(max-width: 760px)').matches) {
          const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
          this.$refs.detail?.scrollIntoView({behavior, block: 'start'});
          this.$refs.detail?.focus({preventScroll: true});
        }
      }
    },
    async select_summary(fridayDate) {
      await this.$router.push({name: 'WeeklySummaryDetail', params: {fridayDate}});
    },
    async load_archive_page(page) {
      this.loading = true;
      this.error = null;
      try {
        this.archive = await weeklySummaryService.getArchive({page});
      } catch (error) {
        this.error = error.message || 'The weekly summary archive could not be loaded.';
      } finally {
        this.loading = false;
      }
    },
    async create_or_open_latest() {
      if (this.preview.alreadySaved) {
        if (!this.archive.items.some(item => item.fridayDate === this.preview.fridayDate)) {
          this.archive = await weeklySummaryService.getArchive({selectedFridayDate: this.preview.fridayDate});
        }
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
        this.detail = await weeklySummaryService.createLatest(this.selected_friday_date);
        await this.reload_archive_preview();
        await this.$router.push({name: 'WeeklySummaryDetail', params: {fridayDate: this.detail.fridayDate}});
        this.showMissingOutcomeConfirm = false;
      } catch (error) {
        this.error = error.message || 'The weekly summary could not be saved.';
      } finally {
        this.creating = false;
      }
    },
    async reload_archive_preview() {
      const selectedFridayDate = this.selected_friday_date;
      [this.archive, this.preview] = await Promise.all([
        weeklySummaryService.getArchive({selectedFridayDate}),
        weeklySummaryService.getPreview(selectedFridayDate)
      ]);
    },
    async refresh_summary() {
      await this.load_detail(this.detail.fridayDate);
      this.actions_date = actionToday();
      this.actions_revision++;
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
    outcome_window(fridayDate) {
      return this.format_period(fridayDate, dayjs(`${fridayDate}T00:00:00`).add(2, 'day').format('YYYY-MM-DD'));
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
.review-content { padding: 0 1rem 1rem; }
.review-content :deep(.p-tabview-panels) { padding: 0; }
.review-content .section-heading { margin-bottom: .75rem; }
.review-content .detail-section, .review-content .weekly-reflection { padding: 1rem 0; }
.review-content h3 { margin: .8rem 0; font-size: 1.1rem; }
.routine-hero { padding: 1rem; margin-top: 1rem; border: 1px solid #cbdde9; border-radius: 4px; background: #f7fafc; }
.routine-hero-columns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 1.5rem; }
.routine-hero-columns h4 { margin-top: .6rem; }
.routine-score { display: flex; align-items: baseline; flex-wrap: wrap; gap: .75rem; margin: .6rem 0; }
.routine-score strong { font-size: 2.6rem; line-height: 1.1; }
.routine-score span, .routine-attention span { color: #63717b; font-size: .85rem; }
.routine-comparisons { display: flex; flex-wrap: wrap; gap: .5rem 1.5rem; }
.routine-comparisons small { color: #63717b; }
.routine-days { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: .5rem; margin: 1rem 0; text-align: center; font-size: .75rem; }
.routine-track { height: .4rem; background: #e1e7ec; margin: .4rem 0; overflow: hidden; border-radius: 2px; }
.routine-track > span { display: block; height: 100%; background: #628da9; }
.routine-attention-list { display: grid; gap: .45rem; }
.routine-attention { display: flex; justify-content: space-between; gap: .5rem; padding: .55rem 0; border-top: 1px solid #e0e8ed; }
.routine-attention strong { font-size: .88rem; }
.routine-attention > * { min-width: 0; overflow-wrap: anywhere; }
.review-status { font-weight: 600; margin-top: .5rem; font-size: .8rem; }
.section-link { margin-top: .8rem; }
.more-recommendations { margin-top: 1rem; }
.routine-search { display: grid; gap: .4rem; max-width: 25rem; }
.routine-item > strong { flex: 1 1 15rem; overflow-wrap: anywhere; }
.routine-item > .routine-track { flex: 1 1 5rem; }
.reflection-teaser p { line-height: 1.5; }
@media (max-width: 640px) { .routine-hero-columns { grid-template-columns: minmax(0, 1fr); gap: .5rem; } .review-content { padding: 0 .75rem .75rem; } .routine-hero { padding: .75rem; } .routine-hero .section-heading, .review-content .section-heading { align-items: flex-start; flex-wrap: wrap; } .routine-attention { flex-direction: column; gap: .25rem; } }

.weekly-highlights { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem; }
.weekly-highlight { min-width: 0; padding: .8rem; border: 1px solid #dedede; border-radius: 4px; overflow-wrap: anywhere; }
.weekly-highlight > span, .weekly-highlight > strong, .weekly-highlight > small { display: block; }
.weekly-highlight > span, .weekly-highlight > small { color: #63717b; font-size: .8rem; line-height: 1.4; }
.weekly-highlight > strong { margin: .5rem 0; font-size: clamp(1.15rem, 3vw, 1.6rem); }
.highlight-bar { height: .3rem; background: #edf1f5; margin-top: .75rem; }
.highlight-bar > span { display: block; height: 100%; background: #3b83bd; }
.highlight-bar--previous { margin: .25rem 0 .5rem; }
.highlight-bar--previous > span { background: #aab7c4; }
.weekly-recommendation { padding: .85rem 0; border-bottom: 1px solid #dedede; overflow-wrap: anywhere; }
.weekly-recommendation h4 { margin: 0; }
.weekly-recommendation p { color: #63717b; font-size: .85rem; }
.recommendation-link { display: inline-flex; text-decoration: none; }

.weekly-page {
  --ink: #21313c;
  --muted: #63717b;
  --paper: #f7f9fb;
  --card: #ffffff;
  --line: #dedede;
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
.detail-heading h2 { margin: 0; font-family: inherit; }
.weekly-header h1 { font-size: 1.6rem; line-height: 1.2; }
.weekly-header p,
.detail-heading p,
.comparison-caption,
.empty-note,
.preview-note { margin: 0.4rem 0 0; color: var(--muted); line-height: 1.5; }
.weekly-kicker { margin-bottom: 0.4rem; color: var(--green); font-size: 0.72rem; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.preview-card,
.archive-card,
.detail-card,
.empty-state { border: 1px solid var(--line); border-radius: 4px; background: var(--card); }
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
.weekly-layout { display: grid; grid-template-columns: minmax(0, 1fr); align-items: start; gap: 0.8rem; }
.weekly-layout--detail { max-width: 1100px; margin: 0 auto; }
.archive-card { padding: 0.85rem; }
.section-heading h2 { font-size: 1.2rem; }
.section-heading > span { color: var(--muted); font-size: 0.85rem; }
.archive-list { display: grid; gap: 0.35rem; margin-top: 0.65rem; }
.archive-count { color: var(--muted); font-size: 0.85rem; white-space: nowrap; }
.archive-item { display: grid; grid-template-columns: 1fr 1.1rem; gap: 0.2rem 0.5rem; width: 100%; padding: 0.65rem; border: 1px solid transparent; border-radius: 0.65rem; background: var(--paper); color: var(--ink); text-align: left; cursor: pointer; }
.archive-item:hover { border-color: #b8ccbc; background: #f5f8f4; }
.archive-period { font-weight: 700; }
.archive-status { color: var(--muted); font-size: 0.78rem; }
.archive-item > i { grid-column: 2; grid-row: 1 / 3; align-self: center; color: var(--green); }
.archive-empty { padding: 1rem 0.25rem; color: var(--muted); font-size: 0.9rem; text-align: center; }
.detail-card { min-width: 0; overflow: hidden; }
.detail-card:focus { outline: 3px solid var(--green); outline-offset: 3px; }
.detail-heading { align-items: flex-start; padding: 1rem; background: var(--paper); border-bottom: 1px solid var(--line); }
.detail-heading h2 { font-size: clamp(1.2rem, 3vw, 1.7rem); }
.detail-heading p { max-width: 720px; font-size: 0.88rem; }
.informational-badge { flex: 0 0 auto; padding: 0.3rem 0.55rem; border: 1px solid #b5c9cf; border-radius: 999px; color: #315f78; font-size: 0.76rem; }
.missing-data { margin: 0.8rem 1rem 0; padding: 0.75rem; border-radius: 0.65rem; background: #fff7e6; color: #5c471f; }
.missing-data ul { margin: 0.3rem 0 0; padding-left: 1.2rem; }
.detail-section,
.weekly-reflection { padding: 1rem; border-bottom: 1px solid var(--line); }
.detail-section-summary { display: flex; align-items: baseline; gap: 0.4rem 0.8rem; flex-wrap: wrap; cursor: pointer; list-style: none; }
.detail-section-summary::-webkit-details-marker { display: none; }
.detail-section-summary::after { content: '＋'; margin-left: auto; color: var(--green); font-weight: 700; }
details[open] > .detail-section-summary::after { content: '−'; }
.detail-section-summary:focus-visible { outline: 3px solid var(--green); outline-offset: 3px; border-radius: 0.2rem; }
.detail-section-summary .weekly-kicker { margin: 0; }
.detail-section-summary strong,
.detail-section-summary h3 { margin: 0; font-size: 1.12rem; }
.outcome-heading,
.weekly-reflection h3 { margin: 0; font-size: 1.12rem; }
.detail-section > .detail-section-summary + * { margin-top: 0.7rem; }
.back-pain-breakdown { display: grid; gap: 0.3rem; margin: 0.6rem 0 0; }
.back-pain-breakdown > div { display: grid; grid-template-columns: 6rem 1fr; gap: 0.4rem; }
.back-pain-breakdown dt { color: var(--muted); font-weight: 700; }
.back-pain-breakdown dd { margin: 0; }
.comparison-caption { margin-bottom: 0.7rem; font-size: 0.84rem; }
.metrics-group-heading { padding: 1rem 0.45rem 0.5rem; color: #35434b; }
.metric-current { font-weight: 700; }
.metrics-table { display: grid; width: 100%; overflow-x: auto; }
.metrics-row { display: grid; grid-template-columns: minmax(145px, 1.15fr) repeat(3, minmax(125px, 1fr)); min-width: 560px; border-bottom: 1px solid #e5dfd5; }
.metrics-row > * { padding: 0.55rem 0.45rem; line-height: 1.4; overflow-wrap: anywhere; }
.metrics-row > strong { color: #35434b; }
.metrics-row--heading { color: var(--muted); font-size: 0.78rem; font-weight: 700; }
.metrics-row--heading small { display: block; margin-top: 0.25rem; }
.routine-list { display: grid; gap: 0.4rem; margin-top: 0.6rem; }
.routine-item { display: flex; align-items: center; flex-wrap: wrap; gap: 0.4rem 0.7rem; padding: 0.55rem 0.65rem; border-radius: 0.55rem; background: var(--paper); }
.routine-item > span { color: var(--muted); font-size: 0.84rem; }
.routine-item--watch { border-left: 3px solid #b1682b; }
.watch-label { color: #855a18 !important; font-weight: 700; }
.record-list { display: grid; gap: 0.35rem; margin: 0.6rem 0 0; padding: 0; list-style: none; }
.record-list li { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 0.25rem 0.75rem; padding: 0.5rem 0; border-bottom: 1px solid #e5dfd5; }
.record-list span { color: var(--muted); }
.weekly-reflection { background: #fbfaf6; }
.weekly-reflection > .section-heading { align-items: baseline; }
.reflection-summary { margin: 0.8rem 0; line-height: 1.55; }
.reflection-details { margin-top: 0.8rem; }
.reflection-details .reflection-sections { margin-top: 0.7rem; }
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
.empty-state h2 { margin: 0.5rem 0; font-family: inherit; }
.empty-state p { color: var(--muted); }
@media (max-width: 760px) {
  .weekly-page { padding: 1rem 0 2rem; }
  .weekly-header { align-items: flex-start; flex-direction: column; }
  .weekly-layout { grid-template-columns: 1fr; }
  .archive-card { position: static; }
  .detail-card { scroll-margin-top: 5.5rem; }
  .detail-heading { flex-direction: column; }
  .reflection-sections { grid-template-columns: 1fr; }
  .metrics-row--heading { display: none; }
  .metrics-row { min-width: 0; grid-template-columns: minmax(0, 1fr); padding: 0.45rem 0; }
  .metrics-row > * { padding: 0.25rem 0; }
  .metrics-row > [role="cell"]::before { content: attr(data-period) ': '; color: var(--muted); font-size: 0.76rem; font-weight: 700; }
}
@media (max-width: 480px) {
  .outcome-grid, .weekend-readings { grid-template-columns: 1fr; }
  .preview-heading { align-items: flex-start; flex-direction: column; }
  .detail-section,
  .weekly-reflection { padding: 0.8rem; }
  .missing-data { margin-right: 0.8rem; margin-left: 0.8rem; }
  .saved-label { display: block; margin-top: 0.3rem; }
  .weekly-reflection > .section-heading { align-items: flex-start; flex-direction: column; gap: 0.25rem; }
  .reflection-actions { grid-template-columns: minmax(0, 1fr); }
}
</style>
