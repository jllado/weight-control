<template>
            <Panel v-if="routines.length > 0" class="p-panel-content-without-padding" >
              <template #header><div class="table-header"><strong>Routines ({{routines.length}})</strong></div></template>
              <DataTable :value="routines" responsiveLayout="scroll" scrollHeight="300px"
                         paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                         currentPageReportTemplate="{first} to {last} of {totalRecords}" >
                <Column headerStyle="width: 55px" bodyStyle="padding-left: 2px; padding-right: 2px; text-align: center" >
                  <template #body="routine">
                    <div class="routine-checkin-action">
                      <CompactAction v-if="routine.data.isDone(date)" icon="pi pi-undo" class="p-button-warning" @click="$emit('undo', routine.data)" :disabled="pendingIds.includes(routine.data.id)" :loading="pendingIds.includes(routine.data.id)" :aria-label="`Undo ${routine.data.name}`" />
                      <CompactAction v-else icon="pi pi-plus" class="p-button-success" @click="$emit('complete', routine.data)" :disabled="(pendingIds.includes(routine.data.id)) || routine.data.isDisabled(date)" :loading="pendingIds.includes(routine.data.id)" :aria-label="`Complete ${routine.data.name}`" />
                    </div>
                  </template>
                </Column>
                <Column>
                  <template #body="routine" >
                    <div class="routine-name-cell">
                      <span>{{ routine.data.name }}</span>
                      <span class="routine-best-streak">Best: {{ routine.data.bestStreak(date) }} {{ routine.data.isWeekly() ? (routine.data.bestStreak(date) === 1 ? 'week' : 'weeks') : (routine.data.bestStreak(date) === 1 ? 'day' : 'days') }}</span>
                    </div>
                  </template>
                </Column>
                <Column header="Streak" headerStyle="width: 40px; text-align: center" bodyStyle="text-align: center" >
                  <template #body="routine" >
                    <span v-bind:class="{'perfect': !routine.data.isWeekly() && routine.data.strike(date) >= 21}">{{ routine.data.strike(date) }}</span>
                  </template>
                </Column>
                <Column header="Fails" headerStyle="width: 40px; text-align: center" bodyStyle="text-align: center" >
                  <template #body="routine" >
                    {{ routine.data.fails(date) }}
                  </template>
                </Column>
                <Column headerStyle="width: 40px; text-align: center" bodyStyle="text-align: center" >
                  <template #body="routine" >
                    <span :class="statusColor(routine.data.status(date))">{{ routine.data.status(date) === null ? '—' : routine.data.status(date) + '%' }}</span>
                  </template>
                </Column>
              </DataTable>
            </Panel>
            <div v-else>No routines yet.</div>
</template>
<script>
export default {
  props: {routines: {type: Array, required: true}, date: {type: Date, required: true}, pendingIds: {type: Array, required: true}},
  emits: ['complete', 'undo'],
  methods: {
    statusColor(value) {
      if (value === null) return '';
      return value >= 80 ? 'perfect' : value >= 60 ? 'good' : value >= 50 ? 'normal' : value >= 40 ? 'fail' : 'bad';
    }
  }
};
</script>
<style scoped>
.routine-checkin-action {
  display: flex;
  justify-content: center;
}
.routine-name-cell {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.25rem 1rem;
}
.routine-best-streak {
  color: #64748b;
  white-space: nowrap;
}
</style>
