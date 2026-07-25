<template>
  <loading v-model:active="loading" :can-cancel="false" :is-full-page="true" />
  <main class="reflection-page">
    <section class="reflection-hero">
      <div>
        <div class="reflection-kicker">Personal review</div>
        <h1>Reflections</h1>
        <p>Turn your completed health records into a focused review of patterns, progress, and practical next steps.</p>
      </div>
      <div class="coverage-seal">
        <strong>90 days</strong>
        <span>30 detailed + 60 summarized</span>
      </div>
    </section>

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
            <div class="reflection-window">Uses detailed records from {{ detailed_start_label }} and weekly context from {{ context_start_label }}.</div>
          </div>
          <span class="informational-badge">Informational, not medical advice</span>
        </div>

        <div v-if="!reflection" class="generation-state">
          <div class="generation-mark"><i class="pi pi-comment"></i></div>
          <h2>No reflection for this day</h2>
          <p v-if="overview.actionConfigured">Open your Weight Control GPT and use the suggested prompt to create this reflection.</p>
          <p v-else>Configure the ChatGPT Action token on the backend before requesting a reflection.</p>
          <div class="suggested-prompt">
            <span>Suggested prompt</span>
            <code>{{ chatgpt_prompt }}</code>
          </div>
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

          <section class="reflection-tools">
            <div class="suggested-prompt">
              <span>Suggested prompt</span>
              <code>{{ chatgpt_prompt }}</code>
            </div>
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
      const date = this.date_key(this.selected_date);
      const request = this.reflection
        ? `Update and save the existing reflection for ${date} using the latest context.`
        : `Generate and save a reflection for ${date}.`;
      return `${request} Analyze symptom episodes against all recorded factors and rank up to three plausible contributors with evidence, counterexamples, and confidence.`;
    },
    chatgpt_button_label() {
      return this.reflection ? 'Update in ChatGPT' : 'Create in ChatGPT';
    }
  },
  async mounted() {
    await this.load_overview();
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
  padding: 1.5rem 1rem 4rem;
  color: var(--ink);
}
.reflection-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 2rem;
  padding: 2.5rem;
  border-radius: 1.25rem;
  background:
      radial-gradient(circle at 85% 10%, rgba(255, 255, 255, 0.65), transparent 28%),
      linear-gradient(135deg, #dfe9dd, #f4e7d2 55%, #d8e5e8);
}
.reflection-hero h1,
.history-heading h2 {
  margin: 0;
  font-family: Georgia, 'Times New Roman', serif;
}
.reflection-hero h1 {
  font-size: clamp(2.5rem, 7vw, 5rem);
  line-height: 0.95;
}
.reflection-hero p {
  max-width: 640px;
  margin: 1rem 0 0;
  font-size: 1.05rem;
  line-height: 1.6;
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
.coverage-seal {
  flex: 0 0 190px;
  display: grid;
  place-items: center;
  min-height: 150px;
  padding: 1rem;
  border: 1px solid rgba(33, 49, 60, 0.25);
  border-radius: 50%;
  text-align: center;
  transform: rotate(3deg);
}
.coverage-seal strong {
  display: block;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 2rem;
}
.coverage-seal span {
  font-size: 0.8rem;
}
.date-console {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: end;
  gap: 1rem;
  margin: 1.5rem 0;
  padding: 1rem;
  border: 1px solid var(--line);
  border-radius: 0.9rem;
  background: var(--card);
}
.date-console > :last-child {
  justify-self: flex-end;
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
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--line);
  background: var(--paper);
}
.reflection-date {
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 1.35rem;
  font-weight: 700;
}
.reflection-window {
  margin-top: 0.25rem;
  color: var(--muted);
  font-size: 0.85rem;
}
.informational-badge {
  padding: 0.45rem 0.75rem;
  border: 1px solid #b5c9cf;
  border-radius: 999px;
  color: var(--blue);
  font-size: 0.78rem;
  white-space: nowrap;
}
.generation-state,
.empty-state {
  padding: 4rem 1.5rem;
  text-align: center;
}
.generation-state p,
.empty-state p {
  max-width: 520px;
  margin: 0.75rem auto 1.5rem;
  color: var(--muted);
  line-height: 1.6;
}
.generation-actions {
  display: flex;
  justify-content: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}
.suggested-prompt {
  display: grid;
  gap: 0.4rem;
  max-width: 720px;
  margin: 0 auto 1.5rem;
  padding: 0.9rem 1rem;
  border: 1px solid var(--line);
  border-radius: 0.65rem;
  background: var(--paper);
  text-align: left;
}
.suggested-prompt span {
  color: var(--green);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.suggested-prompt code {
  overflow-wrap: anywhere;
}
.generation-mark,
.empty-state > i {
  display: inline-grid;
  place-items: center;
  width: 3.5rem;
  height: 3.5rem;
  border-radius: 50%;
  background: #e4eee8;
  color: var(--green);
  font-size: 1.35rem;
}
.reflection-result {
  padding: 2rem;
}
.reflection-result header {
  max-width: 800px;
}
.reflection-result h2 {
  margin: 0;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: clamp(1.8rem, 4vw, 3rem);
}
.reflection-result header p {
  color: #43525c;
  font-size: 1.05rem;
  line-height: 1.75;
}
.insight-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin-top: 2rem;
}
.insight-card {
  padding: 1.25rem;
  border-top: 4px solid;
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
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  background: var(--card);
}
.insight-card h3 {
  margin: 0.75rem 0;
}
.insight-card ul {
  margin: 0;
  padding-left: 1.15rem;
}
.insight-card li {
  margin: 0.55rem 0;
  line-height: 1.5;
}
.reflection-tools {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 1rem;
  margin-top: 2rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--line);
}
.reflection-tools .suggested-prompt {
  max-width: none;
  margin: 0;
}
.reflection-tools .generation-actions {
  justify-content: flex-end;
}
.reflection-result footer {
  margin-top: 2rem;
  color: var(--muted);
  font-size: 0.8rem;
}
.history-section {
  margin-top: 1.5rem;
  padding: 1.5rem;
}
.history-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 1rem;
}
.history-heading h2 {
  font-size: 2rem;
}
.history-heading > span {
  color: var(--muted);
  font-size: 0.85rem;
}
.history-list {
  display: grid;
  gap: 0.5rem;
}
.history-item {
  display: grid;
  grid-template-columns: 120px 1fr auto 1rem;
  align-items: center;
  gap: 1rem;
  width: 100%;
  padding: 1rem;
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
  padding: 2rem;
  text-align: center;
}
code {
  padding: 0.1rem 0.3rem;
  border-radius: 0.25rem;
  background: #ebe6dd;
}
@media (max-width: 760px) {
  .reflection-hero {
    align-items: flex-start;
    padding: 1.5rem;
  }
  .coverage-seal {
    flex-basis: 110px;
    min-height: 95px;
  }
  .coverage-seal strong {
    font-size: 1.35rem;
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
  .reflection-tools {
    grid-template-columns: 1fr;
  }
  .reflection-tools .generation-actions {
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
  .reflection-hero {
    display: block;
  }
  .coverage-seal {
    width: 110px;
    margin: 1.5rem 0 0 auto;
  }
  .reflection-result {
    padding: 1.25rem;
  }
}
</style>
