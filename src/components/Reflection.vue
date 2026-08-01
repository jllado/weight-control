<template>
  <loading v-model:active="loading" :can-cancel="false" :is-full-page="true" />
  <main class="reflection-page">
    <header class="reflection-header">
      <div>
        <div class="reflection-kicker">Personal review</div>
        <h1>Reflections</h1>
        <p>A concise review of your recent health records.</p>
      </div>
      <span class="coverage-note">90 days + year-ago week</span>
    </header>

    <section v-if="overview && !overview.lastCompletedDate" class="empty-state">
      <i class="pi pi-calendar-times"></i>
      <h2>No completed days yet</h2>
      <p>Mark a day as completed on Home before generating a reflection.</p>
      <Button label="Go to Home" icon="pi pi-home" @click="$router.push('/')" />
    </section>

    <template v-if="overview && overview.lastCompletedDate">
      <section class="date-console">
        <Button icon="pi pi-arrow-left" label="Previous Day" class="p-button-outlined" :disabled="!can_previous" @click="previous_day" />
        <div class="date-selector">
          <label for="reflection-date">Reflection date</label>
          <Calendar id="reflection-date"
                    :modelValue="selected_date"
                    @update:modelValue="select_date"
                    dateFormat="dd/mm/yy"
                    :minDate="first_tracked_date"
                    :maxDate="last_completed_date"
                    :manualInput="false"
                    appendTo="body" />
        </div>
        <Button icon="pi pi-arrow-right" iconPos="right" label="Next Day" class="p-button-outlined" :disabled="!can_next" @click="next_day" />
      </section>

      <section class="reflection-canvas">
        <div class="reflection-heading">
          <div>
            <div class="reflection-date">{{ format_date(selected_date) }}</div>
            <div class="reflection-window">Uses detailed records from {{ detailed_start_label }}, weekly context from {{ context_start_label }}, and the matching week 52 weeks earlier.</div>
          </div>
          <span class="informational-badge">Informational, not medical advice</span>
        </div>

        <div v-if="!reflection" class="generation-state">
          <div class="generation-mark"><i class="pi pi-comment"></i></div>
          <h2>No reflection for this day</h2>
          <p v-if="overview.actionConfigured">Open your Weight Control GPT; the prompt will be copied automatically.</p>
          <p v-else>Configure the ChatGPT Action token on the backend before requesting a reflection.</p>
          <div class="generation-actions">
            <Button :label="chatgpt_button_label"
                    icon="pi pi-external-link"
                    :disabled="!overview.actionConfigured"
                    @click="open_chatgpt" />
            <Button label="Refresh reflection"
                    icon="pi pi-refresh"
                    class="p-button-outlined"
                    @click="refresh_reflection" />
          </div>
        </div>

        <article v-else class="reflection-result">
          <header>
            <div class="result-label">Reflection</div>
            <h2>{{ reflection.title }}</h2>
            <p>{{ reflection.summary }}</p>
          </header>

          <div class="insight-grid">
            <section class="insight-card positive">
              <div class="insight-icon"><i class="pi pi-arrow-up"></i></div>
              <h3>Positive signals</h3>
              <ul>
                <li v-for="item in reflection.positiveSignals" :key="`positive-${item}`">{{ item }}</li>
              </ul>
            </section>
            <section class="insight-card watchout">
              <div class="insight-icon"><i class="pi pi-eye"></i></div>
              <h3>Watchouts</h3>
              <ul>
                <li v-for="item in reflection.watchouts" :key="`watchout-${item}`">{{ item }}</li>
              </ul>
            </section>
            <section class="insight-card action">
              <div class="insight-icon"><i class="pi pi-check"></i></div>
              <h3>Next actions</h3>
              <ul>
                <li v-for="item in reflection.nextActions" :key="`action-${item}`">{{ item }}</li>
              </ul>
            </section>
          </div>

          <section class="result-actions">
            <div class="generation-actions">
              <Button :label="chatgpt_button_label"
                      icon="pi pi-external-link"
                      :disabled="!overview.actionConfigured"
                      @click="open_chatgpt" />
              <Button label="Refresh reflection"
                      icon="pi pi-refresh"
                      class="p-button-outlined"
                      @click="refresh_reflection" />
            </div>
          </section>

          <footer>
            Generated {{ format_timestamp(reflection.generatedAt) }} with {{ reflection.model }}
          </footer>
        </article>
      </section>

      <section class="history-section">
        <div class="history-heading">
          <div>
            <div class="reflection-kicker">Archive</div>
            <h2>Reflection history</h2>
          </div>
          <span>{{ overview.reflections.length }} saved</span>
        </div>
        <div v-if="overview.reflections.length" class="history-list">
          <button v-for="item in overview.reflections"
                  :key="item.reflectionDate"
                  type="button"
                  class="history-item"
                  :class="{selected: is_selected(item.reflectionDate)}"
                  @click="select_history(item)">
            <span class="history-date">{{ format_date_string(item.reflectionDate) }}</span>
            <strong>{{ item.title }}</strong>
            <span class="history-generated">{{ format_timestamp(item.generatedAt) }}</span>
            <i class="pi pi-arrow-right"></i>
          </button>
        </div>
        <div v-else class="history-empty">Generated reflections will appear here.</div>
      </section>
    </template>
  </main>
