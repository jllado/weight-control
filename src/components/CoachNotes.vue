<template>
  <main class="coach-notes page-content">
    <h1>Coach Notes</h1>
    <p class="privacy">These notes are shared with your private Weight Control Coach only when it requests the Coach Notes context.</p>
    <form class="note-form" @submit.prevent="save">
      <label for="coach-note-date">Date</label>
      <input id="coach-note-date" v-model="form.date" type="date" required :disabled="saving">
      <label for="coach-note-content">Note</label>
      <Textarea id="coach-note-content" v-model="form.content" rows="5" maxlength="4000" required :disabled="saving" />
      <small>{{ form.content.length }}/4000 characters</small>
      <div class="action-group"><Button type="submit" :label="saving ? 'Saving…' : editing ? 'Save note' : 'Add note'" :loading="saving" :disabled="saving" /><Button v-if="editing" type="button" label="Cancel" class="p-button-secondary" :disabled="saving" @click="reset" /></div>
    </form>
    <p v-if="error" class="p-error" role="alert">{{ error }}</p>
    <div v-if="loading">Loading notes…</div>
    <article v-for="note in notes" :key="note.id" class="saved-note">
      <header><strong>{{ formatDate(note.date) }}</strong><span class="action-group action-group--compact"><CompactAction icon="pi pi-pencil" :aria-label="`Edit note from ${formatDate(note.date)}`" @click="edit(note)" /><CompactAction icon="pi pi-trash" :aria-label="`Delete note from ${formatDate(note.date)}`" :action="() => remove(note)" destructive /></span></header>
      <p>{{ note.content }}</p>
    </article>
    <p v-if="!loading && !notes.length">No Coach Notes yet.</p>
  </main>
</template>
<script>
import dayjs from 'dayjs';
import Textarea from 'primevue/textarea';
import service from '../services/CoachNoteService';
export default {
  components: {Textarea},
  data() { return {notes: [], loading: false, saving: false, error: '', editing: null, form: {date: dayjs().format('YYYY-MM-DD'), content: ''}}; },
  created() { this.load(); },
  methods: {
    async load() { this.loading = true; this.error = ''; try { this.notes = await service.list(); } catch (error) { this.error = error.message; } finally { this.loading = false; } },
    async save() { this.saving = true; this.error = ''; try { const note = this.editing ? await service.update(this.editing.id, this.form) : await service.create(this.form); this.notes = this.editing ? this.notes.map(entry => entry.id === note.id ? note : entry) : [note, ...this.notes]; this.notes.sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id); this.$toast.add({severity: 'success', summary: 'Coach note saved', life: 3000}); this.reset(); } catch (error) { this.error = error.message; } finally { this.saving = false; } },
    edit(note) { this.editing = note; this.form = {date: note.date, content: note.content}; },
    async remove(note) { if (!window.confirm(`Delete the note from ${this.formatDate(note.date)}?`)) return; this.error = ''; try { await service.delete(note.id); this.notes = this.notes.filter(entry => entry.id !== note.id); if (this.editing?.id === note.id) this.reset(); this.$toast.add({severity: 'success', summary: 'Coach note deleted', life: 3000}); } catch (error) { this.error = error.message; } },
    reset() { this.editing = null; this.form = {date: dayjs().format('YYYY-MM-DD'), content: ''}; },
    formatDate(date) { return dayjs(date).format('DD/MM/YYYY'); }
  }
};
</script>
<style scoped>
.coach-notes { max-width: 46rem; margin: 1.5rem auto; padding: 0 1rem; }.privacy { background: #eef6ff; border-radius: .5rem; padding: .8rem; }.note-form { display: flex; flex-direction: column; gap: .5rem; }.note-form textarea { width: 100%; resize: vertical; }.saved-note { border-top: 1px solid var(--surface-border); margin-top: 1rem; padding-top: 1rem; }.saved-note header { display: flex; justify-content: space-between; align-items: center; }.saved-note p { white-space: pre-wrap; overflow-wrap: anywhere; }.actions { display: flex; }
</style>
