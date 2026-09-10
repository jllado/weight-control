<template>
  <Button icon="pi pi-flag" class="p-button-outlined" aria-label="Pause or record" title="Pause or record" aria-haspopup="dialog" @click="openControls" />
  <Dialog appendTo="body" header="Pause or record" v-model:visible="controlsVisible" :modal="true" :style="{width: 'min(32rem, calc(100vw - 2rem))'}">
    <div class="pause-controls-layout">
      <div class="urge-pause-actions pause-controls-primary">
        <template v-if="pause">
          <span v-if="!ready" class="urge-pause-countdown" aria-label="Pause time remaining">{{ countdown }}</span>
          <Button v-if="ready" label="Check in" :disabled="busy" @click="openCheckIn" />
          <Button v-else label="Cancel pause" class="p-button-text p-button-secondary" :disabled="busy" @click="cancel" />
        </template>
        <Button v-else label="Wait 15 minutes" icon="pi pi-clock" class="p-button-outlined" :disabled="busy || !loaded" @click="controlsVisible = false; startVisible = true" />
      </div>
      <DecisionOutcomeActions :disabled="busy" @select="recordIndependent" />
    </div>
    <div v-if="error" class="urge-pause-error" role="alert">
      <span class="p-error">{{ error }}</span>
      <Button label="Refresh" class="p-button-text" :disabled="busy" @click="refresh" />
    </div>
  </Dialog>
  <Dialog appendTo="body" header="Wait 15 minutes" v-model:visible="startVisible" :modal="true" :closable="!busy" :closeOnEscape="!busy" :style="{width: 'min(32rem, calc(100vw - 2rem))'}">
    <div class="urge-pause-field">
      <label for="urge-description">What are you craving or tempted to do? (optional)</label>
      <Textarea id="urge-description" v-model="description" maxlength="500" rows="3" :disabled="busy" aria-describedby="urge-description-help" />
      <small id="urge-description-help">The Coach can read this to help you reflect on your pauses.</small>
      <small v-if="error" class="p-error" role="alert">{{ error }}</small>
    </div>
    <template #footer>
      <Button label="Start" icon="pi pi-clock" :loading="busy" :disabled="busy" @click="start" />
      <Button label="Cancel" class="p-button-secondary" :disabled="busy" @click="startVisible = false" />
    </template>
  </Dialog>
  <Dialog appendTo="body" header="15 minutes are up" v-model:visible="checkInVisible" :modal="true" :closable="!busy" :closeOnEscape="!busy" :style="{width: 'min(32rem, calc(100vw - 2rem))'}">
    <template v-if="pause">
      <p class="urge-pause-description">{{ pause.description }}</p>
      <p>Do you still want to do it?</p>
      <div class="urge-pause-actions">
        <Button label="Not anymore" :class="pause.answer === 'NOT_ANYMORE' ? '' : 'p-button-outlined'" :aria-pressed="pause.answer === 'NOT_ANYMORE'" :disabled="busy" @click="answer('NOT_ANYMORE')" />
        <Button label="Still want to" :class="pause.answer === 'STILL_WANT' ? '' : 'p-button-outlined'" :aria-pressed="pause.answer === 'STILL_WANT'" :disabled="busy" @click="answer('STILL_WANT')" />
      </div>
      <template v-if="pause.answer">
        <p>You took time to pause. You can decide what to do next.</p>
        <DecisionOutcomeActions :disabled="busy" @select="record" />
      </template>
      <small v-if="error" class="p-error" role="alert">{{ error }}</small>
    </template>
    <template #footer>
      <div class="urge-pause-actions">
        <Button label="Wait another 15 minutes" icon="pi pi-clock" :disabled="busy" @click="repeat" />
        <Button label="Finish without logging" class="p-button-text" :disabled="busy" @click="finish" />
      </div>
    </template>
  </Dialog>
  <DecisionOutcomeForm v-if="independentEntry" :entry="independentEntry" @onClose="independentEntry = null" @onSave="decisionSaved" />
  <DecisionOutcomeForm v-if="decisionEntry" :entry="decisionEntry" :saveEntry="saveDecision" @onClose="closeDecision" @onSave="decisionSaved" />
</template>

<script>
import Textarea from 'primevue/textarea';
import DecisionOutcomeActions from './DecisionOutcomeActions.vue';
import DecisionOutcomeForm from './DecisionOutcomeForm.vue';
import service, {pauseUi} from '../services/UrgePauseService';
import {notificationsChanged} from '../services/InAppNotificationService';