</template>

<script>
import dayjs from 'dayjs';
import reflectionService from '@/services/ReflectionService';
import {buildReflectionPrompt} from '@/model/Reflection';

export default {
  name: 'Reflection',
  data() {
    return {
      overview: null,
      selected_date: null,
      reflection: null,
      loading: true,
      chatgpt_url: process.env.VUE_APP_CHATGPT_REFLECTION_URL || 'https://chatgpt.com/gpts/mine'
    };
  },
  computed: {
    first_tracked_date() {
      return this.to_date(this.overview.firstTrackedDate);
    },
    last_completed_date() {
      return this.to_date(this.overview.lastCompletedDate);
    },
    can_previous() {
      return dayjs(this.selected_date).isAfter(this.first_tracked_date, 'day');
    },
    can_next() {
      return dayjs(this.selected_date).isBefore(this.last_completed_date, 'day');
    },
    detailed_start_label() {
      return dayjs(this.selected_date).subtract(29, 'day').format('DD/MM/YYYY');
    },
    context_start_label() {
      return dayjs(this.selected_date).subtract(89, 'day').format('DD/MM/YYYY');
    },
    chatgpt_prompt() {
      return buildReflectionPrompt(this.date_key(this.selected_date));
    },
    chatgpt_button_label() {
      return this.reflection ? 'Update in ChatGPT' : 'Create in ChatGPT';
    }
  },
  async mounted() {
    await this.load_overview(this.$route.query.date);
    this.loading = false;
  },
  methods: {
    async load_overview(selectedDate) {
      try {
        this.overview = await reflectionService.getOverview();
        if (!this.overview.lastCompletedDate) {
          return;
        }
        this.selected_date = selectedDate ? this.to_date(selectedDate) : this.last_completed_date;
        await this.load_reflection();
      } catch (error) {
        this.handle_error(error);
      }
    },
    async load_reflection() {
      this.reflection = await reflectionService.get(this.date_key(this.selected_date));
    },
    async select_date(date) {
      this.selected_date = date;
      this.$router.replace({name: 'Reflection', query: {date: this.date_key(date)}});
      this.loading = true;
      try {
        await this.load_reflection();
      } catch (error) {
        this.handle_error(error);
      } finally {
        this.loading = false;
      }
    },
    previous_day() {
      this.select_date(dayjs(this.selected_date).subtract(1, 'day').toDate());
    },
    next_day() {
      this.select_date(dayjs(this.selected_date).add(1, 'day').toDate());
    },
    async refresh_reflection() {
      this.loading = true;
      try {
        await this.load_overview(this.date_key(this.selected_date));
      } catch (error) {
        this.handle_error(error);
      } finally {
        this.loading = false;
      }
    },
    open_chatgpt() {
      const copyPrompt = navigator.clipboard.writeText(this.chatgpt_prompt);
      window.open(this.chatgpt_url, '_blank', 'noopener,noreferrer');
      copyPrompt
        .then(() => this.$toast.add({
          severity: 'info',
          summary: 'Prompt copied',
          detail: 'Paste it into ChatGPT to continue.',
          life: 5000
        }))
        .catch(error => this.handle_error(error));
    },
    select_history(item) {
      this.select_date(this.to_date(item.reflectionDate));
    },
    is_selected(date) {
      return this.date_key(this.selected_date) === date;
    },
    date_key(date) {
      return dayjs(date).format('YYYY-MM-DD');
    },
    to_date(value) {
      return dayjs(`${value}T00:00:00`).toDate();
    },
    format_date(date) {
      return dayjs(date).format('dddd, D MMMM YYYY');
    },
    format_date_string(date) {
      return dayjs(`${date}T00:00:00`).format('DD MMM YYYY');
    },
    format_timestamp(value) {
      return dayjs(value).format('DD/MM/YYYY HH:mm');
    },
    handle_error(error) {
      this.$log.error(error);
      this.$toast.add({severity: 'error', summary: 'Failed', detail: error.message || error, life: 5000});
    }
  }
};
</script>

