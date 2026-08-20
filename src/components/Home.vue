<template>
  <loading v-model:active="this.state.loading" :can-cancel="false" :is-full-page="true" />
  <WinCelebration ref="winCelebration" />
  <Dialog appendTo="body" header="Routine reminder" v-model:visible="routine_reminder_visible" :closeOnEscape="false" :closable="false" :modal="true" class="routine-reminder-dialog">
    <div v-if="routine_reminder" class="routine-reminder-dialog-content">
      <span class="routine-reminder-visual" aria-hidden="true"><i class="pi pi-bell"></i></span>
      <div class="routine-reminder-details">
        <span class="routine-reminder-kicker">It's time for</span>
        <strong class="routine-reminder-name">{{ routine_reminder?.name }}</strong>
        <div class="routine-reminder-schedule">
          <span class="routine-reminder-schedule-icon" aria-hidden="true"><i class="pi pi-clock"></i></span>
          <div class="routine-reminder-schedule-details">
            <span class="routine-reminder-schedule-label">Scheduled time</span>
            <strong class="routine-reminder-time">{{ format_routine_reminder_time(routine_reminder_schedule?.time) }}</strong>
          </div>
          <span class="routine-reminder-time-zone">Europe/Madrid</span>
        </div>
      </div>
    </div>
    <template #footer>
      <div class="routine-reminder-dialog-footer">
        <div class="routine-reminder-snooze-controls">
          <label for="routine-reminder-snooze-delay">Snooze for</label>
          <Dropdown inputId="routine-reminder-snooze-delay" aria-label="Snooze for" v-model="routine_reminder_snooze_minutes" :options="routine_reminder_snooze_options" optionLabel="label" optionValue="value" :disabled="routine_reminder_loading_action !== null" />
          <Button label="Snooze" icon="pi pi-clock" class="p-button-outlined p-button-secondary" :loading="routine_reminder_loading_action === 'snooze'" :disabled="routine_reminder_loading_action !== null" @click="snooze_routine_reminder" />
        </div>
        <Button label="Mark as done" icon="pi pi-check" class="routine-reminder-complete-button" :loading="routine_reminder_loading_action === 'complete'" :disabled="routine_reminder_loading_action !== null" @click="complete_routine_reminder" />
      </div>
    </template>
  </Dialog>
  <Dialog appendTo="body" :header="check_in_reminder_title" v-model:visible="check_in_reminder_visible" :closeOnEscape="false" :closable="false" :modal="true">
    <p>{{ check_in_reminder_message }}</p>
    <template #footer>
      <Button label="Record" icon="pi pi-check" @click="record_check_in_reminder" />
      <Button label="Dismiss" icon="pi pi-times" class="p-button-secondary" @click="dismiss_check_in_reminder" />
    </template>
  </Dialog>
  <MoodForm :initial_date="check_in_entry?.date" :period="check_in_entry?.period" fixed_date v-model:show="check_in_mood_form_visible" @onSave="save_check_in_entry" @onClose="close_check_in_entry" />
  <BackPainEpisodeForm :initial_date="check_in_entry?.date" :period="check_in_entry?.period" fixed_date v-model:show="check_in_back_form_visible" @onSave="save_check_in_entry" @onSaveAndContinue="load_all" @onClose="close_check_in_entry" />
  <WeightForm :initial_date="measurement_entry?.date" fixed_date v-model:show="measurement_weight_form_visible" @onSave="save_measurement_entry" @onClose="close_measurement_entry" />
  <BloodPressureForm :initial_date="measurement_entry?.date" fixed_date v-model:show="measurement_blood_pressure_form_visible" @onSave="save_measurement_entry" @onClose="close_measurement_entry" />
  <div v-if="!this.state.loading">
    <PushNotificationPrompt />
    <div class="p-grid p-mt-1" >
      <div class="p-col-12" v-if="this.daily_status" >
        <div class="dashboard-date-header">
          <div class="dashboard-date-summary">
            <span class="dashboard-date-icon" aria-hidden="true"><i class="pi pi-calendar"></i></span>
            <div>
              <div class="dashboard-date-label">Dashboard Date</div>
              <div class="dashboard-date-value-row">
                <span class="dashboard-date-value">{{ this.daily_status.dateFormat }}</span>
                <span class="dashboard-date-offset" :class="this.dashboard_date_offset_class">{{ this.dashboard_date_offset_label }}</span>
              </div>
            </div>
          </div>
          <div class="dashboard-date-actions">
            <Button icon="pi pi-arrow-left" label="Previous Day" class="p-button-outlined p-button-secondary dashboard-navigation-button" @click="previous_daily_status" :disabled="this.is_day_navigation_loading()" :loading="this.day_navigation_loading" />
            <Button icon="pi pi-plus" label="New Day" class="p-button-outlined dashboard-navigation-button" @click="new_daily_status" :disabled="this.daily_status.isToday() || this.is_day_navigation_loading()" :loading="this.day_navigation_loading" />
            <Button v-if="!this.can_show_reflection_advice()" icon="pi pi-comment" label="Reflection" class="p-button-outlined dashboard-reflection-button" @click="open_reflection" :disabled="!this.can_open_reflection() || this.dashboard_completion_loading || this.is_day_navigation_loading()" />
            <Button v-else icon="pi pi-comments" label="Ask for advice" class="p-button-outlined dashboard-reflection-button" @click="ask_for_advice" :disabled="!this.reflection_overview.actionConfigured || this.dashboard_completion_loading || this.is_day_navigation_loading()" />
            <Button v-if="this.can_toggle_dashboard_completion()"
                    :label="this.is_selected_date_completed() ? 'Undo Completed Day' : 'Mark Completed Day'"
                    :class="this.is_selected_date_completed() ? 'p-button-outlined p-button-warning dashboard-completion-button' : 'p-button-outlined p-button-success dashboard-completion-button'"
                    @click="toggle_dashboard_completion"
                    :disabled="this.dashboard_completion_loading || this.is_day_navigation_loading()"
                    :loading="this.dashboard_completion_loading">
              <template #icon="iconProps">
                <span :class="[iconProps.class, 'dashboard-completion-icons']">
                  <i :class="this.is_selected_date_completed() ? 'pi pi-undo' : 'pi pi-check'" />
                  <i v-if="this.has_dashboard_completion_warning()" class="pi pi-exclamation-circle missing-daily-entry-icon" role="img" title="Missing entry for selected date" aria-label="Missing entry for selected date" />
                </span>
              </template>
            </Button>
          </div>
        </div>
        <div class="performance-score-card">
          <div>
            <div class="performance-score-label">Performance Score</div>
          </div>
          <div class="performance-score-result">
            <span class="performance-score-value" :class="this.get_routine_status_color(this.get_performance_score())">
              {{ this.get_performance_score() }}<span class="performance-score-scale">/100</span>
            </span>
            <span v-if="this.get_performance_score_trend() !== 0" class="performance-score-trend" :class="this.get_performance_score_trend_class()">
              {{ this.format_performance_score_trend() }}
            </span>
          </div>
        </div>
        <Panel header="Week Score" class="week-status">
          <div class="p-grid p-mt-1" style="min-width: 1000px" >
            <div class="p-col-1" ></div>
            <div class="p-col-1 week-status-cell" style="border: thin solid gray;"></div>
            <div class="p-col-1 week-status-cell">Saturday</div>
            <div class="p-col-1 week-status-cell">Sunday</div>
            <div class="p-col-1 week-status-cell">Monday</div>
            <div class="p-col-1 week-status-cell">Tuesday</div>
            <div class="p-col-1 week-status-cell">Wednesday</div>
            <div class="p-col-1 week-status-cell">Thursday</div>
            <div class="p-col-1 week-status-cell">Friday</div>
            <div class="p-col-1 week-status-cell">Total</div>
            <div class="p-col-2" ></div>

            <div class="p-col-1" ></div>
            <div class="p-col-1 week-status-cell">Routines</div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.saturday" :class="this.get_routine_status_color(this.week_status.saturday.routines_percentage)">{{ this.week_status.saturday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.sunday" :class="this.get_routine_status_color(this.week_status.sunday.routines_percentage)">{{ this.week_status.sunday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.monday" :class="this.get_routine_status_color(this.week_status.monday.routines_percentage)">{{ this.week_status.monday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.tuesday" :class="this.get_routine_status_color(this.week_status.tuesday.routines_percentage)">{{ this.week_status.tuesday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.wednesday" :class="this.get_routine_status_color(this.week_status.wednesday.routines_percentage)">{{ this.week_status.wednesday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.thursday" :class="this.get_routine_status_color(this.week_status.thursday.routines_percentage)">{{ this.week_status.thursday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.friday" :class="this.get_routine_status_color(this.week_status.friday.routines_percentage)">{{ this.week_status.friday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_routine_status_color(this.get_completed_routine_week_percentage_total(this.week_status, 'routines_percentage'))">{{ this.format_completed_routine_week_percentage_total(this.week_status, 'routines_percentage') }}</span></div>
            <div class="p-col-2" ></div>
            <div class="p-col-1" ></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.saturday" :class="this.get_routine_status_color(this.week_ago_status.saturday.routines_percentage)">{{ this.week_ago_status.saturday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.sunday" :class="this.get_routine_status_color(this.week_ago_status.sunday.routines_percentage)">{{ this.week_ago_status.sunday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.monday" :class="this.get_routine_status_color(this.week_ago_status.monday.routines_percentage)">{{ this.week_ago_status.monday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.tuesday" :class="this.get_routine_status_color(this.week_ago_status.tuesday.routines_percentage)">{{ this.week_ago_status.tuesday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.wednesday" :class="this.get_routine_status_color(this.week_ago_status.wednesday.routines_percentage)">{{ this.week_ago_status.wednesday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.thursday" :class="this.get_routine_status_color(this.week_ago_status.thursday.routines_percentage)">{{ this.week_ago_status.thursday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.friday" :class="this.get_routine_status_color(this.week_ago_status.friday.routines_percentage)">{{ this.week_ago_status.friday.routines_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_routine_status_color(this.get_completed_routine_week_percentage_total(this.week_ago_status, 'routines_percentage'))">{{ this.format_completed_routine_week_percentage_total(this.week_ago_status, 'routines_percentage') }}</span></div>
            <div class="p-col-2" ></div>

            <div class="p-col-1" ></div>
            <div class="p-col-1 week-status-cell">Weight</div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.saturday" :class="this.get_routine_status_color(this.week_status.saturday.weight_percentage)">{{ this.week_status.saturday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.sunday" :class="this.get_routine_status_color(this.week_status.sunday.weight_percentage)">{{ this.week_status.sunday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.monday" :class="this.get_routine_status_color(this.week_status.monday.weight_percentage)">{{ this.week_status.monday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.tuesday" :class="this.get_routine_status_color(this.week_status.tuesday.weight_percentage)">{{ this.week_status.tuesday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.wednesday" :class="this.get_routine_status_color(this.week_status.wednesday.weight_percentage)">{{ this.week_status.wednesday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.thursday" :class="this.get_routine_status_color(this.week_status.thursday.weight_percentage)">{{ this.week_status.thursday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.friday" :class="this.get_routine_status_color(this.week_status.friday.weight_percentage)">{{ this.week_status.friday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_routine_status_color(this.get_completed_routine_week_percentage_total(this.week_status, 'weight_percentage'))">{{ this.format_completed_routine_week_percentage_total(this.week_status, 'weight_percentage') }}</span></div>
            <div class="p-col-2" ></div>
            <div class="p-col-1" ></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.saturday" :class="this.get_routine_status_color(this.week_ago_status.saturday.weight_percentage)">{{ this.week_ago_status.saturday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.sunday" :class="this.get_routine_status_color(this.week_ago_status.sunday.weight_percentage)">{{ this.week_ago_status.sunday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.monday" :class="this.get_routine_status_color(this.week_ago_status.monday.weight_percentage)">{{ this.week_ago_status.monday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.tuesday" :class="this.get_routine_status_color(this.week_ago_status.tuesday.weight_percentage)">{{ this.week_ago_status.tuesday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.wednesday" :class="this.get_routine_status_color(this.week_ago_status.wednesday.weight_percentage)">{{ this.week_ago_status.wednesday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.thursday" :class="this.get_routine_status_color(this.week_ago_status.thursday.weight_percentage)">{{ this.week_ago_status.thursday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.friday" :class="this.get_routine_status_color(this.week_ago_status.friday.weight_percentage)">{{ this.week_ago_status.friday.weight_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_routine_status_color(this.get_completed_routine_week_percentage_total(this.week_ago_status, 'weight_percentage'))">{{ this.format_completed_routine_week_percentage_total(this.week_ago_status, 'weight_percentage') }}</span></div>
            <div class="p-col-2" ></div>

            <div class="p-col-1" ></div>
            <div class="p-col-1 week-status-cell">Blood Pressure</div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.saturday" :class="this.get_routine_status_color(this.week_status.saturday.blood_pressure_percentage)">{{ this.week_status.saturday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.sunday" :class="this.get_routine_status_color(this.week_status.sunday.blood_pressure_percentage)">{{ this.week_status.sunday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.monday" :class="this.get_routine_status_color(this.week_status.monday.blood_pressure_percentage)">{{ this.week_status.monday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.tuesday" :class="this.get_routine_status_color(this.week_status.tuesday.blood_pressure_percentage)">{{ this.week_status.tuesday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.wednesday" :class="this.get_routine_status_color(this.week_status.wednesday.blood_pressure_percentage)">{{ this.week_status.wednesday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.thursday" :class="this.get_routine_status_color(this.week_status.thursday.blood_pressure_percentage)">{{ this.week_status.thursday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.friday" :class="this.get_routine_status_color(this.week_status.friday.blood_pressure_percentage)">{{ this.week_status.friday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_routine_status_color(this.get_completed_routine_week_percentage_total(this.week_status, 'blood_pressure_percentage'))">{{ this.format_completed_routine_week_percentage_total(this.week_status, 'blood_pressure_percentage') }}</span></div>
            <div class="p-col-2" ></div>
            <div class="p-col-1" ></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.saturday" :class="this.get_routine_status_color(this.week_ago_status.saturday.blood_pressure_percentage)">{{ this.week_ago_status.saturday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.sunday" :class="this.get_routine_status_color(this.week_ago_status.sunday.blood_pressure_percentage)">{{ this.week_ago_status.sunday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.monday" :class="this.get_routine_status_color(this.week_ago_status.monday.blood_pressure_percentage)">{{ this.week_ago_status.monday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.tuesday" :class="this.get_routine_status_color(this.week_ago_status.tuesday.blood_pressure_percentage)">{{ this.week_ago_status.tuesday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.wednesday" :class="this.get_routine_status_color(this.week_ago_status.wednesday.blood_pressure_percentage)">{{ this.week_ago_status.wednesday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.thursday" :class="this.get_routine_status_color(this.week_ago_status.thursday.blood_pressure_percentage)">{{ this.week_ago_status.thursday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.friday" :class="this.get_routine_status_color(this.week_ago_status.friday.blood_pressure_percentage)">{{ this.week_ago_status.friday.blood_pressure_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_routine_status_color(this.get_completed_routine_week_percentage_total(this.week_ago_status, 'blood_pressure_percentage'))">{{ this.format_completed_routine_week_percentage_total(this.week_ago_status, 'blood_pressure_percentage') }}</span></div>
            <div class="p-col-2" ></div>

            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell">Flexibility</div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.saturday" :class="this.get_routine_status_color(this.week_status.saturday.flexibility_percentage)">{{ this.week_status.saturday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.sunday" :class="this.get_routine_status_color(this.week_status.sunday.flexibility_percentage)">{{ this.week_status.sunday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.monday" :class="this.get_routine_status_color(this.week_status.monday.flexibility_percentage)">{{ this.week_status.monday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.tuesday" :class="this.get_routine_status_color(this.week_status.tuesday.flexibility_percentage)">{{ this.week_status.tuesday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.wednesday" :class="this.get_routine_status_color(this.week_status.wednesday.flexibility_percentage)">{{ this.week_status.wednesday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.thursday" :class="this.get_routine_status_color(this.week_status.thursday.flexibility_percentage)">{{ this.week_status.thursday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.friday" :class="this.get_routine_status_color(this.week_status.friday.flexibility_percentage)">{{ this.week_status.friday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_routine_status_color(this.get_completed_routine_week_percentage_total(this.week_status, 'flexibility_percentage'))">{{ this.format_completed_routine_week_percentage_total(this.week_status, 'flexibility_percentage') }}</span></div>
            <div class="p-col-2" ></div>
            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.saturday" :class="this.get_routine_status_color(this.week_ago_status.saturday.flexibility_percentage)">{{ this.week_ago_status.saturday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.sunday" :class="this.get_routine_status_color(this.week_ago_status.sunday.flexibility_percentage)">{{ this.week_ago_status.sunday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.monday" :class="this.get_routine_status_color(this.week_ago_status.monday.flexibility_percentage)">{{ this.week_ago_status.monday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.tuesday" :class="this.get_routine_status_color(this.week_ago_status.tuesday.flexibility_percentage)">{{ this.week_ago_status.tuesday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.wednesday" :class="this.get_routine_status_color(this.week_ago_status.wednesday.flexibility_percentage)">{{ this.week_ago_status.wednesday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.thursday" :class="this.get_routine_status_color(this.week_ago_status.thursday.flexibility_percentage)">{{ this.week_ago_status.thursday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.friday" :class="this.get_routine_status_color(this.week_ago_status.friday.flexibility_percentage)">{{ this.week_ago_status.friday.flexibility_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_routine_status_color(this.get_completed_routine_week_percentage_total(this.week_ago_status, 'flexibility_percentage'))">{{ this.format_completed_routine_week_percentage_total(this.week_ago_status, 'flexibility_percentage') }}</span></div>
            <div class="p-col-2" ></div>

            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell">Mind</div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.saturday" :class="this.get_routine_status_color(this.week_status.saturday.mind_percentage)">{{ this.week_status.saturday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.sunday" :class="this.get_routine_status_color(this.week_status.sunday.mind_percentage)">{{ this.week_status.sunday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.monday" :class="this.get_routine_status_color(this.week_status.monday.mind_percentage)">{{ this.week_status.monday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.tuesday" :class="this.get_routine_status_color(this.week_status.tuesday.mind_percentage)">{{ this.week_status.tuesday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.wednesday" :class="this.get_routine_status_color(this.week_status.wednesday.mind_percentage)">{{ this.week_status.wednesday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.thursday" :class="this.get_routine_status_color(this.week_status.thursday.mind_percentage)">{{ this.week_status.thursday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span v-if="this.week_status.friday" :class="this.get_routine_status_color(this.week_status.friday.mind_percentage)">{{ this.week_status.friday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_routine_status_color(this.get_completed_routine_week_percentage_total(this.week_status, 'mind_percentage'))">{{ this.format_completed_routine_week_percentage_total(this.week_status, 'mind_percentage') }}</span></div>
            <div class="p-col-2" ></div>
            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.saturday" :class="this.get_routine_status_color(this.week_ago_status.saturday.mind_percentage)">{{ this.week_ago_status.saturday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.sunday" :class="this.get_routine_status_color(this.week_ago_status.sunday.mind_percentage)">{{ this.week_ago_status.sunday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.monday" :class="this.get_routine_status_color(this.week_ago_status.monday.mind_percentage)">{{ this.week_ago_status.monday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.tuesday" :class="this.get_routine_status_color(this.week_ago_status.tuesday.mind_percentage)">{{ this.week_ago_status.tuesday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.wednesday" :class="this.get_routine_status_color(this.week_ago_status.wednesday.mind_percentage)">{{ this.week_ago_status.wednesday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.thursday" :class="this.get_routine_status_color(this.week_ago_status.thursday.mind_percentage)">{{ this.week_ago_status.thursday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span v-if="this.week_ago_status.friday" :class="this.get_routine_status_color(this.week_ago_status.friday.mind_percentage)">{{ this.week_ago_status.friday.mind_percentage }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_routine_status_color(this.get_completed_routine_week_percentage_total(this.week_ago_status, 'mind_percentage'))">{{ this.format_completed_routine_week_percentage_total(this.week_ago_status, 'mind_percentage') }}</span></div>
            <div class="p-col-2" ></div>

            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell">Mood</div>
            <div class="p-col-1 week-status-cell">{{ this.get_day_mood(this.week_status.saturday) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.get_day_mood(this.week_status.sunday) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.get_day_mood(this.week_status.monday) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.get_day_mood(this.week_status.tuesday) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.get_day_mood(this.week_status.wednesday) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.get_day_mood(this.week_status.thursday) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.get_day_mood(this.week_status.friday) }}</div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_mood_color(this.get_week_mood_average(this.week_status, this.daily_status.date))">{{ this.get_mood_average_emoji(this.get_week_mood_average(this.week_status, this.daily_status.date)) }}</span></div>
            <div class="p-col-2" ></div>
            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.get_day_mood(this.week_ago_status.saturday) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.get_day_mood(this.week_ago_status.sunday) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.get_day_mood(this.week_ago_status.monday) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.get_day_mood(this.week_ago_status.tuesday) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.get_day_mood(this.week_ago_status.wednesday) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.get_day_mood(this.week_ago_status.thursday) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.get_day_mood(this.week_ago_status.friday) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.get_mood_average_emoji(this.get_week_mood_average(this.week_ago_status, this.last_week_daily_status.date)) }}</div>
            <div class="p-col-2" ></div>

            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell">Sleep</div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_color(this.week_status.saturday?.date)">{{ this.format_week_sleep(this.week_status.saturday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_color(this.week_status.sunday?.date)">{{ this.format_week_sleep(this.week_status.sunday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_color(this.week_status.monday?.date)">{{ this.format_week_sleep(this.week_status.monday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_color(this.week_status.tuesday?.date)">{{ this.format_week_sleep(this.week_status.tuesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_color(this.week_status.wednesday?.date)">{{ this.format_week_sleep(this.week_status.wednesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_color(this.week_status.thursday?.date)">{{ this.format_week_sleep(this.week_status.thursday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_color(this.week_status.friday?.date)">{{ this.format_week_sleep(this.week_status.friday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_average_color(this.week_status, this.daily_status.date)">{{ this.format_week_sleep_average(this.week_status, this.daily_status.date) }}</span></div>
            <div class="p-col-2" ></div>
            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_color(this.week_ago_status.saturday?.date)">{{ this.format_week_sleep(this.week_ago_status.saturday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_color(this.week_ago_status.sunday?.date)">{{ this.format_week_sleep(this.week_ago_status.sunday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_color(this.week_ago_status.monday?.date)">{{ this.format_week_sleep(this.week_ago_status.monday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_color(this.week_ago_status.tuesday?.date)">{{ this.format_week_sleep(this.week_ago_status.tuesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_color(this.week_ago_status.wednesday?.date)">{{ this.format_week_sleep(this.week_ago_status.wednesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_color(this.week_ago_status.thursday?.date)">{{ this.format_week_sleep(this.week_ago_status.thursday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_color(this.week_ago_status.friday?.date)">{{ this.format_week_sleep(this.week_ago_status.friday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_average_color(this.week_ago_status, this.last_week_daily_status.date)">{{ this.format_week_sleep_average(this.week_ago_status, this.last_week_daily_status.date) }}</span></div>
            <div class="p-col-2" ></div>

            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell">Avg Heart Rate</div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_status.saturday?.date)">{{ this.format_week_sleep_heart_rate(this.week_status.saturday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_status.sunday?.date)">{{ this.format_week_sleep_heart_rate(this.week_status.sunday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_status.monday?.date)">{{ this.format_week_sleep_heart_rate(this.week_status.monday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_status.tuesday?.date)">{{ this.format_week_sleep_heart_rate(this.week_status.tuesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_status.wednesday?.date)">{{ this.format_week_sleep_heart_rate(this.week_status.wednesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_status.thursday?.date)">{{ this.format_week_sleep_heart_rate(this.week_status.thursday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_status.friday?.date)">{{ this.format_week_sleep_heart_rate(this.week_status.friday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_heart_rate_average_color(this.week_status, this.daily_status.date)">{{ this.format_week_sleep_heart_rate_average(this.week_status, this.daily_status.date) }}</span></div>
            <div class="p-col-2" ></div>
            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_ago_status.saturday?.date)">{{ this.format_week_sleep_heart_rate(this.week_ago_status.saturday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_ago_status.sunday?.date)">{{ this.format_week_sleep_heart_rate(this.week_ago_status.sunday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_ago_status.monday?.date)">{{ this.format_week_sleep_heart_rate(this.week_ago_status.monday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_ago_status.tuesday?.date)">{{ this.format_week_sleep_heart_rate(this.week_ago_status.tuesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_ago_status.wednesday?.date)">{{ this.format_week_sleep_heart_rate(this.week_ago_status.wednesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_ago_status.thursday?.date)">{{ this.format_week_sleep_heart_rate(this.week_ago_status.thursday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_heart_rate_color(this.week_ago_status.friday?.date)">{{ this.format_week_sleep_heart_rate(this.week_ago_status.friday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_heart_rate_average_color(this.week_ago_status, this.last_week_daily_status.date)">{{ this.format_week_sleep_heart_rate_average(this.week_ago_status, this.last_week_daily_status.date) }}</span></div>
            <div class="p-col-2" ></div>

            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell">HRV</div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_hrv_color(this.week_status.saturday?.date)">{{ this.format_week_sleep_hrv(this.week_status.saturday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_hrv_color(this.week_status.sunday?.date)">{{ this.format_week_sleep_hrv(this.week_status.sunday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_hrv_color(this.week_status.monday?.date)">{{ this.format_week_sleep_hrv(this.week_status.monday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_hrv_color(this.week_status.tuesday?.date)">{{ this.format_week_sleep_hrv(this.week_status.tuesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_hrv_color(this.week_status.wednesday?.date)">{{ this.format_week_sleep_hrv(this.week_status.wednesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_hrv_color(this.week_status.thursday?.date)">{{ this.format_week_sleep_hrv(this.week_status.thursday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_hrv_color(this.week_status.friday?.date)">{{ this.format_week_sleep_hrv(this.week_status.friday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_sleep_hrv_average_color(this.week_status, this.daily_status.date)">{{ this.format_week_sleep_hrv_average(this.week_status, this.daily_status.date) }}</span></div>
            <div class="p-col-2" ></div>
            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_hrv_color(this.week_ago_status.saturday?.date)">{{ this.format_week_sleep_hrv(this.week_ago_status.saturday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_hrv_color(this.week_ago_status.sunday?.date)">{{ this.format_week_sleep_hrv(this.week_ago_status.sunday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_hrv_color(this.week_ago_status.monday?.date)">{{ this.format_week_sleep_hrv(this.week_ago_status.monday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_hrv_color(this.week_ago_status.tuesday?.date)">{{ this.format_week_sleep_hrv(this.week_ago_status.tuesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_hrv_color(this.week_ago_status.wednesday?.date)">{{ this.format_week_sleep_hrv(this.week_ago_status.wednesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_hrv_color(this.week_ago_status.thursday?.date)">{{ this.format_week_sleep_hrv(this.week_ago_status.thursday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_hrv_color(this.week_ago_status.friday?.date)">{{ this.format_week_sleep_hrv(this.week_ago_status.friday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_sleep_hrv_average_color(this.week_ago_status, this.last_week_daily_status.date)">{{ this.format_week_sleep_hrv_average(this.week_ago_status, this.last_week_daily_status.date) }}</span></div>
            <div class="p-col-2" ></div>

            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell">Calories</div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_calories_color(this.week_status.saturday?.date)">{{ this.format_week_calories(this.week_status.saturday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_calories_color(this.week_status.sunday?.date)">{{ this.format_week_calories(this.week_status.sunday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_calories_color(this.week_status.monday?.date)">{{ this.format_week_calories(this.week_status.monday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_calories_color(this.week_status.tuesday?.date)">{{ this.format_week_calories(this.week_status.tuesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_calories_color(this.week_status.wednesday?.date)">{{ this.format_week_calories(this.week_status.wednesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_calories_color(this.week_status.thursday?.date)">{{ this.format_week_calories(this.week_status.thursday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_calories_color(this.week_status.friday?.date)">{{ this.format_week_calories(this.week_status.friday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell"><span :class="this.get_week_calories_average_color(this.week_status, this.daily_status.date)">{{ this.format_week_calories_average(this.week_status, this.daily_status.date) }}</span></div>
            <div class="p-col-2" ></div>
            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_calories_color(this.week_ago_status.saturday?.date)">{{ this.format_week_calories(this.week_ago_status.saturday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_calories_color(this.week_ago_status.sunday?.date)">{{ this.format_week_calories(this.week_ago_status.sunday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_calories_color(this.week_ago_status.monday?.date)">{{ this.format_week_calories(this.week_ago_status.monday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_calories_color(this.week_ago_status.tuesday?.date)">{{ this.format_week_calories(this.week_ago_status.tuesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_calories_color(this.week_ago_status.wednesday?.date)">{{ this.format_week_calories(this.week_ago_status.wednesday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_calories_color(this.week_ago_status.thursday?.date)">{{ this.format_week_calories(this.week_ago_status.thursday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_calories_color(this.week_ago_status.friday?.date)">{{ this.format_week_calories(this.week_ago_status.friday?.date) }}</span></div>
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_week_calories_average_color(this.week_ago_status, this.last_week_daily_status.date)">{{ this.format_week_calories_average(this.week_ago_status, this.last_week_daily_status.date) }}</span></div>
            <div class="p-col-2" ></div>

            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell">Typical Calories</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_typical_calories(this.get_selected_week_dates()[0]) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_typical_calories(this.get_selected_week_dates()[1]) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_typical_calories(this.get_selected_week_dates()[2]) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_typical_calories(this.get_selected_week_dates()[3]) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_typical_calories(this.get_selected_week_dates()[4]) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_typical_calories(this.get_selected_week_dates()[5]) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_typical_calories(this.get_selected_week_dates()[6]) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_typical_calories_average() }}</div>
            <div class="p-col-2" ></div>

          </div>
        </Panel>
      </div>
      <div class="p-col-12" v-if="this.daily_status">
        <ScrollableTabView class="home-panels-tabs" scrollable>
          <TabPanel header="Status">
            <Panel class="p-panel-content-without-padding">
              <template #header>
                <div class="table-header">
                  <strong>Status</strong>
                </div>
              </template>
              <div class="p-grid" >
                <div class="p-col-4">Status: </div>
                <div class="p-col-8">
                  <span :class="this.get_routine_status_color(this.daily_status.routines_percentage)">{{this.daily_status.total_routines}}/{{this.daily_status.routines_done}}</span>
                  &nbsp;<span v-if="this.daily_status.routines_done - this.last_week_daily_status.routines_done !== 0" v-bind:class="{'perfect': this.daily_status.routines_done - this.last_week_daily_status.routines_done > 0, 'bad': this.daily_status.routines_done - this.last_week_daily_status.routines_done <= 0}" >{{ this.daily_status.routines_done - this.last_week_daily_status.routines_done  > 0 ? '+' : '' }}{{ this.daily_status.routines_done - this.last_week_daily_status.routines_done }}</span>
                </div>
                <div class="p-col-4">Trend Status: </div>
                <div class="p-col-8">
                  <span :class="this.get_routine_status_color(this.daily_status.routines_status)">{{this.daily_status.total_routines}}/{{this.daily_status.routines_score}} ({{this.daily_status.routines_status}}%)</span>
                  &nbsp;<span v-if="this.get_difference(this.daily_status.routines_status, this.last_week_daily_status.routines_status) !== 0" v-bind:class="{'perfect': this.get_difference(this.daily_status.routines_status, this.last_week_daily_status.routines_status) > 0, 'bad': this.get_difference(daily_status.routines_status, this.last_week_daily_status.routines_status) <= 0}" >{{ this.get_difference(this.daily_status.routines_status, this.last_week_daily_status.routines_status) > 0 ? '+' : '' }}{{ this.get_difference(this.daily_status.routines_status, this.last_week_daily_status.routines_status) }}</span>
                </div>
                <div class="p-col-12"/>
                <div class="p-col-4">Weight: </div>
                <div class="p-col-8">
                  <span :class="this.get_routine_status_color(this.daily_status.weight_percentage)">{{this.daily_status.total_weight_routines}}/{{this.daily_status.weight_done}}</span>
                  &nbsp;<span v-if="this.daily_status.weight_done - this.last_week_daily_status.weight_done !== 0" v-bind:class="{'perfect': this.daily_status.weight_done - this.last_week_daily_status.weight_done > 0, 'bad': this.daily_status.weight_done - this.last_week_daily_status.weight_done <= 0}" >{{ this.daily_status.weight_done - this.last_week_daily_status.weight_done  > 0 ? '+' : '' }}{{ this.daily_status.weight_done - this.last_week_daily_status.weight_done }}</span>
                </div>
                <div class="p-col-4">Trend Weight: </div>
                <div class="p-col-8">
                  <span :class="this.get_routine_status_color(this.daily_status.weight_status)">{{this.daily_status.total_weight_routines}}/{{this.daily_status.weight_score}} ({{this.daily_status.weight_status}}%)</span>
                  &nbsp;<span v-if="this.get_difference(this.daily_status.weight_status, this.last_week_daily_status.weight_status) !== 0" v-bind:class="{'perfect': this.get_difference(this.daily_status.weight_status, this.last_week_daily_status.weight_status) > 0, 'bad': this.get_difference(daily_status.weight_status, this.last_week_daily_status.weight_status) <= 0}" >{{ this.get_difference(this.daily_status.weight_status, this.last_week_daily_status.weight_status) > 0 ? '+' : '' }}{{ this.get_difference(this.daily_status.weight_status, this.last_week_daily_status.weight_status) }}</span>
                </div>
                <div class="p-col-12"/>
                <div class="p-col-4">Blood Pressure: </div>
                <div class="p-col-8">
                  <span :class="this.get_routine_status_color(this.daily_status.blood_pressure_percentage)">{{this.daily_status.total_blood_pressure_routines}}/{{this.daily_status.blood_pressure_done}}</span>
                  &nbsp;<span v-if="this.daily_status.blood_pressure_done - this.last_week_daily_status.blood_pressure_done !== 0" v-bind:class="{'perfect': this.daily_status.blood_pressure_done - this.last_week_daily_status.blood_pressure_done > 0, 'bad': this.daily_status.blood_pressure_done - this.last_week_daily_status.blood_pressure_done <= 0}" >{{ this.daily_status.blood_pressure_done - this.last_week_daily_status.blood_pressure_done  > 0 ? '+' : '' }}{{ this.daily_status.blood_pressure_done - this.last_week_daily_status.blood_pressure_done }}</span>
                </div>
                <div class="p-col-4">Trend Blood Pressure: </div>
                <div class="p-col-8">
                  <span :class="this.get_routine_status_color(this.daily_status.blood_pressure_status)">{{this.daily_status.total_blood_pressure_routines}}/{{this.daily_status.blood_pressure_score}} ({{this.daily_status.blood_pressure_status}}%)</span>
                  &nbsp;<span v-if="this.get_difference(this.daily_status.blood_pressure_status, this.last_week_daily_status.blood_pressure_status) !== 0" v-bind:class="{'perfect': this.get_difference(this.daily_status.blood_pressure_status, this.last_week_daily_status.blood_pressure_status) > 0, 'bad': this.get_difference(daily_status.blood_pressure_status, this.last_week_daily_status.blood_pressure_status) <= 0}" >{{ this.get_difference(this.daily_status.blood_pressure_status, this.last_week_daily_status.blood_pressure_status) > 0 ? '+' : '' }}{{ this.get_difference(this.daily_status.blood_pressure_status, this.last_week_daily_status.blood_pressure_status) }}</span>
                </div>
                <div class="p-col-12"/>
                <div class="p-col-4">Flexibility: </div>
                <div class="p-col-8">
                  <span :class="this.get_routine_status_color(this.daily_status.flexibility_percentage)">{{this.daily_status.total_flexibility_routines}}/{{this.daily_status.flexibility_done}}</span>
                  &nbsp;<span v-if="this.daily_status.flexibility_done - this.last_week_daily_status.flexibility_done !== 0" v-bind:class="{'perfect': this.daily_status.flexibility_done - this.last_week_daily_status.flexibility_done > 0, 'bad': this.daily_status.flexibility_done - this.last_week_daily_status.flexibility_done <= 0}" >{{ this.daily_status.flexibility_done - this.last_week_daily_status.flexibility_done  > 0 ? '+' : '' }}{{ this.daily_status.flexibility_done - this.last_week_daily_status.flexibility_done }}</span>
                </div>
                <div class="p-col-4">Trend Flexibility: </div>
                <div class="p-col-8">
                  <span :class="this.get_routine_status_color(this.daily_status.flexibility_status)">{{this.daily_status.total_flexibility_routines}}/{{this.daily_status.flexibility_score}} ({{this.daily_status.flexibility_status}}%)</span>
                  &nbsp;<span v-if="this.get_difference(this.daily_status.flexibility_status, this.last_week_daily_status.flexibility_status) !== 0" v-bind:class="{'perfect': this.get_difference(this.daily_status.flexibility_status, this.last_week_daily_status.flexibility_status) > 0, 'bad': this.get_difference(daily_status.flexibility_status, this.last_week_daily_status.flexibility_status) <= 0}" >{{ this.get_difference(this.daily_status.flexibility_status, this.last_week_daily_status.flexibility_status) > 0 ? '+' : '' }}{{ this.get_difference(this.daily_status.flexibility_status, this.last_week_daily_status.flexibility_status) }}</span>
                </div>
                <div class="p-col-12"/>
                <div class="p-col-4">Mind: </div>
                <div class="p-col-8">
                  <span :class="this.get_routine_status_color(this.daily_status.mind_percentage)">{{this.daily_status.total_mind_routines}}/{{this.daily_status.mind_done}}</span>
                  &nbsp;<span v-if="this.daily_status.mind_done - this.last_week_daily_status.mind_done !== 0" v-bind:class="{'perfect': this.daily_status.mind_done - this.last_week_daily_status.mind_done > 0, 'bad': this.daily_status.mind_done - this.last_week_daily_status.mind_done <= 0}" >{{ this.daily_status.mind_done - this.last_week_daily_status.mind_done  > 0 ? '+' : '' }}{{ this.daily_status.mind_done - this.last_week_daily_status.mind_done }}</span>
                </div>
                <div class="p-col-4">Trend Mind: </div>
                <div class="p-col-8">
                  <span :class="this.get_routine_status_color(this.daily_status.mind_status)">{{this.daily_status.total_mind_routines}}/{{this.daily_status.mind_score}} ({{this.daily_status.mind_status}}%)</span>
                  &nbsp;<span v-if="this.get_difference(this.daily_status.mind_status, this.last_week_daily_status.mind_status) !== 0" v-bind:class="{'perfect': this.get_difference(this.daily_status.mind_status, this.last_week_daily_status.mind_status) > 0, 'bad': this.get_difference(daily_status.mind_status, this.last_week_daily_status.mind_status) <= 0}" >{{ this.get_difference(this.daily_status.mind_status, this.last_week_daily_status.mind_status) > 0 ? '+' : '' }}{{ this.get_difference(this.daily_status.mind_status, this.last_week_daily_status.mind_status) }}</span>
                </div>
                <div class="p-col-12"/>
                <div class="p-col-4">Mood: </div>
                <div class="p-col-8">
                  <span :class="this.get_mood_color(this.daily_status.mood.average)">{{ this.format_mood_average(this.daily_status.mood.average) }}</span>
                  &nbsp;<span v-if="this.get_mood_value_difference(this.daily_status.mood, this.last_week_daily_status.mood) !== null && this.get_mood_value_difference(this.daily_status.mood, this.last_week_daily_status.mood) !== 0" :class="this.get_difference_class(this.get_mood_value_difference(this.daily_status.mood, this.last_week_daily_status.mood))">{{ this.get_mood_value_difference(this.daily_status.mood, this.last_week_daily_status.mood) > 0 ? '+' : '' }}{{ this.get_mood_value_difference(this.daily_status.mood, this.last_week_daily_status.mood) }}</span>
                </div>
                <div class="p-col-4">Mood (30-Day Average): </div>
                <div class="p-col-8">
                  <span :class="this.get_mood_color(this.get_mood_trend_color_value(this.daily_status.mood_trend))">{{ this.format_mood_average(this.daily_status.mood_trend) }}</span>
                  &nbsp;<span v-if="this.get_mood_trend_difference() !== null && this.get_mood_trend_difference() !== 0" :class="this.get_difference_class(this.get_mood_trend_difference())">{{ this.get_mood_trend_difference() > 0 ? '+' : '' }}{{ this.get_mood_trend_difference() }}</span>
                </div>
              </div>
            </Panel>
          </TabPanel>
          <TabPanel>
            <template #header>
              <span class="daily-entry-tab-header">
                <span>Routines</span>
                <i v-if="is_routine_entry_missing()" class="pi pi-exclamation-circle missing-daily-entry-icon" role="img" title="Missing entry for selected date" aria-label="Missing entry for selected date" />
              </span>
            </template>
            <Panel v-if="routines.length > 0" class="p-panel-content-without-padding" >
              <template #header><div class="table-header"><strong>Routines ({{this.routines.length}})</strong></div></template>
              <DataTable :value="this.routines" responsiveLayout="scroll" scrollHeight="300px"
                         paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                         currentPageReportTemplate="{first} to {last} of {totalRecords}" >
                <Column headerStyle="width: 55px" bodyStyle="text-align: center" >
                  <template #body="routine">
                    <Button v-if="isRoutineDone(routine.data)" icon="pi pi-undo" class="p-button-rounded p-button-warning" @click="undoRoutine(routine.data)" :disabled="isRoutineActionPending(routine.data.id)" :loading="isRoutineActionPending(routine.data.id)" />
                    <Button v-else icon="pi pi-plus" class="p-button-rounded p-button-success" @click="plusRoutine(routine.data)" :disabled="isRoutineCheckinDisabled(routine.data)" :loading="isRoutineActionPending(routine.data.id)" />
                  </template>
                </Column>
                <Column>
                  <template #body="routine" >
                    {{ routine.data.name }}
                  </template>
                </Column>
                <Column header="Strike" headerStyle="width: 40px; text-align: center" bodyStyle="text-align: center" >
                  <template #body="routine" >
                    <span v-bind:class="{'perfect': routine.data.strike(this.daily_status.date) >= 21}">{{ routine.data.strike(this.daily_status.date) }}</span>
                  </template>
                </Column>
                <Column header="Fails" headerStyle="width: 40px; text-align: center" bodyStyle="text-align: center" >
                  <template #body="routine" >
                    {{ routine.data.fails(this.daily_status.date) }}
                  </template>
                </Column>
                <Column headerStyle="width: 40px; text-align: center" bodyStyle="text-align: center" >
                  <template #body="routine" >
                    <span :class="this.get_routine_status_color(routine.data.status(this.daily_status.date))">{{ routine.data.status(this.daily_status.date) }}%</span>
                  </template>
                </Column>
              </DataTable>
            </Panel>
            <div v-else>No routines yet.</div>
          </TabPanel>
          <TabPanel header="Body">
            <div class="p-grid">
              <div class="p-col-12">
                <Panel>
                  <template #header>
                    <div class="table-header">
                      <strong>Last Weight</strong>
                      <CreateWeight :initial_date="daily_status.date" :weight="get_weight_for(daily_status.date)" fixed_date @onSave="load_all" />
                    </div>
                  </template>
                  <div class="p-grid" v-if="last_weight && current_weight_trend" >
                    <div class="p-col-6">
                      <div id="fat-bar-status" />
                    </div>
                    <div class="p-col-6">
                      <div id="bmi-bar-status" />
                    </div>
                    <div class="p-col-5">Date: </div>
                    <div class="p-col-7">{{ last_weight.dateFormat }}</div>
                    <div class="p-col-5">Weight: </div>
                    <div class="p-col-7">{{ last_weight.weight }} kg <span v-bind:class="{'bad': last_weight.lost_weight > 0, 'good': last_weight.lost_weight <= 0}">{{ last_weight.lost_weight > 0 ? '+' : '' }}{{ last_weight.lost_weight }}kg</span></div>
                    <div class="p-col-5">Fat: </div>
                    <div class="p-col-7">{{ last_weight.fat }} kg ({{ last_weight.fat_percentage }}%) <span v-bind:class="{'bad': last_weight.lost_fat > 0, 'good': last_weight.lost_fat <= 0}">{{ last_weight.lost_fat > 0 ? '+' : '' }}{{ last_weight.lost_fat }}kg</span></div>
                    <div class="p-col-5">Muscle: </div>
                    <div class="p-col-7">{{ last_weight.muscle }} kg ({{ last_weight.muscle_percentage }}%) <span class="extra_info" v-bind:class="{'good': last_weight.lost_muscle >= 0, 'bad': last_weight.lost_muscle < 0}">{{ last_weight.lost_muscle > 0 ? '+' : '' }}{{ last_weight.lost_muscle }}kg</span></div>
                    <div class="p-col-5">Status: </div>
                    <div class="p-col-7" :style="{color: last_weight.status().color}">{{ last_weight.status().name }}</div>
                    <div class="p-col-5">BMI: </div>
                    <div class="p-col-7">{{ last_weight.bmi().value }} <span :style="{color: last_weight.bmi().status().color}">{{ last_weight.bmi().status().name }}</span></div>
                    <div class="p-col-5">Current Weight-Loss Trend: </div>
                    <div class="p-col-7"><span v-bind:class="{'bad': current_weight_trend.lost_weight > 0, 'good': current_weight_trend.lost_weight <= 0}">{{ current_weight_trend.lost_weight > 0 ? '+' : '' }}{{ current_weight_trend.lost_weight }}kg</span> per month</div>
                    <div class="p-col-5">Current Fat-Loss Trend: </div>
                    <div class="p-col-7"><span v-bind:class="{'bad': current_weight_trend.lost_fat > 0, 'good': current_weight_trend.lost_fat <= 0}">{{ current_weight_trend.lost_fat > 0 ? '+' : '' }}{{ current_weight_trend.lost_fat }}kg</span> per month</div>
                    <div class="p-col-5">Current Muscle-Gain Trend: </div>
                    <div class="p-col-7"><span v-bind:class="{'good': current_weight_trend.lost_muscle >= 0, 'bad': current_weight_trend.lost_muscle < 0}">{{ current_weight_trend.lost_muscle > 0 ? '+' : '' }}{{ current_weight_trend.lost_muscle }}kg</span> per month</div>
                    <div class="p-col-5">Strike: </div>
                    <div class="p-col-7">{{ current_weight_strike }} days below {{ last_weight.range() }} kg</div>
                    <div class="p-col-5">Fat Strike: </div>
                    <div class="p-col-7">{{ current_fat_percentage_strike }} days at or below {{ last_weight.fat_percentage_threshold() }}%</div>
                    <div class="p-col-5">Next Goal: </div>
                    <div class="p-col-7">{{ months_next_range }} months for {{ last_weight.next_range() }} kg</div>
                  </div>
                </Panel>
              </div>
              <div class="p-col-12">
                <Panel>
                  <template #header>
                    <div class="table-header">
                      <strong>Last Pressure</strong>
                      <CreateBloodPressure :initial_date="daily_status.date" :blood_pressure="get_blood_pressure_for(daily_status.date)" fixed_date @onSave="load_all" />
                    </div>
                  </template>
                  <div class="p-grid" v-if="last_blood_pressure && current_blood_pressure_trend" >
                    <div class="p-col-5">Date: </div>
                    <div class="p-col-7">{{ last_blood_pressure.dateFormat }}</div>
                    <div class="p-col-5">Status: </div>
                    <div class="p-col-7" :style="{color: last_blood_pressure.stage().color}">{{ last_blood_pressure.stage().name }}</div>
                    <div class="p-col-5">Upper: </div>
                    <div class="p-col-7">{{ last_blood_pressure.upper }} mm Hg <span class="extra_info" v-bind:class="{'bad': last_blood_pressure.lost_upper > 0, 'good': last_blood_pressure.lost_upper <= 0}">{{ last_blood_pressure.lost_upper >= 0 ? '+' : '' }}{{ last_blood_pressure.lost_upper }} mm Hg</span></div>
                    <div class="p-col-5">Lower: </div>
                    <div class="p-col-7">{{ last_blood_pressure.lower }} mm Hg <span class="extra_info" v-bind:class="{'bad': last_blood_pressure.lost_lower > 0, 'good': last_blood_pressure.lost_lower <= 0}">{{ last_blood_pressure.lost_lower >= 0 ? '+' : '' }}{{ last_blood_pressure.lost_lower }} mm Hg</span></div>
                    <div class="p-col-5">Current Status Trend: </div>
                    <div class="p-col-7" :style="{color: current_blood_pressure_trend.stage().color}">{{ current_blood_pressure_trend.stage().name }}</div>
                    <div class="p-col-5">Current Upper Trend: </div>
                    <div class="p-col-7">
                      {{ current_blood_pressure_trend.upper }} mm Hg
                      <span class="extra_info" v-bind:class="{'bad': current_blood_pressure_trend.lost_upper > 0, 'good': current_blood_pressure_trend.lost_upper <= 0}">
                        {{ current_blood_pressure_trend.lost_upper >= 0 ? '+' : '' }}{{ current_blood_pressure_trend.lost_upper }}
                      </span> per month
                    </div>
                    <div class="p-col-5">Current Lower Trend: </div>
                    <div class="p-col-7">
                      {{ current_blood_pressure_trend.lower }} mm Hg
                      <span class="extra_info" v-bind:class="{'bad': current_blood_pressure_trend.lost_lower > 0, 'good': current_blood_pressure_trend.lost_lower <= 0}">
                        {{ current_blood_pressure_trend.lost_lower >= 0 ? '+' : '' }}{{ current_blood_pressure_trend.lost_lower }}
                      </span> per month
                    </div>
                  </div>
                </Panel>
              </div>
              <div class="p-col-12">
                <Panel>
                  <template #header>
                    <div class="table-header">
                      <strong>Latest Lipid Panel</strong>
                      <CreateLipidPanel @onSave="load_all" />
                    </div>
                  </template>
                  <div class="p-grid" v-if="last_lipid_panel">
                    <div class="p-col-5">Date: </div>
                    <div class="p-col-7">{{ last_lipid_panel.dateFormat }}</div>
                    <div class="p-col-5">Total Cholesterol: </div>
                    <div class="p-col-7">{{ last_lipid_panel.totalCholesterol }} mg/dL <span :class="last_lipid_panel.metricStatus('totalCholesterol', state.user.profile.sex).className">{{ last_lipid_panel.metricStatus('totalCholesterol', state.user.profile.sex).label }}</span> <span class="extra_info" :class="last_lipid_panel.changeClass('totalCholesterol', last_lipid_panel.totalChange)">{{ last_lipid_panel.formatChange(last_lipid_panel.totalChange) }}</span></div>
                    <div class="p-col-5">HDL Cholesterol: </div>
                    <div class="p-col-7">{{ last_lipid_panel.hdlCholesterol }} mg/dL <span :class="last_lipid_panel.metricStatus('hdlCholesterol', state.user.profile.sex).className">{{ last_lipid_panel.metricStatus('hdlCholesterol', state.user.profile.sex).label }}</span> <span class="extra_info" :class="last_lipid_panel.changeClass('hdlCholesterol', last_lipid_panel.hdlChange)">{{ last_lipid_panel.formatChange(last_lipid_panel.hdlChange) }}</span></div>
                    <div class="p-col-5">LDL Cholesterol: </div>
                    <div class="p-col-7">{{ last_lipid_panel.ldlCholesterol }} mg/dL <span :class="last_lipid_panel.metricStatus('ldlCholesterol', state.user.profile.sex).className">{{ last_lipid_panel.metricStatus('ldlCholesterol', state.user.profile.sex).label }}</span> <span class="extra_info" :class="last_lipid_panel.changeClass('ldlCholesterol', last_lipid_panel.ldlChange)">{{ last_lipid_panel.formatChange(last_lipid_panel.ldlChange) }}</span></div>
                    <div class="p-col-5">Triglycerides: </div>
                    <div class="p-col-7">{{ last_lipid_panel.triglycerides }} mg/dL <span :class="last_lipid_panel.metricStatus('triglycerides', state.user.profile.sex).className">{{ last_lipid_panel.metricStatus('triglycerides', state.user.profile.sex).label }}</span> <span class="extra_info" :class="last_lipid_panel.changeClass('triglycerides', last_lipid_panel.triglyceridesChange)">{{ last_lipid_panel.formatChange(last_lipid_panel.triglyceridesChange) }}</span></div>
                  </div>
                  <div v-else>No lipid panels yet.</div>
                </Panel>
              </div>
            </div>
          </TabPanel>
          <TabPanel>
            <template #header>
              <span>Back</span>
            </template>
            <Panel>
              <template #header>
                <div class="table-header">
                  <strong>Back Pain</strong>
                  <CreateBackPainEpisode :initial_date="daily_status.date" fixed_date @onSave="load_all" />
                </div>
              </template>
              <div class="back-pain-summary">
                <div class="back-pain-summary-card">
                  <span class="back-pain-summary-label">Selected Day</span>
                  <span :class="get_back_pain_severity_class(get_worst_back_pain_for(daily_status.date))">{{ format_back_pain_severity(get_worst_back_pain_for(daily_status.date)) }}</span>
                </div>
                <div class="back-pain-summary-card">
                  <span class="back-pain-summary-label">Last Week</span>
                  <span :class="get_back_pain_severity_class(get_worst_back_pain_for(last_week_daily_status.date))">{{ format_back_pain_severity(get_worst_back_pain_for(last_week_daily_status.date)) }}</span>
                </div>
                <div class="back-pain-summary-card">
                  <span class="back-pain-summary-label">Change</span>
                  <span :class="get_back_pain_change_class(get_back_pain_change())">{{ get_back_pain_change() }}</span>
                </div>
                <div class="back-pain-summary-card">
                  <span class="back-pain-summary-label">30-Day Worst</span>
                  <span :class="get_back_pain_severity_class(get_back_pain_rolling_worst())">{{ format_back_pain_severity(get_back_pain_rolling_worst()) }}</span>
                </div>
              </div>
              <DataTable :value="get_back_pain_episodes_for(daily_status.date)" responsiveLayout="scroll" class="back-pain-episodes">
                <template #empty>No pain recorded.</template>
                <Column header="Period" headerStyle="width: 140px">
                  <template #body="episode">{{ format_back_pain_period(episode.data.period) }}</template>
                </Column>
                <Column header="Location" headerStyle="min-width: 180px">
                  <template #body="episode">{{ format_back_pain_location(episode.data) }}</template>
                </Column>
                <Column header="Severity" headerStyle="min-width: 140px">
                  <template #body="episode"><span :class="get_back_pain_severity_class(episode.data.severity)">{{ format_back_pain_severity(episode.data.severity) }}</span></template>
                </Column>
                <Column header="Note" headerStyle="min-width: 180px">
                  <template #body="episode">{{ episode.data.note || 'No note' }}</template>
                </Column>
                <Column headerStyle="width: 180px">
                  <template #body="episode">
                    <div class="back-pain-actions">
                      <CreateBackPainEpisode :initial_date="daily_status.date" :episode="episode.data" fixed_date @onSave="load_all" />
                      <Button label="Delete" icon="pi pi-trash" class="p-button-warning" @click="remove_back_pain_episode(episode.data)" />
                    </div>
                  </template>
                </Column>
              </DataTable>
            </Panel>
          </TabPanel>
          <TabPanel>
            <template #header>
              <span class="daily-entry-tab-header">
                <span>Sleep</span>
                <i v-if="is_sleep_entry_missing()" class="pi pi-exclamation-circle missing-daily-entry-icon" role="img" title="Missing entry for selected date" aria-label="Missing entry for selected date" />
              </span>
            </template>
            <Panel>
              <template #header>
                <div class="table-header">
                  <strong>Sleep</strong>
                  <CreateSleep :initial_date="daily_status.date" :sleep="get_sleep_for(daily_status.date)" fixed_date @onSave="load_all" />
                </div>
              </template>
              <div class="p-grid">
                <div class="p-col-5">30-Day Status: </div>
                <div class="p-col-7">
                  <span v-if="this.current_sleep_status" :class="this.current_sleep_status.className">{{ this.current_sleep_status.name }} ({{ this.current_sleep_status.score }}/4)</span>
                  <span v-else>Not enough data ({{ this.current_sleep_status_entry_count }}/{{ this.sleep_status_window }})</span>
                </div>
                <div class="p-col-5">Today Sleep: </div>
                <div class="p-col-7">
                  <span>{{ this.format_daily_sleep(this.get_sleep_for(this.daily_status.date)) }}</span>
                  &nbsp;<span v-if="this.get_sleep_duration_difference(this.get_sleep_for(this.daily_status.date), this.get_sleep_for(this.last_week_daily_status.date)) !== null && this.get_sleep_duration_difference(this.get_sleep_for(this.daily_status.date), this.get_sleep_for(this.last_week_daily_status.date)) !== 0" :class="this.get_difference_class(this.get_sleep_duration_difference(this.get_sleep_for(this.daily_status.date), this.get_sleep_for(this.last_week_daily_status.date)))">{{ this.format_sleep_trend(this.get_sleep_duration_difference(this.get_sleep_for(this.daily_status.date), this.get_sleep_for(this.last_week_daily_status.date))) }}</span>
                </div>
                <div class="p-col-5">Sleep (30-Day Average): </div>
                <div class="p-col-7">
                  <span v-if="this.current_sleep_trend">
                    <span>{{ this.format_sleep_duration(this.current_sleep_trend.totalSleepDuration) }}</span>
                    <span :class="this.get_sleep_trend_class(this.current_sleep_trend.lostTotalSleepDuration)">&nbsp;{{ this.format_sleep_trend(this.current_sleep_trend.lostTotalSleepDuration) }}</span>
                  </span>
                  <span v-else>Not enough data</span>
                </div>
                <div class="p-col-5">Today Average Heart Rate: </div>
                <div class="p-col-7">
                  <span>{{ this.get_sleep_for(this.daily_status.date) ? this.get_sleep_for(this.daily_status.date).heartRateFormat() : 'Not recorded' }}</span>
                </div>
                <div class="p-col-5">Heart Rate (30-Day Average): </div>
                <div class="p-col-7">
                  <span v-if="this.current_sleep_trend">
                    <span>{{ this.current_sleep_trend.averageHeartRate }} bpm</span>
                    <span :class="this.get_heart_rate_trend_class(this.current_sleep_trend.lostAverageHeartRate)">&nbsp;{{ this.format_sleep_metric_trend(this.current_sleep_trend.lostAverageHeartRate, 'bpm') }}</span>
                  </span>
                  <span v-else>Not enough data</span>
                </div>
                <div class="p-col-5">Today HRV: </div>
                <div class="p-col-7">
                  <span>{{ this.get_sleep_for(this.daily_status.date) ? this.get_sleep_for(this.daily_status.date).hrvFormat() : 'Not recorded' }}</span>
                </div>
                <div class="p-col-5">HRV (30-Day Average): </div>
                <div class="p-col-7">
                  <span v-if="this.current_sleep_trend">
                    <span>{{ this.current_sleep_trend.averageHrv }} ms</span>
                    <span :class="this.get_hrv_trend_class(this.current_sleep_trend.lostAverageHrv)">&nbsp;{{ this.format_sleep_metric_trend(this.current_sleep_trend.lostAverageHrv, 'ms') }}</span>
                  </span>
                  <span v-else>Not enough data</span>
                </div>
                <template v-if="last_sleep">
                  <div class="p-col-5">Bedtime: </div>
                  <div class="p-col-7">{{ last_sleep.bedtimeWindowFormat() }}</div>
                  <div class="p-col-5">Deep / REM / Light: </div>
                  <div class="p-col-7">{{ last_sleep.deepSleepDurationFormat() }} / {{ last_sleep.remSleepDurationFormat() }} / {{ last_sleep.lightSleepDurationFormat() }}</div>
                  <div class="p-col-5">Awake: </div>
                  <div class="p-col-7">{{ last_sleep.awakeTimeFormat() }}</div>
                </template>
              </div>
            </Panel>
          </TabPanel>
          <TabPanel>
            <template #header>
              <span class="daily-entry-tab-header">
                <span>Mood</span>
                <i v-if="is_mood_entry_missing()" class="pi pi-exclamation-circle missing-daily-entry-icon" role="img" title="Missing entry for selected date" aria-label="Missing entry for selected date" />
              </span>
            </template>
            <Panel>
              <template #header>
                <div class="table-header">
                  <strong>Mood</strong>
                  <CreateMood :initial_date="daily_status.date" fixed_date @onSave="load_all" />
                </div>
              </template>
              <div class="p-grid">
                <div class="p-col-5">Daily Mood: </div>
                <div class="p-col-7">
                  <span :class="this.get_mood_color(this.daily_status.mood.average)">{{ this.format_mood_average(this.daily_status.mood.average) }}</span>
                </div>
                <div class="p-col-5">Previous Week Mood: </div>
                <div class="p-col-7">
                  <span :class="this.get_mood_color(this.last_week_daily_status.mood.average)">{{ this.format_mood_average(this.last_week_daily_status.mood.average) }}</span>
                </div>
                <div class="p-col-5">Mood (30-Day Average): </div>
                <div class="p-col-7">
                  <span :class="this.get_mood_color(this.get_mood_trend_color_value(this.daily_status.mood_trend))">{{ this.format_mood_average(this.daily_status.mood_trend) }}</span>
                </div>
                <div class="p-col-5">Morning Mood: </div>
                <div class="p-col-7">
                  <span :class="this.get_mood_color(this.daily_status.mood.morning?.value)">{{ this.format_daily_mood(this.daily_status.mood.morning) }}</span>
                </div>
                <div class="p-col-5">Midday Mood: </div>
                <div class="p-col-7">
                  <span :class="this.get_mood_color(this.daily_status.mood.midday?.value)">{{ this.format_daily_mood(this.daily_status.mood.midday) }}</span>
                </div>
                <div class="p-col-5">Evening Mood: </div>
                <div class="p-col-7">
                  <span :class="this.get_mood_color(this.daily_status.mood.evening?.value)">{{ this.format_daily_mood(this.daily_status.mood.evening) }}</span>
                </div>
                <div class="p-col-5">Last Entry Date: </div>
                <div class="p-col-7">{{ previous_mood ? previous_mood.dateFormat : 'Not recorded' }}</div>
                <div class="p-col-5">Last Entry Period: </div>
                <div class="p-col-7">{{ previous_mood ? previous_mood.periodLabel() : 'Not recorded' }}</div>
                <div class="p-col-5">Last Entry Mood: </div>
                <div class="p-col-7">
                  <span :class="this.get_mood_color(this.previous_mood?.value)">{{ this.format_daily_mood(this.previous_mood) }}</span>
                </div>
              </div>
            </Panel>
          </TabPanel>
          <TabPanel>
            <template #header>
              <span class="daily-entry-tab-header">
                <span>Calories</span>
                <i v-if="is_calorie_entry_missing()" class="pi pi-exclamation-circle missing-daily-entry-icon" role="img" title="Missing entry for selected date" aria-label="Missing entry for selected date" />
              </span>
            </template>
            <Panel>
              <template #header>
                <div class="table-header">
                  <strong>Meals</strong>
                  <CreateMeal :initial_date="daily_status.date" :meals="meals" fixed_date @onSave="load_all" />
                </div>
              </template>
              <div class="meal-list">
                <div v-if="get_meals_for(daily_status.date).length === 0" class="meal-list-empty">No meals recorded.</div>
                <div v-for="meal in get_meals_for(daily_status.date)" :key="meal.id" class="meal-entry">
                  <div class="meal-entry-main">
                    <div class="meal-entry-summary">
                      <strong>{{ meal.label() }}</strong>
                      <span>{{ meal.calories }} kcal</span>
                    </div>
                    <div class="meal-entry-actions">
                      <CreateMeal :initial_date="daily_status.date" :meal="meal" :meals="meals" fixed_date icon_only @onSave="load_all" />
                      <Button icon="pi pi-trash" aria-label="Delete" class="p-button-rounded p-button-sm p-button-warning" @click="remove_meal(meal)" />
                    </div>
                  </div>
                  <span v-if="meal.macroSummary()" class="meal-entry-macros">{{ meal.macroSummary() }}</span>
                </div>
              </div>
              <div class="meal-total">
                <strong>Total:</strong>
                <span>{{ get_meal_calories_total(daily_status.date) }} kcal</span>
              </div>
              <div class="p-grid">
                <div class="p-col-5">Previous Week Calories: </div>
                <div class="p-col-7">{{ this.format_daily_calories(this.get_calorie_for(this.last_week_daily_status.date)) }}</div>
                <div class="p-col-5">Calories (30-Day Average): </div>
                <div class="p-col-7">
                  <span v-if="this.current_calorie_trend">
                    <span>{{ this.current_calorie_trend.calories }} kcal</span>
                    <span :class="this.get_calorie_trend_class(this.current_calorie_trend.lostCalories)">&nbsp;{{ this.format_calorie_trend(this.current_calorie_trend.lostCalories) }}</span>
                  </span>
                  <span v-else>Not enough data</span>
                </div>
                <div class="p-col-5">{{ this.weekly_calorie_maximum_status.label }}: </div>
                <div class="p-col-7"><span :class="this.weekly_calorie_maximum_status.className">{{ this.weekly_calorie_maximum_status.calories }} kcal</span></div>
                <div class="p-col-5">Last Entry Date: </div>
                <div class="p-col-7">{{ previous_calorie ? previous_calorie.dateFormat : 'Not recorded' }}</div>
                <div class="p-col-5">Last Entry Calories: </div>
                <div class="p-col-7">{{ previous_calorie ? `${previous_calorie.calories} kcal` : 'Not recorded' }}</div>
              </div>
            </Panel>
          </TabPanel>
          <TabPanel>
            <template #header>
              <span class="daily-entry-tab-header">
                <span>Workout</span>
                <i v-if="is_workout_entry_missing()" class="pi pi-exclamation-circle missing-daily-entry-icon" role="img" title="Missing entry for selected date" aria-label="Missing entry for selected date" />
              </span>
            </template>
            <Panel>
              <template #header>
                <div class="table-header">
                  <strong>Workout</strong>
                  <CreateWorkout :initial_date="daily_status.date" :workout="current_workout" :workouts="workouts" fixed_date @onSave="refresh_workout_status" />
                </div>
              </template>
              <div class="workout-comparison">
                <div class="workout-card">
                  <div class="workout-card-title">Today Workout</div>
                  <div v-if="current_workout" class="p-grid">
                    <div class="p-col-5">Date: </div>
                    <div class="p-col-7">{{ current_workout.workoutDateFormat }}</div>
                    <div class="p-col-5">Note: </div>
                    <div class="p-col-7">{{ current_workout.note || 'No note' }}</div>
                    <div class="p-col-12 workout-line-list">
                      <div v-for="(line, index) in get_workout_lines(current_workout)" :key="`current-${index}`" class="workout-line-item">
                        <div class="workout-line-title">{{ line.exerciseName }}</div>
                        <div v-if="line.trackingMode === 'REPS'">
                          <div v-for="(set, setIndex) in line.sets" :key="`current-reps-${index}-${setIndex}`" class="workout-line-detail">{{ format_workout_reps_set(set) }}</div>
                        </div>
                        <div v-else-if="line.trackingMode === 'SECONDS'">
                          <div v-for="(set, setIndex) in line.sets" :key="`current-seconds-${index}-${setIndex}`" class="workout-line-detail">{{ format_workout_seconds_set(set) }}</div>
                        </div>
                        <div v-else>
                          <div v-for="(interval, intervalIndex) in line.intervals" :key="`current-cardio-${index}-${intervalIndex}`" class="workout-line-detail">{{ format_workout_cardio_interval(interval) }}</div>
                          <div v-if="format_workout_line_footer(line)" class="workout-line-footer">{{ format_workout_line_footer(line) }}</div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div v-else>No workout recorded for today.</div>
                </div>
                <div class="workout-card">
                  <div class="workout-card-title">Previous Week Workout</div>
                  <div v-if="previous_week_workout" class="p-grid">
                    <div class="p-col-5">Date: </div>
                    <div class="p-col-7">{{ previous_week_workout.workoutDateFormat }}</div>
                    <div class="p-col-5">Note: </div>
                    <div class="p-col-7">{{ previous_week_workout.note || 'No note' }}</div>
                    <div class="p-col-12 workout-line-list">
                      <div v-for="(line, index) in get_workout_lines(previous_week_workout)" :key="`previous-${index}`" class="workout-line-item">
                        <div class="workout-line-title">{{ line.exerciseName }}</div>
                        <div v-if="line.trackingMode === 'REPS'">
                          <div v-for="(set, setIndex) in line.sets" :key="`previous-reps-${index}-${setIndex}`" class="workout-line-detail">{{ format_workout_reps_set(set) }}</div>
                        </div>
                        <div v-else-if="line.trackingMode === 'SECONDS'">
                          <div v-for="(set, setIndex) in line.sets" :key="`previous-seconds-${index}-${setIndex}`" class="workout-line-detail">{{ format_workout_seconds_set(set) }}</div>
                        </div>
                        <div v-else>
                          <div v-for="(interval, intervalIndex) in line.intervals" :key="`previous-cardio-${index}-${intervalIndex}`" class="workout-line-detail">{{ format_workout_cardio_interval(interval) }}</div>
                          <div v-if="format_workout_line_footer(line)" class="workout-line-footer">{{ format_workout_line_footer(line) }}</div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div v-else>No workout recorded for the same day last week.</div>
                </div>
              </div>
            </Panel>
          </TabPanel>
          <TabPanel header="Wins">
            <Panel class="p-panel-content-without-padding">
              <template #header>
                <div class="table-header wins-and-misses-header">
                  <strong>Wins</strong>
                  <div class="tab-panel-actions">
                    <Button label="WIN" icon="pi pi-check" class="p-button-success decision-outcome-button" @click="record_decision_outcome('WIN')" :disabled="decision_outcome_loading" :loading="decision_outcome_loading && pending_decision_outcome === 'WIN'" />
                    <Button label="MISS" icon="pi pi-times" class="p-button-danger decision-outcome-button" @click="record_decision_outcome('MISS')" :disabled="decision_outcome_loading" :loading="decision_outcome_loading && pending_decision_outcome === 'MISS'" />
                  </div>
                </div>
              </template>
              <div class="p-grid wins-and-misses-metrics">
                <div class="p-col-5">Selected Date: </div>
                <div class="p-col-7">{{ format_outcome_metrics(wins_and_misses_status.selectedDate) }}</div>
                <div class="p-col-5">Rolling 30 Days: </div>
                <div class="p-col-7">{{ format_outcome_metrics(wins_and_misses_status.rolling30Days) }}</div>
                <div class="p-col-5">Previous 30 Days: </div>
                <div class="p-col-7">{{ format_outcome_metrics(wins_and_misses_status.previous30Days) }}</div>
                <div class="p-col-5">30-Day Change: </div>
                <div class="p-col-7"><span :class="get_win_rate_change_class()">{{ format_win_rate_change() }}</span></div>
                <div class="p-col-5">All Time: </div>
                <div class="p-col-7">{{ format_outcome_metrics(wins_and_misses_status.allTime) }}</div>
                <div class="p-col-5">Current WIN Streak: </div>
                <div class="p-col-7">{{ wins_and_misses_status.currentWinStreak }}</div>
              </div>
            </Panel>
          </TabPanel>
        </ScrollableTabView>
      </div>
    </div>
    <div class="p-grid p-mt-1" v-if="weight_chart_data || sleep_total_chart_data || calorie_chart_data || routines_chart_data || mood_chart_data || lipid_panels.length > 0" >
      <div class="p-col-4 p-text-right">
        <RadioButton inputId="chart_type_monthly" name="chart_type" value="monthly" v-model="chart_type" @change="load_chart_data" />
        <label for="chart_type_monthly" class="p-ml-1">Monthly</label>
      </div>
      <div class="p-col-4 p-text-center">
        <RadioButton inputId="chart_type_year" name="chart_type" value="last_year" v-model="chart_type" @change="load_chart_data" />
        <label for="chart_type_year" class="p-ml-1">Year</label>
      </div>
      <div class="p-col-4 p-text-left">
        <RadioButton inputId="chart_type_all" name="chart_type" value="all" v-model="chart_type" @change="load_chart_data" />
        <label for="chart_type_all" class="p-ml-1">All</label>
      </div>
      <div id="measures-chart" class="center">
        <TabView>
          <TabPanel header="Measures">
            <div v-if="weight_chart_data">
              <Chart type="line" :data="weight_chart_data.data" :options="weight_chart_data.options" :height="175" />
              <Chart type="line" :data="fat_chart_data.data" :options="fat_chart_data.options" :height="175" />
              <Chart type="line" :data="muscle_chart_data.data" :options="muscle_chart_data.options" :height="175" />
              <Chart type="line" :data="upper_pressure_chart_data.data" :options="upper_pressure_chart_data.options" :height="175" />
              <Chart type="line" :data="lower_pressure_chart_data.data" :options="lower_pressure_chart_data.options" :height="175" />
            </div>
            <div v-else>No weight or pressure data yet.</div>
          </TabPanel>
          <TabPanel header="Cholesterol">
            <div v-if="total_cholesterol_chart_data">
              <Chart type="line" :data="total_cholesterol_chart_data.data" :options="total_cholesterol_chart_data.options" :height="175" />
              <Chart type="line" :data="hdl_cholesterol_chart_data.data" :options="hdl_cholesterol_chart_data.options" :height="175" />
              <Chart type="line" :data="ldl_cholesterol_chart_data.data" :options="ldl_cholesterol_chart_data.options" :height="175" />
              <Chart type="line" :data="triglycerides_chart_data.data" :options="triglycerides_chart_data.options" :height="175" />
            </div>
            <div v-else>No lipid panel data in the selected period.</div>
          </TabPanel>
          <TabPanel header="Lost">
            <div v-if="weight_lost_chart_data">
              <Chart type="line" :data="weight_lost_chart_data.data" :options="weight_lost_chart_data.options" :height="175" />
              <Chart type="line" :data="fat_lost_chart_data.data" :options="fat_lost_chart_data.options" :height="175" />
              <Chart type="line" :data="muscle_lost_chart_data.data" :options="muscle_lost_chart_data.options" :height="175" />
              <Chart type="line" :data="upper_pressure_lost_chart_data.data" :options="upper_pressure_lost_chart_data.options" :height="175" />
              <Chart type="line" :data="lower_pressure_lost_chart_data.data" :options="lower_pressure_lost_chart_data.options" :height="175" />
            </div>
            <div v-else>No weight or pressure trend data yet.</div>
          </TabPanel>
          <TabPanel header="Routines">
            <div v-if="routines_chart_data">
              <Chart type="line" :data="routines_chart_data.data" :options="routines_chart_data.options" :height="175" />
              <div class="p-mb-3">
                <Dropdown
                    v-model="selected_routine_chart_id"
                    :options="get_routine_chart_options()"
                    optionLabel="label"
                    optionValue="id"
                    placeholder="Select routine"
                    filter
                    class="w-full"
                    @change="load_chart_data"
                />
              </div>
              <Chart v-if="selected_routine_chart_data" type="line" :data="selected_routine_chart_data.data" :options="selected_routine_chart_data.options" :height="175" />
            </div>
            <div v-else>No routine data yet.</div>
          </TabPanel>
          <TabPanel header="Sleep">
            <div v-if="sleep_total_chart_data">
              <Chart type="line" :data="sleep_total_chart_data.data" :options="sleep_total_chart_data.options" :height="175" />
              <Chart type="line" :data="sleep_deep_chart_data.data" :options="sleep_deep_chart_data.options" :height="175" />
              <Chart type="line" :data="sleep_rem_chart_data.data" :options="sleep_rem_chart_data.options" :height="175" />
              <Chart type="line" :data="sleep_light_chart_data.data" :options="sleep_light_chart_data.options" :height="175" />
              <Chart type="line" :data="sleep_awake_chart_data.data" :options="sleep_awake_chart_data.options" :height="175" />
              <Chart type="line" :data="sleep_heart_rate_chart_data.data" :options="sleep_heart_rate_chart_data.options" :height="175" />
              <Chart type="line" :data="sleep_hrv_chart_data.data" :options="sleep_hrv_chart_data.options" :height="175" />
              <Chart type="line" :data="sleep_bedtime_start_chart_data.data" :options="sleep_bedtime_start_chart_data.options" :height="175" />
              <Chart type="line" :data="sleep_bedtime_end_chart_data.data" :options="sleep_bedtime_end_chart_data.options" :height="175" />
            </div>
            <div v-else>No sleep data yet.</div>
          </TabPanel>
          <TabPanel header="Mood">
            <div v-if="mood_chart_data">
              <Chart type="line" :data="mood_chart_data.data" :options="mood_chart_data.options" :height="175" />
            </div>
            <div v-else>No mood data yet.</div>
          </TabPanel>
          <TabPanel header="Calories">
            <div v-if="calorie_chart_data">
              <Chart type="line" :data="calorie_chart_data.data" :options="calorie_chart_data.options" :height="175" />
            </div>
            <div v-else>No calorie data yet.</div>
          </TabPanel>
        </TabView>
      </div>
    </div>
  </div>
</template>

<script>
import {nextTick} from 'vue';
import {userState} from '../state';
import {BMIStatus, WeightStatus} from "@/model/Weight";
import routineService from '../services/RoutineService';
import weightService from '../services/WeightService';
import summaryService, {TREND_WINDOW_DAYS} from '../services/MeasuresSummaryService';
import dashboardService from '../services/DashboardService';
import bloodPressureService from '../services/BloodPressureService';
import moodService from '../services/MoodService';
import sleepService from '../services/SleepService';
import calorieService from '../services/CalorieService';
import mealService from '../services/MealService';
import workoutService from '../services/WorkoutService';
import decisionOutcomeService from '../services/DecisionOutcomeService';
import reflectionService from '../services/ReflectionService';
import backPainEpisodeService from '../services/BackPainEpisodeService';
import inAppNotificationService from '../services/InAppNotificationService';
import lipidPanelService from '../services/LipidPanelService';
import CreateWeight from "@/components/CreateWeight";
import CreateBloodPressure from "@/components/CreateBloodPressure";
import CreateSleep from "@/components/CreateSleep";
import CreateMeal from "@/components/CreateMeal";
import CreateWorkout from "@/components/CreateWorkout";
import CreateMood from "@/components/CreateMood";
import CreateBackPainEpisode from "@/components/CreateBackPainEpisode";
import CreateLipidPanel from "@/components/CreateLipidPanel";
import MoodForm from "@/components/MoodForm";
import BackPainEpisodeForm from "@/components/BackPainEpisodeForm";
import WeightForm from "@/components/WeightForm";
import BloodPressureForm from "@/components/BloodPressureForm";
import WinCelebration from "@/components/WinCelebration";
import PushNotificationPrompt from "@/components/PushNotificationPrompt";
import ScrollableTabView from "@/components/ScrollableTabView";
import dayjs from 'dayjs';
import anychart from 'anychart/dist/js/anychart-base.min'
import anychartLinearGauge from 'anychart/dist/js/anychart-linear-gauge.min'
import {formatDuration, formatTimeOfDayFromMinutes, getSleepStatus} from "@/model/Sleep";
import {getMoodOption, getMoodPeriodOption, getMoodPeriodOrder} from "@/model/Mood";
import {
  getCalorieMetricColor,
  getTypicalCaloriesForDate,
  getHeartRateMetricColor,
  getHrvMetricColor,
  getSleepMetricColor
} from "@/model/WeekMetricThresholds";
import {buildReflectionAdvicePrompt, buildReflectionPrompt} from "@/model/Reflection";
import {formatBackPainLocation, formatBackPainPeriod, formatBackPainSeverity, getBackPainSeverityOption, getBackPainSeverityRank} from "@/model/BackPainEpisode";

const isToday = require('dayjs/plugin/isToday');
dayjs.extend(isToday)

const madridDateFormatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Europe/Madrid',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit'
});

function madrid_date(value) {
  const parts = Object.fromEntries(madridDateFormatter.formatToParts(value).map(part => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

export default {
  components: {CreateWeight, CreateBloodPressure, CreateSleep, CreateMeal, CreateWorkout, CreateMood, CreateBackPainEpisode, CreateLipidPanel, MoodForm, BackPainEpisodeForm, WeightForm, BloodPressureForm, WinCelebration, PushNotificationPrompt, ScrollableTabView},
  data() {
    return {
      routines: [],
      moods: [],
      weights: [],
      blood_pressures: [],
      sleeps: [],
      calories: [],
      meals: [],
      workouts: [],
      back_pain_episodes: [],
      lipid_panels: [],
      daily_status: undefined,
      week_status: undefined,
      week_ago_status: undefined,
      last_week_daily_status: undefined,
      wins_and_misses_status: undefined,
      last_weight: undefined,
      last_blood_pressure: undefined,
      last_lipid_panel: undefined,
      last_sleep: undefined,
      current_workout: undefined,
      previous_week_workout: undefined,
      current_blood_pressure_trend: undefined,
      current_weight_trend: undefined,
      current_sleep_trend: undefined,
      current_sleep_status: undefined,
      current_sleep_status_entry_count: 0,
      current_calorie_trend: undefined,
      current_weight_strike: undefined,
      current_fat_percentage_strike: undefined,
      months_next_range: undefined,
      chart_type: "monthly",
      routines_chart_data: undefined,
      selected_routine_chart_id: undefined,
      selected_routine_chart_data: undefined,
      weight_chart_data: undefined,
      fat_chart_data: undefined,
      muscle_chart_data: undefined,
      weight_lost_chart_data: undefined,
      upper_pressure_chart_data: undefined,
      lower_pressure_chart_data: undefined,
      fat_lost_chart_data: undefined,
      muscle_lost_chart_data: undefined,
      upper_pressure_lost_chart_data: undefined,
      lower_pressure_lost_chart_data: undefined,
      sleep_total_chart_data: undefined,
      sleep_deep_chart_data: undefined,
      sleep_rem_chart_data: undefined,
      sleep_light_chart_data: undefined,
      sleep_awake_chart_data: undefined,
      sleep_heart_rate_chart_data: undefined,
      sleep_hrv_chart_data: undefined,
      sleep_bedtime_start_chart_data: undefined,
      sleep_bedtime_end_chart_data: undefined,
      mood_chart_data: undefined,
      calorie_chart_data: undefined,
      total_cholesterol_chart_data: undefined,
      hdl_cholesterol_chart_data: undefined,
      ldl_cholesterol_chart_data: undefined,
      triglycerides_chart_data: undefined,
      fat_status_bar: undefined,
      bmi_status_bar: undefined,
      day_navigation_loading: false,
      dashboard_completion_loading: false,
      routine_action_loading_id: null,
      routine_reminder: null,
      routine_reminder_schedule: null,
      routine_reminder_visible: false,
      routine_reminder_loading_action: null,
      routine_reminder_snooze_minutes: 15,
      routine_reminder_snooze_options: [
        {label: '15 minutes', value: 15},
        {label: '30 minutes', value: 30},
        {label: '1 hour', value: 60}
      ],
      check_in_reminder: null,
      check_in_reminder_visible: false,
      check_in_entry: null,
      check_in_mood_form_visible: false,
      check_in_back_form_visible: false,
      measurement_entry: null,
      measurement_weight_form_visible: false,
      measurement_blood_pressure_form_visible: false,
      decision_outcome_loading: false,
      pending_decision_outcome: null,
      last_completed_dashboard_date: null,
      reflection_overview: null,
      latest_reflection: null,
      chatgpt_url: process.env.VUE_APP_CHATGPT_REFLECTION_URL || 'https://chatgpt.com/gpts/mine',
      sleep_status_window: TREND_WINDOW_DAYS,
      state: userState()
    }
  },
  computed: {
    check_in_reminder_title() {
      if (!this.check_in_reminder) {
        return '';
      }
      const feature = this.check_in_reminder.type === 'mood' ? 'mood' : 'back';
      return `${getMoodPeriodOption(this.check_in_reminder.period).label} ${feature} reminder`;
    },
    check_in_reminder_message() {
      return this.check_in_reminder?.type === 'mood'
          ? `Record your ${getMoodPeriodOption(this.check_in_reminder.period).label.toLowerCase()} mood.`
          : 'Record a back pain episode if needed.';
    },
    dashboard_date_offset() {
      return dayjs(this.daily_status.date).startOf('day').diff(dayjs().startOf('day'), 'day');
    },
    dashboard_date_offset_label() {
      if (this.dashboard_date_offset === 0) {
        return 'Today';
      }
      return `${this.dashboard_date_offset} ${Math.abs(this.dashboard_date_offset) === 1 ? 'day' : 'days'}`;
    },
    dashboard_date_offset_class() {
      return this.dashboard_date_offset === 0 ? 'dashboard-date-offset-today' : 'dashboard-date-offset-behind';
    },
    previous_mood() {
      return this.moods
          .filter(mood => dayjs(mood.date).isBefore(this.daily_status.date, 'day'))
          .sort((left, right) => dayjs(right.date).diff(left.date) || getMoodPeriodOrder(right.period) - getMoodPeriodOrder(left.period))[0] || null;
    },
    previous_calorie() {
      return this.calories.find(calorie => dayjs(calorie.date).isBefore(this.daily_status.date, 'day')) || null;
    },
    weekly_calorie_maximum_status() {
      const difference = this.get_projected_week_calories_total() - this.state.user.profile.weeklyAverageCalorieMaximum * 7;
      if (difference > 0) {
        return {label: 'Weekly Calories Above Maximum', calories: difference, className: 'bad'};
      }
      if (difference < 0) {
        return {label: 'Weekly Calories Below Maximum', calories: Math.abs(difference), className: 'good'};
      }
      return {label: 'Weekly Calories at Maximum', calories: 0, className: 'normal'};
    }
  },
  watch: {
    async '$route.fullPath'() {
      if (this.daily_status) {
        await this.handle_route_actions();
      }
    }
  },
  async mounted() {
    this.state.loading = true;
    await this.load_all_routines();
    await this.open_routine_reminder();
    if (this.routine_reminder_visible) {
      await nextTick();
    }
    await this.load_remaining_dashboard_data();
    await nextTick();
    if (this.last_weight) {
      await this.init_fat_status_bar();
      await this.init_bmi_status_bar();
    }
    this.state.loading = false;
    await this.open_check_in_reminder();
    await this.open_measurement_reminder();
    await this.record_decision_outcome_shortcut();
  },
  methods: {
    async handle_route_actions() {
      await this.open_routine_reminder();
      await this.open_check_in_reminder();
      await this.open_measurement_reminder();
      await this.record_decision_outcome_shortcut();
    },
    async record_decision_outcome_shortcut() {
      const outcome = this.$route.query.decisionOutcome;
      if (!outcome) {
        return;
      }

      const query = {...this.$route.query};
      delete query.decisionOutcome;
      await this.$router.replace({query});
      await this.record_decision_outcome(outcome);
    },
    async open_check_in_reminder() {
      const type = this.$route.query.checkInReminder;
      const period = this.$route.query.checkInPeriod;
      const date = this.$route.query.checkInReminderDate;
      if (!type && !period && !date) {
        this.check_in_reminder = null;
        this.check_in_reminder_visible = false;
        return;
      }

      const validPeriods = ['MORNING', 'MIDDAY', 'EVENING'];
      const moodExists = type === 'mood' && this.moods.some(mood => madrid_date(mood.date) === date && mood.period === period);
      const backPainExists = type === 'back' && this.back_pain_episodes.some(episode => madrid_date(episode.date) === date && episode.period === period);
      if (!['mood', 'back'].includes(type) || !validPeriods.includes(period) || date !== madrid_date(new Date()) || moodExists || backPainExists) {
        await this.clear_check_in_reminder_query();
        return;
      }

      this.check_in_reminder = {type, period, date: new Date(`${date}T12:00:00`)};
      this.check_in_reminder_visible = true;
    },
    async record_check_in_reminder() {
      this.check_in_entry = this.check_in_reminder;
      this.check_in_reminder = null;
      this.check_in_reminder_visible = false;
      if (this.check_in_entry.type === 'mood') {
        this.check_in_mood_form_visible = true;
      } else {
        this.check_in_back_form_visible = true;
      }
      await this.clear_check_in_reminder_query();
    },
    async dismiss_check_in_reminder() {
      const notificationId = this.$route.query.notificationId;
      if (notificationId) {
        await inAppNotificationService.dismiss(notificationId);
      }
      this.check_in_reminder = null;
      this.check_in_reminder_visible = false;
      await this.clear_check_in_reminder_query();
    },
    async save_check_in_entry() {
      await this.load_all();
      this.close_check_in_entry();
    },
    close_check_in_entry() {
      this.check_in_mood_form_visible = false;
      this.check_in_back_form_visible = false;
      this.check_in_entry = null;
    },
    async clear_check_in_reminder_query() {
      const query = {...this.$route.query};
      delete query.checkInReminder;
      delete query.checkInPeriod;
      delete query.checkInReminderDate;
      delete query.notificationId;
      await this.$router.replace({query});
    },
    async open_measurement_reminder() {
      const type = this.$route.query.measurementReminder;
      const date = this.$route.query.measurementReminderDate;
      if (!type && !date) {
        return;
      }

      const reminderDate = new Date(`${date}T12:00:00`);
      const weightExists = type === 'weight' && this.weights.some(weight => madrid_date(weight.date) === date);
      const bloodPressureExists = type === 'blood-pressure' && this.blood_pressures.some(bloodPressure => madrid_date(bloodPressure.date) === date);
      if (!['weight', 'blood-pressure'].includes(type) || date !== madrid_date(new Date()) || reminderDate.getDay() !== 6 || weightExists || bloodPressureExists) {
        await this.close_measurement_entry();
        return;
      }

      this.measurement_entry = {type, date: reminderDate};
      this.measurement_weight_form_visible = type === 'weight';
      this.measurement_blood_pressure_form_visible = type === 'blood-pressure';
    },
    async save_measurement_entry() {
      await this.load_all();
    },
    async close_measurement_entry() {
      this.measurement_weight_form_visible = false;
      this.measurement_blood_pressure_form_visible = false;
      this.measurement_entry = null;
      if (this.$route.query.measurementReminder || this.$route.query.measurementReminderDate) {
        await this.clear_measurement_reminder_query();
      }
    },
    async clear_measurement_reminder_query() {
      const query = {...this.$route.query};
      delete query.measurementReminder;
      delete query.measurementReminderDate;
      delete query.notificationId;
      await this.$router.replace({query});
    },
    async open_routine_reminder() {
      const reminderId = this.$route.query.routineReminderId;
      const reminderScheduleId = this.$route.query.routineReminderScheduleId;
      const reminderDate = this.$route.query.routineReminderDate;
      if (!reminderId || !reminderScheduleId || !reminderDate) {
        this.routine_reminder = null;
        this.routine_reminder_schedule = null;
        this.routine_reminder_visible = false;
        return;
      }

      const routine = this.routines.find(candidate => String(candidate.id) === String(reminderId));
      const reminder = routine?.reminders.find(candidate => String(candidate.id) === String(reminderScheduleId));
      if (reminderDate !== madrid_date(new Date()) || !reminder || this.is_routine_done_on(routine, reminderDate)) {
        await this.clear_routine_reminder_query();
        return;
      }

      this.routine_reminder = routine;
      this.routine_reminder_schedule = reminder;
      this.routine_reminder_snooze_minutes = 15;
      this.routine_reminder_visible = true;
    },
    is_routine_done_on(routine, date) {
      return routine.times.some(checkin => madrid_date(checkin) === date);
    },
    format_routine_reminder_time(reminderTime) {
      return reminderTime.slice(0, 5);
    },
    async close_routine_reminder() {
      this.routine_reminder_visible = false;
      this.routine_reminder = null;
      this.routine_reminder_schedule = null;
      await this.clear_routine_reminder_query();
    },
    async snooze_routine_reminder() {
      this.routine_reminder_loading_action = 'snooze';
      try {
        const result = await routineService.snoozeReminder(
            this.routine_reminder.id,
            this.routine_reminder_schedule.id,
            this.routine_reminder_snooze_minutes
        );
        const duration = this.routine_reminder_snooze_options.find(option => option.value === this.routine_reminder_snooze_minutes).label;
        const summary = result.nextReminderAt ? `Routine reminder snoozed for ${duration}` : 'This reminder will not fire again today';
        this.$toast.add({severity: 'success', summary, life: 3000});
        await this.close_routine_reminder();
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.routine_reminder_loading_action = null;
      }
    },
    async complete_routine_reminder() {
      this.routine_reminder_loading_action = 'complete';
      try {
        const checkedRoutine = await routineService.checkin(this.routine_reminder.id, new Date());
        this.routines = this.routines.map(candidate => candidate.id === checkedRoutine.id ? checkedRoutine : candidate);
        await this.refresh_daily_status();
        await this.load_chart_data();
        this.$toast.add({severity:'success', summary: 'Routine marked as done', life: 3000});
        this.$confetti.start();
        setTimeout(() => this.$confetti.stop(), 2000);
        await this.close_routine_reminder();
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.routine_reminder_loading_action = null;
      }
    },
    async clear_routine_reminder_query() {
      const query = {...this.$route.query};
      delete query.routineReminderId;
      delete query.routineReminderScheduleId;
      delete query.routineReminderDate;
      delete query.notificationId;
      await this.$router.replace({query});
    },
    set_fat_status_bar_data() {
      if (this.fat_status_bar && this.last_weight) {
        this.fat_status_bar.data([this.last_weight.fat_percentage]);
      }
    },
    set_bmi_status_bar_data() {
      if (this.bmi_status_bar && this.last_weight) {
        this.bmi_status_bar.data([this.last_weight.bmi().value]);
      }
    },
    async init_fat_status_bar() {
      this.fat_status_bar = anychartLinearGauge.gauges.linear();
      this.fat_status_bar.top("-210px");
      this.fat_status_bar.height("450px");
      this.fat_status_bar.layout('horizontal');
      this.set_fat_status_bar_data();
      let scaleBarColorScale = buildBarColorScale();
      let scaleBar = this.fat_status_bar.scaleBar(0);
      scaleBar.width('5%');
      scaleBar.offset('31.5%');
      scaleBar.colorScale(scaleBarColorScale)
      let marker = this.fat_status_bar.marker(0);
      marker.offset('31.5%');
      marker.type('triangle-up');
      marker.zIndex(10);
      marker.color('black');
      let scale = this.fat_status_bar.scale();
      scale.minimum(5);
      scale.maximum(35);
      scale.ticks().interval(5);
      let axis = this.fat_status_bar.axis();
      axis.minorTicks(true)
      axis.minorTicks().stroke('#cecece');
      axis.width('1%');
      let title = axis.title();
      title.enabled(true);
      title.text('Fat %');
      title.padding(-45);
      axis.offset('29.5%');
      axis.orientation('top');
      axis.labels().format('{%value}%');
      this.fat_status_bar.container('fat-bar-status');
      this.fat_status_bar.draw();

      function buildBarColorScale() {
        let ranges = [];
        var toStatus = undefined;
        for (let statusKey in WeightStatus) {
          let status = WeightStatus[statusKey];
          if (toStatus) {
            ranges.push({
              from: status.fat,
              to: toStatus.fat,
              color: [status.color, toStatus.color]
            });
          }
          toStatus = status;
        }
        return anychart.scales.ordinalColor().ranges(ranges);
      }
    },
    async init_bmi_status_bar() {
      this.bmi_status_bar = anychartLinearGauge.gauges.linear();
      this.bmi_status_bar.top("-210px");
      this.bmi_status_bar.height("450px");
      this.bmi_status_bar.layout('horizontal');
      this.set_bmi_status_bar_data();
      let scaleBarColorScale = buildBarColorScale();
      let scaleBar = this.bmi_status_bar.scaleBar(0);
      scaleBar.width('5%');
      scaleBar.offset('31.5%');
      scaleBar.colorScale(scaleBarColorScale)
      let marker = this.bmi_status_bar.marker(0);
      marker.offset('31.5%');
      marker.type('triangle-up');
      marker.zIndex(10);
      marker.color('black');
      let scale = this.bmi_status_bar.scale();
      scale.minimum(10);
      scale.maximum(30);
      scale.ticks().interval(5);
      let axis = this.bmi_status_bar.axis();
      axis.minorTicks(true)
      axis.minorTicks().stroke('#cecece');
      axis.width('1%');
      let title = axis.title();
      title.enabled(true);
      title.text('BMI');
      title.padding(-45);
      axis.offset('29.5%');
      axis.orientation('top');
      axis.labels().format('{%value}');
      this.bmi_status_bar.container('bmi-bar-status');
      this.bmi_status_bar.draw();

      function buildBarColorScale() {
        let ranges = [];
        var toStatus = undefined;
        for (let statusKey in BMIStatus) {
          let status = BMIStatus[statusKey];
          if (toStatus) {
            ranges.push({
              from: status.value,
              to: toStatus.value,
              color: [status.color, toStatus.color]
            });
          }
          toStatus = status;
        }
        return anychart.scales.ordinalColor().ranges(ranges);
      }
    },
    async load_all_routines() {
      this.routines = await routineService.get_all_by(this.state.user.mail);
      this.sync_selected_routine_chart();
    },
    get_day_mood(day) {
      return !day || day.mood.average === null ? '' : this.get_mood_average_emoji(day.mood.average);
    },
    format_daily_mood(mood) {
      if (!mood) {
        return 'Not recorded';
      }
      return `${mood.emoji()} ${mood.label()} (${mood.value}/5)`;
    },
    format_mood_average(value) {
      if (value === null || value === undefined) {
        return 'Not recorded';
      }
      return `${this.get_mood_average_emoji(value)} ${Math.round(value * 100) / 100}/5`;
    },
    get_mood_average_emoji(value) {
      if (value === null || value === undefined) {
        return '';
      }
      return getMoodOption(Math.round(value)).emoji;
    },
    get_mood_color(value) {
      if (value === null || value === undefined) {
        return '';
      }
      if (value >= 4) {
        return 'perfect';
      }
      if (value >= 3) {
        return 'normal';
      }
      return 'bad';
    },
    get_mood_value_difference(currentMood, lastMood) {
      if (currentMood.average === null || lastMood.average === null) {
        return null;
      }
      return Math.round((currentMood.average - lastMood.average) * 100) / 100;
    },
    get_mood_trend_difference() {
      if (this.daily_status.mood_trend === null || this.daily_status.mood_trend === undefined || this.last_week_daily_status.mood_trend === null || this.last_week_daily_status.mood_trend === undefined) {
        return null;
      }
      return this.get_difference(this.daily_status.mood_trend, this.last_week_daily_status.mood_trend);
    },
    get_difference_class(value) {
      return {
        perfect: value > 0,
        bad: value < 0
      };
    },
    get_sleep_trend_class(value) {
      return {
        good: value > 0,
        bad: value < 0
      };
    },
    get_heart_rate_trend_class(value) {
      return {
        good: value < 0,
        bad: value > 0
      };
    },
    get_hrv_trend_class(value) {
      return {
        good: value > 0,
        bad: value < 0
      };
    },
    get_calorie_trend_class(value) {
      return {
        good: value < 0,
        bad: value > 0
      };
    },
    format_sleep_duration(value) {
      return formatDuration(value);
    },
    format_sleep_trend(value) {
      if (value === null || value === undefined) {
        return 'Not enough data';
      }
      const sign = value > 0 ? '+' : value < 0 ? '-' : '';
      return `${sign}${formatDuration(Math.abs(value))}`;
    },
    format_calorie_trend(value) {
      if (value === null || value === undefined) {
        return 'Not enough data';
      }
      const sign = value > 0 ? '+' : value < 0 ? '-' : '';
      return `${sign}${Math.abs(value)} kcal`;
    },
    format_sleep_metric_trend(value, unit) {
      if (value === null || value === undefined) {
        return 'Not enough data';
      }
      const sign = value > 0 ? '+' : value < 0 ? '-' : '';
      return `${sign}${Math.round(Math.abs(value) * 100) / 100} ${unit}`;
    },
    format_daily_sleep(sleep) {
      if (!sleep) {
        return 'Not recorded';
      }
      return sleep.totalSleepDurationFormat();
    },
    format_daily_calories(calorie) {
      if (!calorie) {
        return 'Not recorded';
      }
      return `${calorie.calories} kcal`;
    },
    get_week_days(weekStatus, excludedDate = null) {
      return [
        weekStatus?.saturday,
        weekStatus?.sunday,
        weekStatus?.monday,
        weekStatus?.tuesday,
        weekStatus?.wednesday,
        weekStatus?.thursday,
        weekStatus?.friday
      ].filter(day => day && (!excludedDate || !dayjs(day.date).isSame(excludedDate, 'day')));
    },
    get_selected_week_dates() {
      if (!this.daily_status?.date) {
        return [];
      }
      const weekStart = dayjs(this.daily_status.date).subtract((dayjs(this.daily_status.date).day() + 1) % 7, 'day');
      return Array.from({length: 7}, (_, index) => weekStart.add(index, 'day').format('YYYY-MM-DD'));
    },
    average_day_metric(days, key) {
      if (days.length === 0) {
        return null;
      }
      return Math.round(days.map(day => Number(day[key])).reduce((left, right) => left + right, 0) / days.length * 100) / 100;
    },
    average_values(values) {
      if (values.length === 0) {
        return null;
      }
      return Math.round(values.reduce((left, right) => left + right, 0) / values.length * 100) / 100;
    },
    get_effective_completed_date() {
      if (!this.last_completed_dashboard_date || !this.daily_status?.date) {
        return null;
      }
      const selectedDate = dayjs(this.daily_status.date);
      const completedDate = dayjs(this.last_completed_dashboard_date);
      return selectedDate.isBefore(completedDate, 'day') ? selectedDate : completedDate;
    },
    get_current_week_completed_day_count() {
      const effectiveCompletedDate = this.get_effective_completed_date();
      if (!effectiveCompletedDate) {
        return 0;
      }
      return this.get_week_days(this.week_status).filter(day => !dayjs(day.date).isAfter(effectiveCompletedDate, 'day')).length;
    },
    get_performance_score() {
      return Math.round(Number(this.daily_status.routines_status));
    },
    get_performance_score_trend() {
      return Number(this.daily_status.routines_status) - Number(this.last_week_daily_status.routines_status);
    },
    format_performance_score_trend() {
      const trend = this.get_performance_score_trend();
      return `${trend > 0 ? '↑' : '↓'} ${Math.abs(trend).toFixed(1)}`;
    },
    get_performance_score_trend_class() {
      const trend = this.get_performance_score_trend();
      return {
        perfect: trend > 0,
        bad: trend < 0
      };
    },
    get_week_days_for_total(weekStatus) {
      if (weekStatus === this.week_ago_status) {
        return this.get_week_days(weekStatus);
      }
      const completedDayCount = this.get_current_week_completed_day_count();
      return this.get_week_days(weekStatus).slice(0, completedDayCount);
    },
    get_week_percentage_total(weekStatus, excludedDate, key) {
      return this.average_day_metric(this.get_week_days_for_total(weekStatus), key);
    },
    get_completed_routine_week_days(weekStatus) {
      return this.get_week_days_for_total(weekStatus);
    },
    get_completed_routine_week_percentage_total(weekStatus, key) {
      return this.average_day_metric(this.get_week_days_for_total(weekStatus), key);
    },
    format_week_percentage_total(weekStatus, excludedDate, key) {
      const total = this.get_week_percentage_total(weekStatus, excludedDate, key);
      return total === null ? '' : total;
    },
    format_completed_routine_week_percentage_total(weekStatus, key) {
      const total = this.get_completed_routine_week_percentage_total(weekStatus, key);
      return total === null ? '' : total;
    },
    get_week_mood_average(weekStatus) {
      const values = this.get_week_days_for_total(weekStatus).map(day => day.mood.average).filter(value => value !== null);
      return this.average_values(values);
    },
    format_week_sleep(date) {
      const sleep = this.get_sleep_for(date);
      if (!sleep) {
        return '';
      }
      return sleep.totalSleepDurationFormat();
    },
    get_week_sleep_color(date) {
      const sleep = this.get_sleep_for(date);
      if (!sleep) {
        return '';
      }
      return getSleepMetricColor(sleep.totalSleepDuration);
    },
    get_week_sleep_average_minutes(weekStatus, excludedDate = null) {
      const sleeps = this.get_week_sleeps(weekStatus, excludedDate);
      if (sleeps.length === 0) {
        return null;
      }
      return Math.round(sleeps.reduce((total, sleep) => total + sleep.totalSleepDuration, 0) / sleeps.length);
    },
    format_week_sleep_average(weekStatus, excludedDate = null) {
      const average = this.get_week_sleep_average_minutes(weekStatus, excludedDate);
      if (average === null) {
        return '';
      }
      return formatDuration(average);
    },
    get_week_sleep_average_color(weekStatus, excludedDate = null) {
      const average = this.get_week_sleep_average_minutes(weekStatus, excludedDate);
      const sleeps = this.get_week_sleeps(weekStatus, excludedDate);
      if (average === null || sleeps.length === 0) {
        return '';
      }
      return getSleepMetricColor(average);
    },
    format_week_sleep_heart_rate(date) {
      const sleep = this.get_sleep_for(date);
      if (!sleep) {
        return '';
      }
      return sleep.heartRateFormat();
    },
    get_week_sleep_heart_rate_color(date) {
      const sleep = this.get_sleep_for(date);
      if (!sleep || sleep.averageHeartRate === null || sleep.averageHeartRate === undefined) {
        return '';
      }
      return getHeartRateMetricColor(sleep.averageHeartRate, sleep.date, this.sleeps);
    },
    get_week_sleep_heart_rate_average_value(weekStatus, excludedDate = null) {
      const sleeps = this.get_week_sleeps(weekStatus, excludedDate).filter(sleep => sleep.averageHeartRate !== null && sleep.averageHeartRate !== undefined);
      if (sleeps.length === 0) {
        return null;
      }
      return Math.round(sleeps.reduce((total, sleep) => total + Number(sleep.averageHeartRate), 0) / sleeps.length * 100) / 100;
    },
    format_week_sleep_heart_rate_average(weekStatus, excludedDate = null) {
      const average = this.get_week_sleep_heart_rate_average_value(weekStatus, excludedDate);
      if (average === null) {
        return '';
      }
      return `${average} bpm`;
    },
    get_week_sleep_heart_rate_average_color(weekStatus, excludedDate = null) {
      const average = this.get_week_sleep_heart_rate_average_value(weekStatus, excludedDate);
      const sleeps = this.get_week_sleeps(weekStatus, excludedDate).filter(sleep => sleep.averageHeartRate !== null && sleep.averageHeartRate !== undefined);
      if (average === null || sleeps.length === 0) {
        return '';
      }
      return getHeartRateMetricColor(average, this.get_latest_entry_date(sleeps), this.sleeps);
    },
    format_week_sleep_hrv(date) {
      const sleep = this.get_sleep_for(date);
      if (!sleep) {
        return '';
      }
      return sleep.hrvFormat();
    },
    get_week_sleep_hrv_color(date) {
      const sleep = this.get_sleep_for(date);
      if (!sleep || sleep.averageHrv === null || sleep.averageHrv === undefined) {
        return '';
      }
      return getHrvMetricColor(sleep.averageHrv, sleep.date, this.sleeps);
    },
    get_week_sleep_hrv_average_value(weekStatus, excludedDate = null) {
      const sleeps = this.get_week_sleeps(weekStatus, excludedDate).filter(sleep => sleep.averageHrv !== null && sleep.averageHrv !== undefined);
      if (sleeps.length === 0) {
        return null;
      }
      return Math.round(sleeps.reduce((total, sleep) => total + sleep.averageHrv, 0) / sleeps.length * 100) / 100;
    },
    format_week_sleep_hrv_average(weekStatus, excludedDate = null) {
      const average = this.get_week_sleep_hrv_average_value(weekStatus, excludedDate);
      if (average === null) {
        return '';
      }
      return `${average} ms`;
    },
    get_week_sleep_hrv_average_color(weekStatus, excludedDate = null) {
      const average = this.get_week_sleep_hrv_average_value(weekStatus, excludedDate);
      const sleeps = this.get_week_sleeps(weekStatus, excludedDate).filter(sleep => sleep.averageHrv !== null && sleep.averageHrv !== undefined);
      if (average === null || sleeps.length === 0) {
        return '';
      }
      return getHrvMetricColor(average, this.get_latest_entry_date(sleeps), this.sleeps);
    },
    format_week_calories(date) {
      const calorie = this.get_calorie_for(date);
      if (!calorie) {
        return '';
      }
      return `${calorie.calories} kcal`;
    },
    get_week_calories_color(date) {
      const calorie = this.get_calorie_for(date);
      if (!calorie) {
        return '';
      }
      return getCalorieMetricColor(calorie.calories, this.state.user.profile, calorie.date);
    },
    get_week_calories_average_value(weekStatus, excludedDate = null) {
      const calories = this.get_week_calories(weekStatus, excludedDate);
      if (calories.length === 0) {
        return null;
      }
      return Math.round(calories.reduce((total, calorie) => total + calorie.calories, 0) / calories.length * 100) / 100;
    },
    format_week_calories_average(weekStatus, excludedDate = null) {
      const average = this.get_week_calories_average_value(weekStatus, excludedDate);
      if (average === null) {
        return '';
      }
      return `${average} kcal`;
    },
    get_week_calories_average_color(weekStatus, excludedDate = null) {
      const average = this.get_week_calories_average_value(weekStatus, excludedDate);
      const target = this.get_week_typical_calories_average_value(weekStatus, excludedDate);
      const latestEntryDate = this.get_latest_entry_date(this.get_week_days_for_total(weekStatus));
      if (average === null || target === null || !latestEntryDate) {
        return '';
      }
      return getCalorieMetricColor(average, {
        typicalCaloriesPerDay: {
          saturday: target,
          sunday: target,
          monday: target,
          tuesday: target,
          wednesday: target,
          thursday: target,
          friday: target
        }
      }, latestEntryDate);
    },
    get_week_typical_calories_value(date) {
      if (!date) {
        return null;
      }
      const calorie = this.get_calorie_for(date);
      if (calorie) {
        return calorie.calories;
      }
      return getTypicalCaloriesForDate(this.state.user.profile, date);
    },
    format_week_typical_calories(date) {
      const calories = this.get_week_typical_calories_value(date);
      if (calories === null) {
        return '';
      }
      return `${calories} kcal`;
    },
    get_week_typical_calories_average_value() {
      const dates = this.get_selected_week_dates();
      if (dates.length === 0) {
        return null;
      }
      return Math.round(this.get_projected_week_calories_total() / dates.length * 100) / 100;
    },
    get_projected_week_calories_total() {
      return this.get_selected_week_dates().reduce((total, date) => total + this.get_week_typical_calories_value(date), 0);
    },
    format_week_typical_calories_average() {
      const average = this.get_week_typical_calories_average_value();
      if (average === null) {
        return '';
      }
      return `${average} kcal`;
    },
    get_sleep_for(date) {
      if (!date) {
        return null;
      }
      return this.sleeps.find(sleep => dayjs(sleep.date).isSame(date, 'day')) || null;
    },
    get_weight_for(date) {
      return this.weights.find(weight => dayjs(weight.date).isSame(date, 'day')) || null;
    },
    get_blood_pressure_for(date) {
      return this.blood_pressures.find(bloodPressure => dayjs(bloodPressure.date).isSame(date, 'day')) || null;
    },
    get_calorie_for(date) {
      if (!date) {
        return null;
      }
      return this.calories.find(calorie => dayjs(calorie.date).isSame(date, 'day')) || null;
    },
    get_meals_for(date) {
      return this.meals.filter(meal => dayjs(meal.date).isSame(date, 'day'));
    },
    get_meal_calories_total(date) {
      return this.get_meals_for(date).reduce((total, meal) => total + meal.calories, 0);
    },
    get_back_pain_episodes_for(date) {
      return this.back_pain_episodes.filter(episode => dayjs(episode.date).isSame(date, 'day'));
    },
    get_worst_back_pain_for(date) {
      return this.get_back_pain_episodes_for(date).reduce((worst, episode) => getBackPainSeverityRank(episode.severity) > getBackPainSeverityRank(worst) ? episode.severity : worst, null);
    },
    get_back_pain_change() {
      const difference = getBackPainSeverityRank(this.get_worst_back_pain_for(this.daily_status.date)) - getBackPainSeverityRank(this.get_worst_back_pain_for(this.last_week_daily_status.date));
      return difference < 0 ? 'Better' : difference > 0 ? 'Worse' : 'Same';
    },
    get_back_pain_rolling_worst() {
      const end = dayjs(this.daily_status.date);
      return Array.from({length: 30}, (_, index) => this.get_worst_back_pain_for(end.subtract(index, 'day')))
          .reduce((worst, severity) => getBackPainSeverityRank(severity) > getBackPainSeverityRank(worst) ? severity : worst, null);
    },
    get_back_pain_severity_class(value) {
      return getBackPainSeverityOption(value).className;
    },
    format_back_pain_severity(value) {
      return formatBackPainSeverity(value);
    },
    get_back_pain_change_class(value) {
      return {
        good: value === 'Better',
        bad: value === 'Worse'
      };
    },
    format_back_pain_location(episode) {
      return formatBackPainLocation(episode);
    },
    format_back_pain_period(value) {
      return formatBackPainPeriod(value);
    },
    async remove_back_pain_episode(episode) {
      if (!confirm('Are you sure you want to delete this episode?')) {
        return;
      }
      try {
        await backPainEpisodeService.delete(episode);
        await this.load_all_back_pain_episodes();
      } catch (e) {
        this.handle_error(e);
      }
    },
    is_sleep_entry_missing() {
      return this.get_sleep_for(this.daily_status.date) === null;
    },
    is_mood_entry_missing() {
      return !this.daily_status.mood.morning || !this.daily_status.mood.midday || !this.daily_status.mood.evening;
    },
    is_calorie_entry_missing() {
      return this.get_calorie_for(this.daily_status.date) === null;
    },
    is_routine_entry_missing() {
      return this.daily_status.total_routines > 0 && this.daily_status.routines_done === 0;
    },
    is_workout_entry_missing() {
      return this.current_workout === null;
    },
    has_dashboard_completion_warning() {
      return !this.is_selected_date_completed()
          && (this.is_routine_entry_missing()
              || this.is_sleep_entry_missing()
              || this.is_mood_entry_missing()
              || this.is_calorie_entry_missing()
              || this.is_workout_entry_missing());
    },
    get_week_calories(weekStatus) {
      return this.get_week_days_for_total(weekStatus).map(day => this.get_calorie_for(day.date)).filter(calorie => calorie);
    },
    get_week_sleeps(weekStatus) {
      return this.get_week_days_for_total(weekStatus).map(day => this.get_sleep_for(day.date)).filter(sleep => sleep);
    },
    get_latest_entry_date(entries) {
      return entries.reduce((latest, entry) => {
        if (!latest || dayjs(entry.date).isAfter(latest, 'day')) {
          return entry.date;
        }
        return latest;
      }, null);
    },
    get_sleep_duration_difference(currentSleep, lastSleep) {
      if (!currentSleep || !lastSleep) {
        return null;
      }
      return currentSleep.totalSleepDuration - lastSleep.totalSleepDuration;
    },
    get_mood_trend_color_value(value) {
      if (value === null || value === undefined) {
        return 0;
      }
      return Math.round(value);
    },
    get_current_date() {
      return this.daily_status.date;
    },
    get_difference(a, b) {
      return Math.round((a - b) * 100) / 100;
    },
    is_day_navigation_loading() {
      return this.day_navigation_loading;
    },
    async new_daily_status() {
      if (this.is_day_navigation_loading() || this.daily_status.isToday()) {
        return;
      }

      this.day_navigation_loading = true;
      try {
        const dashboard = await dashboardService.advance();
        this.apply_dashboard(dashboard);
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.day_navigation_loading = false;
      }
    },
    async previous_daily_status() {
      if (this.is_day_navigation_loading()) {
        return;
      }

      this.day_navigation_loading = true;
      try {
        const dashboard = await dashboardService.retreat();
        this.apply_dashboard(dashboard);
        this.$toast.add({severity:'success', summary: 'Previous day loaded', life: 3000});
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.day_navigation_loading = false;
      }
    },
    async refresh_daily_status() {
      const dashboard = await dashboardService.refresh();
      this.apply_dashboard(dashboard);
    },
    async record_decision_outcome(outcome) {
      this.decision_outcome_loading = true;
      this.pending_decision_outcome = outcome;
      try {
        await decisionOutcomeService.create(this.daily_status.date, outcome);
        if (outcome === 'WIN') {
          this.$refs.winCelebration.playRandom();
        }
        await this.load_status();
        this.$toast.add({severity:'success', summary: `${outcome} recorded`, life: 3000});
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.decision_outcome_loading = false;
        this.pending_decision_outcome = null;
      }
    },
    format_outcome_metrics(metrics) {
      if (metrics.winRate === null) {
        return `${metrics.wins} WIN / ${metrics.misses} MISS (No decisions)`;
      }
      return `${metrics.wins} WIN / ${metrics.misses} MISS (${this.format_outcome_percentage(metrics.winRate)}%)`;
    },
    format_outcome_percentage(value) {
      return Math.round(Number(value) * 100) / 100;
    },
    format_win_rate_change() {
      const change = this.wins_and_misses_status.winRateChange;
      if (change === null) {
        return 'Not enough data';
      }
      const formatted = this.format_outcome_percentage(change);
      return `${formatted > 0 ? '+' : ''}${formatted} pp`;
    },
    get_win_rate_change_class() {
      const change = this.wins_and_misses_status.winRateChange;
      return {
        perfect: change > 0,
        bad: change < 0
      };
    },
    can_toggle_dashboard_completion() {
      return this.is_selected_date_completed() || this.can_mark_selected_date_completed();
    },
    can_open_reflection() {
      return !!this.last_completed_dashboard_date && !dayjs(this.daily_status.date).isAfter(this.last_completed_dashboard_date, 'day');
    },
    can_show_reflection_advice() {
      return !this.can_open_reflection() && !!this.latest_reflection;
    },
    open_reflection() {
      const date = dayjs(this.daily_status.date).format('YYYY-MM-DD');
      const copyPrompt = navigator.clipboard.writeText(buildReflectionPrompt(date));
      this.$router.push({name: 'Reflection', query: {date}});
      copyPrompt
        .then(() => this.$toast.add({
          severity: 'info',
          summary: 'Prompt copied',
          detail: 'Paste it into ChatGPT to continue.',
          life: 5000
        }))
        .catch(error => this.handle_error(error));
    },
    ask_for_advice() {
      const currentTime = dayjs().format('dddd, D MMMM YYYY [at] HH:mm ([UTC]Z)');
      const contextDate = dayjs(this.last_completed_dashboard_date).format('YYYY-MM-DD');
      const prompt = buildReflectionAdvicePrompt(this.latest_reflection, contextDate, currentTime);
      const copyPrompt = navigator.clipboard.writeText(prompt);
      window.open(this.chatgpt_url, '_blank', 'noopener,noreferrer');
      copyPrompt
        .then(() => this.$toast.add({
          severity: 'info',
          summary: 'Advice prompt copied',
          detail: 'Paste it into ChatGPT to continue.',
          life: 5000
        }))
        .catch(error => this.handle_error(error));
    },
    can_mark_selected_date_completed() {
      if (!this.daily_status?.date) {
        return false;
      }
      if (!this.last_completed_dashboard_date) {
        return true;
      }
      return dayjs(this.daily_status.date).isAfter(this.last_completed_dashboard_date, 'day');
    },
    is_selected_date_completed() {
      return !!this.last_completed_dashboard_date && dayjs(this.daily_status?.date).isSame(this.last_completed_dashboard_date, 'day');
    },
    async toggle_dashboard_completion() {
      if (this.dashboard_completion_loading || !this.can_toggle_dashboard_completion()) {
        return;
      }

      const completed = !this.is_selected_date_completed();
      this.dashboard_completion_loading = true;
      try {
        const dashboard = await dashboardService.setDashboardCompletion(completed);
        this.apply_dashboard(dashboard);
        this.$toast.add({
          severity:'success',
          summary: completed ? 'Day marked completed' : 'Day completion undone',
          life: 3000
        });
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.dashboard_completion_loading = false;
      }
    },
    async load_status() {
      this.apply_dashboard(await dashboardService.get());
    },
    async load_reflection_advice() {
      this.reflection_overview = await reflectionService.getOverview();
      const latestReflectionDate = this.reflection_overview.reflections[0]?.reflectionDate;
      this.latest_reflection = latestReflectionDate ? await reflectionService.get(latestReflectionDate) : null;
    },
    apply_dashboard(dashboard) {
      this.last_completed_dashboard_date = dashboard.lastCompletedDashboardDate ? new Date(dashboard.lastCompletedDashboardDate) : null;
      this.daily_status = dashboard.dailyStatus;
      this.last_week_daily_status = dashboard.lastWeekDailyStatus;
      this.week_status = dashboard.weekStatus;
      this.week_ago_status = dashboard.weekAgoStatus;
      this.wins_and_misses_status = dashboard.winsAndMissesStatus;
      this.sync_workout_context();
    },
    isRoutineDone(routine) {
      return routine.isDone(this.daily_status.date);
    },
    isRoutineCheckinDisabled(routine) {
      return routine.isDisabled(this.daily_status.date)
          || this.isRoutineActionPending(routine.id);
    },
    isRoutineActionPending(routineId) {
      return this.routine_action_loading_id === routineId;
    },
    get_routine_chart_options() {
      return this.routines.map(routine => ({
        id: routine.id,
        label: this.get_routine_chart_label(routine)
      }));
    },
    get_routine_chart_label(routine) {
      const types = routine.typeValues();
      return types ? `${routine.name} (${types})` : routine.name;
    },
    get_selected_routine_chart() {
      return this.routines.find(routine => routine.id === this.selected_routine_chart_id);
    },
    sync_selected_routine_chart() {
      if (this.routines.length === 0) {
        this.selected_routine_chart_id = undefined;
        return;
      }
      if (!this.get_selected_routine_chart()) {
        this.selected_routine_chart_id = this.routines[0].id;
      }
    },
    async plusRoutine(routine) {
      if (this.isRoutineCheckinDisabled(routine)) {
        return;
      }

      this.routine_action_loading_id = routine.id;
      try {
        const checkedRoutine = await routineService.checkin(routine.id, this.get_current_date());
        this.routines = this.routines.map(candidate => candidate.id === checkedRoutine.id ? checkedRoutine : candidate);
        this.$toast.add({severity:'success', summary: 'Routine done it', life: 3000});
        await this.refresh_daily_status();
        await this.load_chart_data();
        this.$confetti.start();
        setTimeout(function (){
          this.$confetti.stop();
        }.bind(this), 2000);
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.routine_action_loading_id = null;
      }
    },
    async undoRoutine(routine) {
      if (!this.isRoutineDone(routine) || this.isRoutineActionPending(routine.id)) {
        return;
      }

      this.routine_action_loading_id = routine.id;
      try {
        const updatedRoutine = await routineService.undoCheckin(routine.id, this.get_current_date());
        this.routines = this.routines.map(candidate => candidate.id === updatedRoutine.id ? updatedRoutine : candidate);
        this.$toast.add({severity:'success', summary: 'Routine undone', life: 3000});
        await this.refresh_daily_status();
        await this.load_chart_data();
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.routine_action_loading_id = null;
      }
    },
    get_routine_status_color(percentage) {
      if (percentage === null || percentage === undefined || percentage === '') {
        return '';
      }
      if (percentage >= 80) {
        return 'perfect';
      }
      if (percentage >= 60) {
        return 'good';
      }
      if (percentage >= 50) {
        return 'normal';
      }
      if (percentage >= 40) {
        return 'fail';
      }
      return 'bad';
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e, life: 3000});
    },
    async load_all_weights() {
      this.weights = await weightService.get_all_by(this.state.user.mail);
      this.last_weight = this.weights[0];
    },
    async load_all_blood_pressures() {
      this.blood_pressures = await bloodPressureService.get_all_by(this.state.user.mail);
      this.last_blood_pressure = this.blood_pressures[0];
    },
    async load_all_lipid_panels() {
      this.lipid_panels = await lipidPanelService.get_all();
      this.last_lipid_panel = this.lipid_panels[0];
    },
    async load_all_moods() {
      this.moods = await moodService.get_all_by(this.state.user.mail);
    },
    async load_all_sleeps() {
      this.sleeps = await sleepService.get_all();
      this.last_sleep = this.sleeps[0];
    },
    async load_all_calories() {
      this.calories = await calorieService.get_all();
    },
    async load_all_meals() {
      this.meals = await mealService.get_all();
    },
    async load_all_workouts() {
      this.workouts = await workoutService.get_all();
      this.sync_workout_context();
    },
    async load_all_back_pain_episodes() {
      this.back_pain_episodes = await backPainEpisodeService.get_all();
    },
    async remove_meal(meal) {
      if (!confirm('Are you sure you want to delete this meal?')) {
        return;
      }
      try {
        await mealService.delete(meal);
        await this.load_all();
      } catch (e) {
        this.handle_error(e);
      }
    },
    async refresh_workout_status() {
      await this.load_all_workouts();
    },
    sync_workout_context() {
      if (!this.daily_status) {
        this.current_workout = null;
        this.previous_week_workout = null;
        return;
      }
      this.current_workout = this.workouts.find(workout => dayjs(workout.workoutDate).isSame(this.daily_status.date, 'day')) || null;
      this.previous_week_workout = this.workouts.find(workout => dayjs(workout.workoutDate).isSame(this.last_week_daily_status.date, 'day')) || null;
    },
    get_workout_lines(workout) {
      return [...(workout?.lines || [])].sort((left, right) => left.position - right.position);
    },
    format_workout_weight(weight) {
      return weight || weight === 0 ? `${weight} kg` : null;
    },
    format_workout_duration(seconds) {
      const minutes = Math.floor(seconds / 60);
      const remainder = seconds % 60;
      return `${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`;
    },
    format_workout_reps_set(set) {
      const weight = this.format_workout_weight(set.weight);
      return weight ? `${weight} x ${set.repetitions} reps` : `${set.repetitions} reps`;
    },
    format_workout_seconds_set(set) {
      const weight = this.format_workout_weight(set.weight);
      const duration = this.format_workout_duration(set.durationSeconds);
      return weight ? `${weight} x ${duration}` : duration;
    },
    format_workout_cardio_interval(interval) {
      const details = [this.format_workout_duration(interval.durationSeconds)];
      if (interval.distanceKm || interval.distanceKm === 0) {
        details.push(`${interval.distanceKm} km`);
      }
      if (interval.speedKph || interval.speedKph === 0) {
        details.push(`${interval.speedKph} km/h`);
      }
      if (interval.inclinePercent || interval.inclinePercent === 0) {
        details.push(`${interval.inclinePercent}% incline`);
      }
      if (interval.resistanceLevel || interval.resistanceLevel === 0) {
        details.push(`resistance ${interval.resistanceLevel}`);
      }
      return details.join(' | ');
    },
    format_workout_line_footer(line) {
      const details = [];
      if (line.calories || line.calories === 0) {
        details.push(`${line.calories} kcal`);
      }
      if (line.averageHeartRate || line.averageHeartRate === 0) {
        details.push(`${line.averageHeartRate} bpm`);
      }
      return details.length ? details.join(' | ') : null;
    },
    async load_all() {
      await this.load_all_routines();
      await this.load_remaining_dashboard_data();
    },
    async load_remaining_dashboard_data() {
      await this.load_all_weights();
      await this.load_all_blood_pressures();
      await this.load_all_lipid_panels();
      await this.load_all_moods();
      await this.load_all_sleeps();
      await this.load_all_calories();
      await this.load_all_meals();
      await this.load_all_back_pain_episodes();
      await this.load_status();
      await this.load_reflection_advice();
      await this.load_all_workouts();
      await this.load_chart_data();
      await this.load_current_trend();
      if (this.last_weight && this.current_weight_trend) {
        this.load_current_weight_strike();
        this.load_current_fat_percentage_strike();
        this.load_months_next_range();
      }
      this.set_fat_status_bar_data();
      this.set_bmi_status_bar_data();
    },
    async load_current_trend() {
      this.current_weight_trend = summaryService.get_weight_trend(this.weights);
      this.current_blood_pressure_trend = summaryService.get_blood_pressure_trend(this.blood_pressures);
      let current_period_sleeps = summaryService.get_rolling_period_measures_for(this.daily_status.date, this.sleeps);
      this.current_sleep_status_entry_count = current_period_sleeps.length;
      this.current_sleep_status = current_period_sleeps.length >= TREND_WINDOW_DAYS ? getSleepStatus(current_period_sleeps) : undefined;
      this.current_sleep_trend = summaryService.get_sleep_trend(this.sleeps, this.daily_status.date);
      this.current_calorie_trend = summaryService.get_calorie_trend(this.calories, this.daily_status.date);
    },
    load_current_weight_strike() {
      let range = this.last_weight.range();
      this.current_weight_strike = summaryService.get_weight_strike_days(range, this.weights);
    },
    load_current_fat_percentage_strike() {
      let threshold = this.last_weight.fat_percentage_threshold();
      this.current_fat_percentage_strike = summaryService.get_fat_percentage_strike_days(threshold, this.weights);
    },
    load_months_next_range() {
      this.months_next_range = this.last_weight.months_next_range(this.current_weight_trend)
    },
    load_chart_data: async function () {
      if (!this.last_weight && !this.last_sleep && this.routines.length === 0 && this.calories.length === 0 && this.moods.length === 0 && this.lipid_panels.length === 0) {
        return;
      }
      this.state.loading = true;
      if (this.routines.length > 0) {
        this.sync_selected_routine_chart();
        let routines_from_date = get_routines_from_date(this.chart_type, this.routines);
        let month_routines = get_month_routines_from(routines_from_date, this.routines);
        this.routines_chart_data = build_month_routines_chart(month_routines, this.chart_type);
        let selected_routine = this.get_selected_routine_chart();
        this.selected_routine_chart_data = selected_routine
            ? build_month_routine_chart(selected_routine, routines_from_date, this.chart_type)
            : undefined;
      } else {
        this.routines_chart_data = undefined;
        this.selected_routine_chart_data = undefined;
      }
      if (this.last_weight) {
        let from_date = get_measures_from_date(this.chart_type, this.weights, this.blood_pressures);
        let month_measures = get_month_measures_from(from_date, this.weights, this.blood_pressures);
        this.weight_chart_data = build_month_weight_chart(month_measures, this.chart_type);
        this.fat_chart_data = build_month_fat_chart(month_measures, this.chart_type);
        this.muscle_chart_data = build_month_muscle_chart(month_measures, this.chart_type);
        this.upper_pressure_chart_data = build_month_upper_pressure_chart(month_measures, this.chart_type);
        this.lower_pressure_chart_data = build_month_lower_pressure_chart(month_measures, this.chart_type);
        this.weight_lost_chart_data = build_month_weight_lost_chart(month_measures, this.chart_type);
        this.fat_lost_chart_data = build_month_fat_lost_chart(month_measures, this.chart_type);
        this.muscle_lost_chart_data = build_month_muscle_lost_chart(month_measures, this.chart_type);
        this.upper_pressure_lost_chart_data = build_month_upper_pressure_lost_chart(month_measures, this.chart_type);
        this.lower_pressure_lost_chart_data = build_month_lower_pressure_lost_chart(month_measures, this.chart_type);
      } else {
        this.weight_chart_data = undefined;
        this.fat_chart_data = undefined;
        this.muscle_chart_data = undefined;
        this.upper_pressure_chart_data = undefined;
        this.lower_pressure_chart_data = undefined;
        this.weight_lost_chart_data = undefined;
        this.fat_lost_chart_data = undefined;
        this.muscle_lost_chart_data = undefined;
        this.upper_pressure_lost_chart_data = undefined;
        this.lower_pressure_lost_chart_data = undefined;
      }
      if (this.last_sleep) {
        let sleep_from_date = get_sleeps_from_date(this.chart_type, this.sleeps);
        let month_sleeps = get_month_sleeps_from(sleep_from_date, this.sleeps);
        this.sleep_total_chart_data = build_month_sleep_duration_chart('Total Sleep', '#233d4d', month_sleeps, this.chart_type, 'totalSleepDuration');
        this.sleep_deep_chart_data = build_month_sleep_duration_chart('Deep Sleep', '#005f73', month_sleeps, this.chart_type, 'deepSleepDuration');
        this.sleep_rem_chart_data = build_month_sleep_duration_chart('REM Sleep', '#0a9396', month_sleeps, this.chart_type, 'remSleepDuration');
        this.sleep_light_chart_data = build_month_sleep_duration_chart('Light Sleep', '#94d2bd', month_sleeps, this.chart_type, 'lightSleepDuration');
        this.sleep_awake_chart_data = build_month_sleep_duration_chart('Awake Time', '#ee9b00', month_sleeps, this.chart_type, 'awakeTime');
        this.sleep_heart_rate_chart_data = build_month_sleep_numeric_chart('Average Heart Rate bpm', '#bb3e03', month_sleeps, this.chart_type, 'averageHeartRate');
        this.sleep_hrv_chart_data = build_month_sleep_numeric_chart('Average HRV ms', '#ae2012', month_sleeps, this.chart_type, 'averageHrv');
        this.sleep_bedtime_start_chart_data = build_month_sleep_time_chart('Bedtime Start', '#3a86ff', month_sleeps, this.chart_type, 'bedtimeStartMinutes');
        this.sleep_bedtime_end_chart_data = build_month_sleep_time_chart('Bedtime End', '#8338ec', month_sleeps, this.chart_type, 'bedtimeEndMinutes');
      } else {
        this.sleep_total_chart_data = undefined;
        this.sleep_deep_chart_data = undefined;
        this.sleep_rem_chart_data = undefined;
        this.sleep_light_chart_data = undefined;
        this.sleep_awake_chart_data = undefined;
        this.sleep_heart_rate_chart_data = undefined;
        this.sleep_hrv_chart_data = undefined;
        this.sleep_bedtime_start_chart_data = undefined;
        this.sleep_bedtime_end_chart_data = undefined;
      }
      if (this.moods.length > 0) {
        let mood_from_date = get_moods_from_date(this.chart_type, this.moods);
        let month_moods = get_month_moods_from(mood_from_date, this.moods);
        this.mood_chart_data = build_month_mood_chart(month_moods, this.chart_type);
      } else {
        this.mood_chart_data = undefined;
      }
      if (this.calories.length > 0) {
        let calorie_from_date = get_calories_from_date(this.chart_type, this.calories);
        let month_calories = get_month_calories_from(calorie_from_date, this.calories);
        this.calorie_chart_data = build_month_calorie_chart(month_calories, this.chart_type);
      } else {
        this.calorie_chart_data = undefined;
      }
      const chart_lipid_panels = get_lipid_panels_for_chart(this.chart_type, this.lipid_panels);
      if (chart_lipid_panels.length > 0) {
        this.total_cholesterol_chart_data = build_lipid_panel_chart('Total Cholesterol mg/dL', '#1a36c1', chart_lipid_panels, 'totalCholesterol');
        this.hdl_cholesterol_chart_data = build_lipid_panel_chart('HDL Cholesterol mg/dL', '#06a01b', chart_lipid_panels, 'hdlCholesterol');
        this.ldl_cholesterol_chart_data = build_lipid_panel_chart('LDL Cholesterol mg/dL', '#c95110', chart_lipid_panels, 'ldlCholesterol');
        this.triglycerides_chart_data = build_lipid_panel_chart('Triglycerides mg/dL', '#9c6644', chart_lipid_panels, 'triglycerides');
      } else {
        this.total_cholesterol_chart_data = undefined;
        this.hdl_cholesterol_chart_data = undefined;
        this.ldl_cholesterol_chart_data = undefined;
        this.triglycerides_chart_data = undefined;
      }
      this.state.loading = false;

      function build_lipid_panel_chart(title, color, panels, key) {
        return {
          data: {
            labels: panels.map(panel => panel.dateFormat),
            datasets: [{
              label: title,
              borderColor: color,
              fill: false,
              data: panels.map(panel => panel[key])
            }]
          },
          options: {
            plugins: {
              title: {
                display: true,
                text: title
              }
            }
          }
        };
      }

      function get_lipid_panels_for_chart(chart_type, panels) {
        const from_date = chart_type === 'all' ? null : get_first_date(chart_type);
        return panels
            .filter(panel => !from_date || panel.date >= from_date)
            .sort((left, right) => left.date - right.date);
      }

      function build_month_weight_lost_chart(measures, chart_type) {
        let current_data = [];
        for (const lost of measures.month_average_measures) {
          current_data.push(lost.lost_weight);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lost_weight);
        });
        return build_chart_settings('Lost Weigh Kg', '#10bac9', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_fat_lost_chart(measures, chart_type) {
        let current_data = [];
        for (const lost of measures.month_average_measures) {
          current_data.push(lost.lost_fat);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lost_fat);
        });
        return build_chart_settings('Lost Fat Kg', '#d2b918', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_muscle_lost_chart(measures, chart_type) {
        let current_data = [];
        for (const lost of measures.month_average_measures) {
          current_data.push(lost.lost_muscle);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lost_muscle);
        });
        return build_chart_settings('Lost Muscle Kg', '#6fb374', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_upper_pressure_lost_chart(measures, chart_type) {
        let current_data = [];
        for (const lost of measures.month_average_measures) {
          current_data.push(lost.lost_upper);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lost_upper);
        });
        return build_chart_settings('Lost Upper Blood Pressure mm Hg', '#c95110', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_lower_pressure_lost_chart(measures, chart_type) {
        let current_data = [];
        for (const lost of measures.month_average_measures) {
          current_data.push(lost.lost_lower);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lost_lower);
        });
        return build_chart_settings('Lost Lower Blood Pressure mm Hg', '#06a089', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_upper_pressure_chart(measures, chart_type) {
        let current_data = [];
        for (const measure of measures.month_average_measures) {
          current_data.push(measure.upper);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.upper);
        });
        return build_chart_settings('Upper Pressure mm Hg', '#c95110', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_lower_pressure_chart(measures, chart_type) {
        let current_data = [];
        for (const measure of measures.month_average_measures) {
          current_data.push(measure.lower);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lower);
        });
        return build_chart_settings('Lower Pressure mm Hg', '#06a089', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_muscle_chart(measures, chart_type) {
        let current_data = [];
        for (const measure of measures.month_average_measures) {
          current_data.push(measure.muscle);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.muscle);
        });
        return build_chart_settings('Muscle %', '#06a01b', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_fat_chart(measures, chart_type) {
        let current_data = [];
        for (const measure of measures.month_average_measures) {
          current_data.push(measure.fat);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.fat);
        });
        return build_chart_settings('Fat %', '#c91016', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_weight_chart(measures, chart_type) {
        let current_data = [];
        measures.month_average_measures.forEach(measure => {
          current_data.push(measure.weight);
        });
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.weight);
        });
        return build_chart_settings('Weight Kg', '#1a36c1', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_routines_chart(routines, chart_type) {
        let current_data = [];
        routines.month_average_routines.forEach(routine_percentage => {
          current_data.push(routine_percentage);
        });
        let year_ago_data = [];
        routines.year_ago_month_average_routines.forEach(routine_percentage => {
          year_ago_data.push(routine_percentage);
        });
        return build_chart_settings('Routine %', '#0a123a', chart_type, current_data, year_ago_data, routines.labels);
      }

      function build_month_routine_chart(routine, from_date, chart_type) {
        let month_routine = get_month_routine_from(from_date, routine);
        return build_chart_settings(`${routine.name} %`, '#0a123a', chart_type, month_routine.month_percentages, month_routine.year_ago_month_percentages, month_routine.labels);
      }

      function build_month_sleep_duration_chart(title, color, sleeps, chart_type, key) {
        return build_chart_settings(
            `${title} h`,
            color,
            chart_type,
            sleeps.month_average_sleeps.map(sleep => sleep ? sleep[key] / 3600 : null),
            sleeps.year_ago_month_average_sleeps.map(sleep => sleep ? sleep[key] / 3600 : null),
            sleeps.labels
        );
      }

      function build_month_sleep_numeric_chart(title, color, sleeps, chart_type, key) {
        return build_chart_settings(
            title,
            color,
            chart_type,
            sleeps.month_average_sleeps.map(sleep => sleep ? sleep[key] : null),
            sleeps.year_ago_month_average_sleeps.map(sleep => sleep ? sleep[key] : null),
            sleeps.labels
        );
      }

      function build_month_sleep_time_chart(title, color, sleeps, chart_type, key) {
        return build_chart_settings(
            title,
            color,
            chart_type,
            sleeps.month_average_sleeps.map(sleep => sleep ? sleep[key] : null),
            sleeps.year_ago_month_average_sleeps.map(sleep => sleep ? sleep[key] : null),
            sleeps.labels,
            value => formatTimeOfDayFromMinutes(value)
        );
      }

      function build_month_mood_chart(moods, chart_type) {
        return {
          data: {
            labels: moods.labels,
            datasets: [
              {
                label: 'Current',
                borderColor: '#f59e0b',
                fill: false,
                data: moods.month_average_moods
              },
              ...(chart_type !== 'all' ? [{
                label: 'Year Ago',
                borderColor: 'gray',
                fill: false,
                data: moods.year_ago_month_average_moods
              }] : [])
            ]
          },
          options: {
            plugins: {
              title: {
                display: true,
                text: 'Mood /5'
              },
              tooltip: {
                callbacks: {
                  title: format_chart_tooltip_title
                }
              }
            },
            scales: {
              y: {
                min: 1,
                max: 5,
                ticks: {
                  stepSize: 1,
                  callback(value) {
                    return ['','Very Bad','Bad','Neutral','Good','Great'][Math.round(value)] || value;
                  }
                }
              }
            }
          }
        };
      }

      function build_month_calorie_chart(calories, chart_type) {
        return build_chart_settings(
            'Calories kcal',
            '#9c6644',
            chart_type,
            calories.month_average_calories,
            calories.year_ago_month_average_calories,
            calories.labels
        );
      }

      function get_month_routines_from(from_date, routines) {
        let month_routine = {
          labels: [],
          month_average_routines: [],
          year_ago_month_average_routines: []
        };
        let current_date = dayjs(from_date);
        let current_month = dayjs().endOf('month').toDate();
        while (current_date.toDate() <= current_month) {
          month_routine.labels.push(current_date.format('MMM-YYYY'));
          month_routine.month_average_routines.push(summaryService.get_month_average_routines_percentage_for(current_date, routines) ?? null)
          current_date = current_date.add(1, 'month')
        }
        let year_ago_current_date = dayjs(from_date).subtract(1, 'year');
        let year_ago_next_month = dayjs(current_month).subtract(1, 'year').toDate();
        while (year_ago_current_date.toDate() <= year_ago_next_month) {
          month_routine.year_ago_month_average_routines.push(summaryService.get_month_average_routines_percentage_for(year_ago_current_date, routines) ?? null)
          year_ago_current_date = year_ago_current_date.add(1, 'month')
        }
        return month_routine;
      }

      function get_month_routine_from(from_date, routine) {
        let month_routine = {
          labels: [],
          month_percentages: [],
          year_ago_month_percentages: []
        };
        let current_date = dayjs(from_date);
        let current_month = dayjs().endOf('month').toDate();
        while (current_date.toDate() <= current_month) {
          month_routine.labels.push(current_date.format('MMM-YYYY'));
          month_routine.month_percentages.push(routine.month_percentage(current_date) ?? null);
          current_date = current_date.add(1, 'month')
        }
        let year_ago_current_date = dayjs(from_date).subtract(1, 'year');
        let year_ago_next_month = dayjs(current_month).subtract(1, 'year').toDate();
        while (year_ago_current_date.toDate() <= year_ago_next_month) {
          month_routine.year_ago_month_percentages.push(routine.month_percentage(year_ago_current_date) ?? null);
          year_ago_current_date = year_ago_current_date.add(1, 'month')
        }
        return month_routine;
      }

      function get_month_sleeps_from(from_date, sleeps) {
        let month_sleep = {
          labels: [],
          month_average_sleeps: [],
          year_ago_month_average_sleeps: []
        };
        let current_date = dayjs(from_date);
        let next_month = dayjs().add(1, 'month').toDate();
        while (current_date.toDate() <= next_month) {
          month_sleep.labels.push(current_date.format('MMM-YYYY'));
          month_sleep.month_average_sleeps.push(summaryService.get_month_average_sleeps_for(current_date, sleeps) || null);
          current_date = current_date.add(1, 'month')
        }
        let year_ago_current_date = dayjs(from_date).subtract(1, 'year');
        let year_ago_next_month = dayjs(next_month).subtract(1, 'year').toDate();
        while (year_ago_current_date.toDate() <= year_ago_next_month) {
          month_sleep.year_ago_month_average_sleeps.push(summaryService.get_month_average_sleeps_for(year_ago_current_date, sleeps) || null);
          year_ago_current_date = year_ago_current_date.add(1, 'month')
        }
        return month_sleep;
      }

      function get_month_moods_from(from_date, moods) {
        let month_mood = {
          labels: [],
          month_average_moods: [],
          year_ago_month_average_moods: []
        };
        let current_date = dayjs(from_date).startOf('month');
        let next_month = dayjs().add(1, 'month').startOf('month');
        while (!current_date.isAfter(next_month, 'month')) {
          month_mood.labels.push(current_date.format('MMM-YYYY'));
          month_mood.month_average_moods.push(summaryService.get_month_average_moods_for(current_date, moods) ?? null);
          current_date = current_date.add(1, 'month');
        }
        let year_ago_current_date = dayjs(from_date).subtract(1, 'year');
        let year_ago_next_month = next_month.subtract(1, 'year');
        while (!year_ago_current_date.isAfter(year_ago_next_month, 'month')) {
          month_mood.year_ago_month_average_moods.push(summaryService.get_month_average_moods_for(year_ago_current_date, moods) ?? null);
          year_ago_current_date = year_ago_current_date.add(1, 'month');
        }
        return month_mood;
      }

      function get_month_calories_from(from_date, calories) {
        let month_calorie = {
          labels: [],
          month_average_calories: [],
          year_ago_month_average_calories: []
        };
        let current_date = dayjs(from_date);
        let next_month = dayjs().add(1, 'month').toDate();
        while (current_date.toDate() <= next_month) {
          month_calorie.labels.push(current_date.format('MMM-YYYY'));
          month_calorie.month_average_calories.push(summaryService.get_month_average_calories_for(current_date, calories) ?? null);
          current_date = current_date.add(1, 'month')
        }
        let year_ago_current_date = dayjs(from_date).subtract(1, 'year');
        let year_ago_next_month = dayjs(next_month).subtract(1, 'year').toDate();
        while (year_ago_current_date.toDate() <= year_ago_next_month) {
          month_calorie.year_ago_month_average_calories.push(summaryService.get_month_average_calories_for(year_ago_current_date, calories) ?? null);
          year_ago_current_date = year_ago_current_date.add(1, 'month')
        }
        return month_calorie;
      }

      function get_month_measures_from(from_date, weights, blood_pressures) {
        let month_measure = {
          labels: [],
          month_average_measures: [],
          year_ago_month_average_measures: []
        };
        let current_date = dayjs(from_date);
        let next_month = dayjs().add(1, 'month').toDate();
        var month_average_weight;
        var month_average_blood_pressure;
        while (current_date.toDate() <= next_month) {
          month_measure.labels.push(current_date.format('MMM-YYYY'));
          month_average_weight = summaryService.get_month_average_weights_for(current_date, weights) || month_average_weight;
          month_average_blood_pressure = summaryService.get_month_average_blood_pressures_for(current_date, blood_pressures) || month_average_blood_pressure;
          month_measure.month_average_measures.push(build_measure_graph_date(month_average_weight, month_average_blood_pressure))
          current_date = current_date.add(1, 'month')
        }
        let year_ago_current_date = dayjs(from_date).subtract(1, 'year');
        let year_ago_next_month = dayjs(next_month).subtract(1, 'year').toDate();
        var year_ago_month_average_weight;
        var year_ago_month_average_blood_pressure;
        while (year_ago_current_date.toDate() <= year_ago_next_month) {
          year_ago_month_average_weight = summaryService.get_month_average_weights_for(year_ago_current_date, weights) || year_ago_month_average_weight;
          year_ago_month_average_blood_pressure = summaryService.get_month_average_blood_pressures_for(year_ago_current_date, blood_pressures) || year_ago_month_average_blood_pressure;
          month_measure.year_ago_month_average_measures.push(build_measure_graph_date(year_ago_month_average_weight, year_ago_month_average_blood_pressure))
          year_ago_current_date = year_ago_current_date.add(1, 'month')
        }
        return month_measure;
      }

      function build_chart_settings(title, color, chart_type, current_data, year_ago_data, labels, tick_formatter) {
        let data = {
          labels: labels,
          datasets: [
            {
              label: 'Current',
              borderColor: color,
              fill: false,
              data: current_data
            }
          ]
        };
        if (chart_type != 'all') {
          data.datasets.push({
            label: 'Year Ago',
            borderColor: 'gray',
            fill: false,
            data: year_ago_data
          });
        }
        return {
          data: data,
          options: {
            plugins: {
              title: {
                display: true,
                text: title
              },
              tooltip: {
                callbacks: {
                  title: format_chart_tooltip_title
                }
              }
            },
            ...(tick_formatter ? {
              scales: {
                y: {
                  ticks: {
                    callback(value) {
                      return tick_formatter(value);
                    }
                  }
                }
              }
            } : {})
          }
        }
      }

      function format_chart_tooltip_title([tooltip_item]) {
        if (tooltip_item.dataset.label !== 'Year Ago') {
          return tooltip_item.label;
        }
        const [month, year] = tooltip_item.label.split('-');
        return `${month}-${Number(year) - 1}`;
      }

      function build_measure_graph_date(weight, blood_pressure) {
        return new MeasureGraphData(
            weight.weight,
            weight.lost_weight,
            weight.fat,
            weight.lost_fat,
            weight.muscle,
            weight.lost_muscle,
            blood_pressure.upper,
            blood_pressure.lower,
            blood_pressure.lost_upper,
            blood_pressure.lost_lower
        );
      }

      function get_measures_from_date(chart_type, weights, blood_pressures) {
        if (chart_type === 'all') {
          return get_first_date_measure(weights, blood_pressures);
        }
        return get_first_date(chart_type)
      }

      function get_routines_from_date(chart_type, routines) {
        if (chart_type === 'all') {
          return get_first_date_routine(routines);
        }
        return get_first_date(chart_type);
      }

      function get_sleeps_from_date(chart_type, sleeps) {
        if (chart_type === 'all') {
          return get_first_date_sleep(sleeps);
        }
        return get_first_date(chart_type);
      }

      function get_moods_from_date(chart_type, moods) {
        if (chart_type === 'all') {
          return get_first_date_mood(moods);
        }
        return get_first_date(chart_type);
      }

      function get_calories_from_date(chart_type, calories) {
        if (chart_type === 'all') {
          return get_first_date_calorie(calories);
        }
        return get_first_date(chart_type);
      }

      function get_first_date(chart_type) {
        if (chart_type === 'monthly') {
          return dayjs().subtract(3, 'month').toDate();
        }
        return dayjs().subtract(1, 'year').toDate();
      }

      function get_first_date_measure(weights, blood_pressures) {
        let first_weight_date = get_first_weight_date(weights);
        let first_blood_pressure_date = get_first_blood_pressure_date(blood_pressures);
        return first_weight_date > first_blood_pressure_date ? first_blood_pressure_date : first_weight_date;
      }

      function get_first_date_routine(routines) {
        return routines.map(r => r.start_date).sort((a, b) => a - b)[0];
      }

      function get_first_date_sleep(sleeps) {
        return sleeps[sleeps.length - 1].date;
      }

      function get_first_date_mood(moods) {
        return moods[moods.length - 1].date;
      }

      function get_first_date_calorie(calories) {
        return calories[calories.length - 1].date;
      }

      function get_first_weight_date(weights) {
        return weights[weights.length - 1].date;
      }

      function get_first_blood_pressure_date(blood_pressures) {
        let last_blood_pressure = blood_pressures[blood_pressures.length - 1];
        return last_blood_pressure ? last_blood_pressure.date : new Date();
      }
    }
  }
}

class MeasureGraphData {
  constructor(weight, lost_weight, fat, lost_fat, muscle, lost_muscle, upper, lower, lost_upper, lost_lower) {
    this.weight = weight;
    this.lost_weight = lost_weight;
    this.fat = fat;
    this.lost_fat = lost_fat;
    this.muscle = muscle;
    this.lost_muscle = lost_muscle;
    this.upper = upper;
    this.lower = lower;
    this.lost_upper = lost_upper;
    this.lost_lower = lost_lower;
  }
}
</script>

<style>
@media (min-width: 1024px) {
  .center {
    display: block;
    margin-left: auto;
    margin-right: auto;
    width: 50%;
  }
}
@media (max-width: 1024px) {
  .center {
    display: block;
    width: 100%;
  }
}
.week-status .p-panel-content {
  overflow-x: auto;
}
.week-status-cell {
  border: thin solid gray;
  text-align: center;
}
.week-ago-cell {
  font-size: small;
  background-color: #dfdada;
}
.dashboard-date-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem;
  border: 1px solid #dce4ea;
  border-radius: 0.625rem;
  margin-bottom: 1rem;
  background: #f8fafc;
  box-shadow: 0 0.25rem 0.75rem rgba(35, 52, 70, 0.08);
}
.dashboard-date-summary {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: max-content;
}
.dashboard-date-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 50%;
  color: #1976d2;
  background: #e7f1fb;
  font-size: 1.125rem;
}
.dashboard-date-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: flex-end;
}
.dashboard-navigation-button,
.dashboard-reflection-button,
.dashboard-completion-button {
  white-space: nowrap;
}
.dashboard-completion-icons {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
}
.dashboard-date-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #666;
}
.dashboard-date-value {
  font-size: 1.25rem;
  font-weight: 700;
}
.dashboard-date-value-row {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}
.dashboard-date-offset {
  display: inline-flex;
  padding: 0.125rem 0.5rem;
  border: 1px solid;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
}
.dashboard-date-offset-behind {
  color: #8a5300;
  background: #fff3cd;
  border-color: #ffe69c;
}
.dashboard-date-offset-today {
  color: #146c43;
  background: #d1e7dd;
  border-color: #a3cfbb;
}
@media (max-width: 768px) {
  .dashboard-date-header {
    flex-direction: column;
    align-items: stretch;
  }
  .dashboard-date-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .dashboard-date-actions .p-button {
    justify-content: center;
  }
  .dashboard-reflection-button,
  .dashboard-completion-button {
    grid-column: 1 / -1;
  }
}
.daily-entry-tab-header {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
}
.missing-daily-entry-icon {
  color: #e91224;
}
.meal-list {
  display: flex;
  flex-direction: column;
}
.meal-list-empty {
  padding: 0.5rem 0;
  color: #666;
}
.meal-entry {
  padding: 0.5rem 0;
}
.meal-entry + .meal-entry {
  border-top: 1px solid #dce4ea;
}
.meal-entry-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  min-width: 0;
  white-space: nowrap;
}
.meal-entry-summary,
.meal-entry-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.meal-entry-actions {
  flex-shrink: 0;
}
.meal-entry-macros {
  display: block;
  margin-top: 0.25rem;
  color: #666;
  font-size: 0.875rem;
}
.meal-total {
  display: flex;
  justify-content: space-between;
  margin: 0.25rem 0 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid #dce4ea;
}
.routine-reminder-dialog {
  width: min(34rem, calc(100vw - 2rem));
  overflow: hidden;
  border: 1px solid #dce4ea;
  border-radius: 1rem;
  box-shadow: 0 1.25rem 3.5rem rgba(35, 52, 70, 0.22);
}
.routine-reminder-dialog .p-dialog-header {
  padding: 1.25rem 1.5rem 0.5rem;
  border-bottom: 0;
  color: #233d4d;
  background: #fff;
}
.routine-reminder-dialog .p-dialog-title {
  font-size: 0.875rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.routine-reminder-dialog .p-dialog-content {
  padding: 0.75rem 1.5rem 1.5rem;
  background: #fff;
}
.routine-reminder-dialog .p-dialog-footer {
  padding: 1rem 1.5rem;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
}
.routine-reminder-dialog-content {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}
.routine-reminder-visual {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 4rem;
  height: 4rem;
  border-radius: 50%;
  color: #1976d2;
  background: #e7f1fb;
  box-shadow: inset 0 0 0 1px #cfe3f7;
  font-size: 1.5rem;
}
.routine-reminder-details {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.routine-reminder-kicker,
.routine-reminder-schedule-label {
  color: #667785;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.routine-reminder-name {
  margin-top: 0.2rem;
  color: #233d4d;
  font-size: 1.5rem;
  line-height: 1.25;
  overflow-wrap: anywhere;
}
.routine-reminder-schedule {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 0.75rem;
  align-items: center;
  margin-top: 1.25rem;
  padding: 0.875rem 1rem;
  border: 1px solid #dce4ea;
  border-radius: 0.75rem;
  background: #f8fafc;
}
.routine-reminder-schedule-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  color: #1976d2;
  background: #e7f1fb;
}
.routine-reminder-schedule-details {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}
.routine-reminder-time {
  color: #233d4d;
  font-size: 1.125rem;
}
.routine-reminder-time-zone {
  padding: 0.3rem 0.55rem;
  border: 1px solid #cfe3f7;
  border-radius: 9999px;
  color: #155a92;
  background: #e7f1fb;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
}
.routine-reminder-dialog-footer,
.routine-reminder-snooze-controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.routine-reminder-dialog-footer {
  justify-content: space-between;
  width: 100%;
}
.routine-reminder-snooze-controls label {
  color: #526471;
  font-size: 0.875rem;
  font-weight: 600;
  white-space: nowrap;
}
.routine-reminder-snooze-controls .p-dropdown {
  width: 8.5rem;
}
.routine-reminder-complete-button {
  white-space: nowrap;
}
@media (max-width: 575px) {
  .routine-reminder-dialog .p-dialog-header {
    padding: 1rem 1.25rem 0.5rem;
  }
  .routine-reminder-dialog .p-dialog-content {
    padding: 0.75rem 1.25rem 1.25rem;
  }
  .routine-reminder-dialog .p-dialog-footer {
    padding: 1rem 1.25rem 1.25rem;
  }
  .routine-reminder-dialog-content {
    grid-template-columns: 1fr;
  }
  .routine-reminder-visual {
    width: 3.5rem;
    height: 3.5rem;
  }
  .routine-reminder-name {
    font-size: 1.35rem;
  }
  .routine-reminder-schedule {
    grid-template-columns: auto minmax(0, 1fr);
  }
  .routine-reminder-time-zone {
    grid-column: 2;
    justify-self: start;
  }
  .routine-reminder-dialog-footer,
  .routine-reminder-snooze-controls {
    align-items: stretch;
    flex-direction: column;
  }
  .routine-reminder-snooze-controls .p-dropdown,
  .routine-reminder-dialog-footer .p-button {
    justify-content: center;
    width: 100%;
  }
}
.back-pain-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
  margin-bottom: 1rem;
}
.back-pain-summary-card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem;
  border: 1px solid #d5d5d5;
  border-radius: 6px;
}
.back-pain-summary-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #666;
}
.back-pain-actions {
  display: flex;
  gap: 0.5rem;
}
@media (max-width: 768px) {
  .back-pain-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.performance-score-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1.5rem;
  padding: 1.25rem;
  margin-bottom: 1rem;
  border: 1px solid #d5d5d5;
  border-radius: 6px;
  background: #fff;
}
.performance-score-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  color: #666;
}
.performance-score-result {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: baseline;
  gap: 1.5rem;
}
.performance-score-value {
  font-size: 3rem;
  font-weight: 700;
  line-height: 1;
}
.performance-score-scale {
  font-size: 1.25rem;
  font-weight: 600;
}
.performance-score-trend {
  font-weight: 600;
}
@media (max-width: 575px) {
  .performance-score-card {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
  }
  .performance-score-result {
    justify-content: space-between;
    gap: 1rem;
  }
  .performance-score-value {
    font-size: 2.5rem;
  }
}
.tab-panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}
.wins-and-misses-header {
  gap: 1rem;
}
.decision-outcome-button {
  width: 7rem;
}
.wins-and-misses-metrics > div {
  min-height: 2.5rem;
  display: flex;
  align-items: center;
}
.workout-comparison {
  display: grid;
  gap: 1rem;
}
.workout-card {
  border: 1px solid #d5d5d5;
  border-radius: 6px;
  padding: 1rem;
}
.workout-card-title {
  font-weight: 700;
  margin-bottom: 0.75rem;
}
.workout-line-list {
  display: grid;
  gap: 0.75rem;
}
.workout-line-item {
  border-top: 1px solid #ececec;
  padding-top: 0.75rem;
}
.workout-line-item:first-child {
  border-top: 0;
  padding-top: 0;
}
.workout-line-title {
  font-weight: 600;
  margin-bottom: 0.35rem;
}
.workout-line-detail,
.workout-line-footer {
  font-size: 0.95rem;
  line-height: 1.4;
}
.workout-line-footer {
  margin-top: 0.35rem;
  color: #666;
}
</style>