export default {
  components: {Textarea, DecisionOutcomeActions, DecisionOutcomeForm},
  data() {
    return {pauseUi, controlsVisible: false, independentEntry: null, pause: null, loaded: false, busy: false, error: '', description: '', startVisible: false, checkInVisible: false,
      decisionEntry: null, now: Date.now(), serverOffset: 0, timer: null, refreshTimer: null, expiryChecked: null};
  },
  computed: {
    remaining() { return this.pause ? Math.max(0, Math.ceil((Date.parse(this.pause.endsAt) - this.now) / 1000)) : 0; },
    ready() { return this.pause !== null && this.remaining === 0; },
    countdown() { return `${String(Math.floor(this.remaining / 60)).padStart(2, '0')}:${String(this.remaining % 60).padStart(2, '0')}`; }
  },
  watch: {
    'pauseUi.openRequest'() { if (this.ready) this.openCheckIn(); else this.openControls(); },
    countdown() { this.publishSummary(); },
    '$route.fullPath'() { this.refresh(); }
  },
  mounted() {
    this.refresh();
    this.timer = setInterval(() => {
      this.now = Date.now() + this.serverOffset;
      if (this.ready && this.expiryChecked !== this.pause.id && !this.busy) {
        this.expiryChecked = this.pause.id;
        this.refresh(true);
      }
    }, 1000);
    this.refreshTimer = setInterval(() => { if (document.visibilityState === 'visible') this.refresh(); }, 30000);
    document.addEventListener('visibilitychange', this.foreground);
    window.addEventListener('focus', this.foreground);
  },
  beforeUnmount() {
    pauseUi.summary = null;
    clearInterval(this.timer);
    clearInterval(this.refreshTimer);
    document.removeEventListener('visibilitychange', this.foreground);
    window.removeEventListener('focus', this.foreground);
  },
  methods: {
    publishSummary() { pauseUi.summary = this.pause ? {ready: this.ready, countdown: this.countdown} : null; },
    openControls() { this.controlsVisible = true; },
    openCheckIn() { this.controlsVisible = false; this.checkInVisible = true; },
    today() { return new Intl.DateTimeFormat('en-CA', {timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit'}).format(new Date(this.now)); },
    recordIndependent(outcome) {
      this.controlsVisible = false;
      this.independentEntry = {date: this.today(), outcome, reason: null};
    },
    decisionSaved() { pauseUi.decisionRevision++; },
    apply(response) {
      this.pause = response.pause;
      this.serverOffset = Date.parse(response.serverNow) - Date.now();
      this.now = Date.now() + this.serverOffset;
      this.loaded = true;
      this.publishSummary();
      if (!this.pause || !this.ready) this.checkInVisible = false;
    },
    async refresh(openWhenReady = false) {
      if (this.busy || this.decisionEntry || this.independentEntry) return;
      this.busy = true;
      let openCheckIn = false;
      try {
        this.apply(await service.current());
        this.error = '';
        if (this.ready) this.expiryChecked = this.pause.id;
        if (openWhenReady && this.ready) openCheckIn = true;
        const target = this.$route.query.urgePauseId;
        if (target && this.$route.path !== '/login') {
          if (this.pause && String(this.pause.id) === target && this.ready) openCheckIn = true;
          else this.$toast.add({severity: 'info', summary: 'This pause is no longer awaiting a check-in.', life: 4000});
          const query = {...this.$route.query};
          delete query.urgePauseId;
          await this.$router.replace({path: this.$route.path, query});
        }
        notificationsChanged();
      } catch (error) { this.error = error.message; }
      finally { this.busy = false; }
      if (openCheckIn) this.openCheckIn();
    },
    foreground() { if (document.visibilityState === 'visible') this.refresh(true); },
    async run(action) {
      this.busy = true;
      this.error = '';
      try { await action(); }
      catch (error) { this.error = error.message; }
      finally { this.busy = false; }
    },
    async start() {
      await this.run(async () => {
        this.apply(await service.start(this.description.trim() || null));
        this.startVisible = false;
        this.description = '';
      });
    },
    async cancel() { await this.run(async () => this.apply(await service.action(this.pause.id, 'cancel'))); },
    async answer(answer) { await this.run(async () => this.apply(await service.action(this.pause.id, 'check-in', {answer}))); },
    async repeat() { await this.run(async () => this.apply(await service.action(this.pause.id, 'repeat'))); },
    async finish() {
      await this.run(async () => {
        await service.finish(this.pause.id, null, null);
        this.apply(await service.current());
        this.checkInVisible = false;
      });
    },
    record(outcome) {
      const date = this.today();
      this.decisionEntry = {date, outcome, reason: this.pause.description, pauseId: this.pause.id};
      this.checkInVisible = false;
    },
    async saveDecision(reason) {
      await service.finish(this.decisionEntry.pauseId, this.decisionEntry.outcome, reason);
      this.pause = null;
      this.publishSummary();
      this.checkInVisible = false;
    },
    closeDecision() {
      this.decisionEntry = null;
      if (this.pause) this.checkInVisible = true;
      else this.refresh();
    }
  }
};
</script>

<style scoped>
.pause-controls-layout { display: flex; flex-direction: column; gap: 1rem; }
.pause-controls-primary { justify-content: center; }
.urge-pause-description { overflow-wrap: anywhere; white-space: pre-wrap; }
.urge-pause-actions { display: flex; align-items: center; flex-wrap: wrap; gap: .5rem; }
.urge-pause-countdown { font-size: 1.25rem; font-variant-numeric: tabular-nums; }
.urge-pause-field { display: flex; flex-direction: column; gap: .5rem; }
.urge-pause-field textarea { width: 100%; resize: vertical; }
.urge-pause-error { flex-basis: 100%; overflow-wrap: anywhere; }
.p-error { display: block; overflow-wrap: anywhere; }
@media (max-width: 575px) {
  .urge-pause-actions { width: 100%; }
}
</style>