<style scoped>
.reflection-page {
  --ink: #21313c;
  --muted: #63717b;
  --paper: #f4f0e8;
  --card: #fffdf8;
  --line: #d8d1c5;
  --green: #39745a;
  --amber: #b1682b;
  --blue: #315f78;
  max-width: 1180px;
  margin: 0 auto;
  padding: 1rem 1rem 2rem;
  color: var(--ink);
}
.reflection-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}
.reflection-header h1,
.history-heading h2 {
  margin: 0;
  font-family: Georgia, 'Times New Roman', serif;
}
.reflection-header h1 {
  font-size: clamp(2rem, 5vw, 3rem);
  line-height: 1;
}
.reflection-header p {
  margin: 0.35rem 0 0;
  color: var(--muted);
}
.reflection-kicker,
.result-label {
  margin-bottom: 0.45rem;
  color: var(--green);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
.coverage-note {
  padding: 0.4rem 0.65rem;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--muted);
  font-size: 0.8rem;
  white-space: nowrap;
}
.date-console {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: end;
  gap: 0.75rem;
  margin: 0.75rem 0;
  padding: 0.65rem;
  border: 1px solid var(--line);
  border-radius: 0.9rem;
  background: var(--card);
}
.date-console > :last-child {
  justify-self: flex-end;
}
@media (min-width: 761px) {
  .date-console > .p-button {
    width: 10rem;
    white-space: nowrap;
  }
  .date-console > :first-child {
    justify-self: flex-start;
  }
}
.date-selector {
  display: grid;
  gap: 0.35rem;
  text-align: center;
}
.date-selector label {
  color: var(--muted);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.reflection-canvas,
.history-section,
.empty-state {
  border: 1px solid var(--line);
  border-radius: 1.25rem;
  background: var(--card);
  box-shadow: 0 1rem 3rem rgba(41, 48, 52, 0.07);
}
.reflection-canvas {
  overflow: hidden;
}
.reflection-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.8rem 1rem;
  border-bottom: 1px solid var(--line);
  background: var(--paper);
}
.reflection-date {
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 1.15rem;
  font-weight: 700;
}
.reflection-window {
  margin-top: 0.25rem;
  color: var(--muted);
  font-size: 0.85rem;
}
.informational-badge {
  padding: 0.35rem 0.6rem;
  border: 1px solid #b5c9cf;
  border-radius: 999px;
  color: var(--blue);
  font-size: 0.78rem;
  white-space: nowrap;
}
.generation-state,
.empty-state {
  padding: 2rem 1rem;
  text-align: center;
}
.generation-state p,
.empty-state p {
  max-width: 520px;
  margin: 0.5rem auto 1rem;
  color: var(--muted);
  line-height: 1.6;
}
.generation-actions {
  display: flex;
  justify-content: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}
.generation-mark,
.empty-state > i {
  display: inline-grid;
  place-items: center;
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 50%;
  background: #e4eee8;
  color: var(--green);
  font-size: 1.35rem;
}
.reflection-result {
  padding: 1rem;
}
.reflection-result header {
  max-width: 800px;
}
.reflection-result h2 {
  margin: 0;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: clamp(1.4rem, 3vw, 2rem);
}
.reflection-result header p {
  margin: 0.5rem 0 0;
  color: #43525c;
  line-height: 1.5;
}
.insight-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.75rem;
  margin-top: 1rem;
}
.insight-card {
  padding: 0.85rem;
  border-top: 3px solid;
  border-radius: 0.65rem;
  background: var(--paper);
}
.insight-card.positive {
  border-color: var(--green);
}
.insight-card.watchout {
  border-color: var(--amber);
}
.insight-card.action {
  border-color: var(--blue);
}
.insight-icon {
  display: inline-grid;
  place-items: center;
  width: 1.75rem;
  height: 1.75rem;
  border-radius: 50%;
  background: var(--card);
}
.insight-card h3 {
  margin: 0.5rem 0;
  font-size: 1rem;
}
.insight-card ul {
  margin: 0;
  padding-left: 1.15rem;
}
.insight-card li {
  margin: 0;
  line-height: 1.4;
}
.result-actions {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--line);
}
.result-actions .generation-actions {
  justify-content: flex-end;
}
.reflection-result footer {
  margin-top: 0.75rem;
  color: var(--muted);
  font-size: 0.8rem;
}
.history-section {
  margin-top: 0.75rem;
  padding: 1rem;
}
.history-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 0.65rem;
}
.history-heading h2 {
  font-size: 1.35rem;
}
.history-heading > span {
  color: var(--muted);
  font-size: 0.85rem;
}
.history-list {
  display: grid;
  gap: 0.35rem;
}
.history-item {
  display: grid;
  grid-template-columns: 120px 1fr auto 1rem;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  padding: 0.65rem 0.75rem;
  border: 1px solid transparent;
  border-radius: 0.65rem;
  background: var(--paper);
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}
.history-item:hover,
.history-item.selected {
  border-color: var(--green);
  background: #edf3ed;
}
.history-date {
  color: var(--green);
  font-weight: 700;
}
.history-generated,
.history-empty {
  color: var(--muted);
  font-size: 0.8rem;
}
.history-empty {
  padding: 1rem;
  text-align: center;
}
@media (max-width: 760px) {
  .reflection-header {
    align-items: flex-start;
  }
  .date-console {
    grid-template-columns: 1fr 1fr;
  }
  .date-selector {
    grid-column: 1 / -1;
    grid-row: 1;
  }
  .date-console > :last-child {
    justify-self: stretch;
  }
  .date-console > .p-button {
    justify-content: center;
  }
  .reflection-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .insight-grid {
    grid-template-columns: 1fr;
  }
  .result-actions .generation-actions {
    justify-content: flex-start;
  }
  .history-item {
    grid-template-columns: 1fr 1rem;
  }
  .history-date,
  .history-generated {
    grid-column: 1;
  }
  .history-item > i {
    grid-column: 2;
    grid-row: 1 / 4;
  }
}
@media (max-width: 480px) {
  .reflection-header {
    display: block;
  }
  .coverage-note {
    display: inline-block;
    margin-top: 0.65rem;
  }
  .reflection-result {
    padding: 1.25rem;
  }
}
</style>
