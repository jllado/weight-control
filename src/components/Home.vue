<template>
  <loading v-model:active="this.state.loading" :can-cancel="false" :is-full-page="true" />
  <div v-if="!this.state.loading">
    <div class="p-grid p-mt-1" >
      <div class="p-col-12" v-if="this.daily_status" >
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
            <div class="p-col-1 week-status-cell"><span :class=this.get_routine_status_color(this.week_status.routines_percentage)>{{ this.week_status.routines_percentage }}</span></div>
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
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class=this.get_routine_status_color(this.week_ago_status.routines_percentage)>{{ this.week_ago_status.routines_percentage }}</span></div>
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
            <div class="p-col-1 week-status-cell"><span :class="this.get_routine_status_color(this.week_status.weight_percentage)">{{ this.week_status.weight_percentage }}</span></div>
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
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_routine_status_color(this.week_ago_status.weight_percentage)">{{ this.week_ago_status.weight_percentage }}</span></div>
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
            <div class="p-col-1 week-status-cell"><span :class="this.get_routine_status_color(this.week_status.blood_pressure_percentage)">{{ this.week_status.blood_pressure_percentage }}</span></div>
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
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_routine_status_color(this.week_ago_status.blood_pressure_percentage)">{{ this.week_ago_status.blood_pressure_percentage }}</span></div>
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
            <div class="p-col-1 week-status-cell"><span :class="this.get_routine_status_color(this.week_status.flexibility_percentage)">{{ this.week_status.flexibility_percentage }}</span></div>
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
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_routine_status_color(this.week_ago_status.flexibility_percentage)">{{ this.week_ago_status.flexibility_percentage }}</span></div>
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
            <div class="p-col-1 week-status-cell"><span :class="this.get_routine_status_color(this.week_status.mind_percentage)">{{ this.week_status.mind_percentage }}</span></div>
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
            <div class="p-col-1 week-status-cell week-ago-cell"><span :class="this.get_routine_status_color(this.week_ago_status.mind_percentage)">{{ this.week_ago_status.mind_percentage }}</span></div>
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
            <div class="p-col-1 week-status-cell">{{ this.format_mood_average(this.week_status.mood_average) }}</div>
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
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_mood_average(this.week_ago_status.mood_average) }}</div>
            <div class="p-col-2" ></div>

            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell">Sleep</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_sleep(this.week_status.saturday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_sleep(this.week_status.sunday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_sleep(this.week_status.monday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_sleep(this.week_status.tuesday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_sleep(this.week_status.wednesday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_sleep(this.week_status.thursday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_sleep(this.week_status.friday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_sleep_average(this.week_status) }}</div>
            <div class="p-col-2" ></div>
            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_sleep(this.week_ago_status.saturday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_sleep(this.week_ago_status.sunday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_sleep(this.week_ago_status.monday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_sleep(this.week_ago_status.tuesday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_sleep(this.week_ago_status.wednesday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_sleep(this.week_ago_status.thursday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_sleep(this.week_ago_status.friday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_sleep_average(this.week_ago_status) }}</div>
            <div class="p-col-2" ></div>

            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell">Calories</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_calories(this.week_status.saturday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_calories(this.week_status.sunday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_calories(this.week_status.monday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_calories(this.week_status.tuesday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_calories(this.week_status.wednesday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_calories(this.week_status.thursday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_calories(this.week_status.friday?.date) }}</div>
            <div class="p-col-1 week-status-cell">{{ this.format_week_calories_average(this.week_status) }}</div>
            <div class="p-col-2" ></div>
            <div class="p-col-1"></div>
            <div class="p-col-1 week-status-cell week-ago-cell">Week ago</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_calories(this.week_ago_status.saturday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_calories(this.week_ago_status.sunday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_calories(this.week_ago_status.monday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_calories(this.week_ago_status.tuesday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_calories(this.week_ago_status.wednesday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_calories(this.week_ago_status.thursday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_calories(this.week_ago_status.friday?.date) }}</div>
            <div class="p-col-1 week-status-cell week-ago-cell">{{ this.format_week_calories_average(this.week_ago_status) }}</div>
            <div class="p-col-2" ></div>
          </div>
        </Panel>
      </div>
      <div class="p-col-12" v-if="this.daily_status" >
        <Panel class="p-panel-content-without-padding">
          <template #header>
            <div class="table-header">
              <span><strong>Status</strong> {{ this.daily_status.dateFormat }}</span>
              <Button icon="pi pi-plus" label="New" @click="new_daily_status" :disabled="this.daily_status.isToday()" />
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
              <span :class="this.get_mood_color(this.daily_status.mood?.value)">{{ this.format_daily_mood(this.daily_status.mood) }}</span>
              <Button class="p-button-text p-ml-2" :icon="this.daily_status.mood ? 'pi pi-pencil' : 'pi pi-plus'" :label="this.daily_status.mood ? 'Edit' : 'Add'" @click="open_mood_modal()" />
              &nbsp;<span v-if="this.get_mood_value_difference(this.daily_status.mood, this.last_week_daily_status.mood) !== null && this.get_mood_value_difference(this.daily_status.mood, this.last_week_daily_status.mood) !== 0" :class="this.get_difference_class(this.get_mood_value_difference(this.daily_status.mood, this.last_week_daily_status.mood))">{{ this.get_mood_value_difference(this.daily_status.mood, this.last_week_daily_status.mood) > 0 ? '+' : '' }}{{ this.get_mood_value_difference(this.daily_status.mood, this.last_week_daily_status.mood) }}</span>
            </div>
            <div class="p-col-4">Trend Mood: </div>
            <div class="p-col-8">
              <span :class="this.get_mood_color(this.get_mood_trend_color_value(this.daily_status.mood_trend))">{{ this.format_mood_average(this.daily_status.mood_trend) }}</span>
              &nbsp;<span v-if="this.get_mood_trend_difference() !== null && this.get_mood_trend_difference() !== 0" :class="this.get_difference_class(this.get_mood_trend_difference())">{{ this.get_mood_trend_difference() > 0 ? '+' : '' }}{{ this.get_mood_trend_difference() }}</span>
            </div>
            <div class="p-col-12"/>
            <div class="p-col-4">Sleep: </div>
            <div class="p-col-8">
              <span>{{ this.format_daily_sleep(this.get_sleep_for(this.daily_status.date)) }}</span>
              <Button class="p-button-text p-ml-2" :icon="this.get_sleep_for(this.daily_status.date) ? 'pi pi-pencil' : 'pi pi-plus'" :label="this.get_sleep_for(this.daily_status.date) ? 'Edit' : 'Add'" @click="open_sleep_modal()" />
              &nbsp;<span v-if="this.get_sleep_duration_difference(this.get_sleep_for(this.daily_status.date), this.get_sleep_for(this.last_week_daily_status.date)) !== null && this.get_sleep_duration_difference(this.get_sleep_for(this.daily_status.date), this.get_sleep_for(this.last_week_daily_status.date)) !== 0" :class="this.get_difference_class(this.get_sleep_duration_difference(this.get_sleep_for(this.daily_status.date), this.get_sleep_for(this.last_week_daily_status.date)))">{{ this.format_sleep_trend(this.get_sleep_duration_difference(this.get_sleep_for(this.daily_status.date), this.get_sleep_for(this.last_week_daily_status.date))) }}</span>
            </div>
            <div class="p-col-4">Trend Sleep: </div>
            <div class="p-col-8">
              <span v-if="this.current_sleep_trend" :class="this.get_sleep_trend_class(this.current_sleep_trend.lostTotalSleepDuration)">{{ this.format_sleep_trend(this.current_sleep_trend.lostTotalSleepDuration) }}</span>
              <span v-else>Not enough data</span>
            </div>
            <div class="p-col-12"/>
            <div class="p-col-4">Average Heart Rate: </div>
            <div class="p-col-8">
              <span>{{ this.get_sleep_for(this.daily_status.date) ? this.get_sleep_for(this.daily_status.date).heartRateFormat() : 'Not recorded' }}</span>
            </div>
            <div class="p-col-12"/>
            <div class="p-col-4">HRV: </div>
            <div class="p-col-8">
              <span>{{ this.get_sleep_for(this.daily_status.date) ? this.get_sleep_for(this.daily_status.date).hrvFormat() : 'Not recorded' }}</span>
            </div>
            <div class="p-col-12"/>
            <div class="p-col-4">Calories: </div>
            <div class="p-col-8">
              <span>{{ this.format_daily_calories(this.get_calorie_for(this.daily_status.date)) }}</span>
              <Button class="p-button-text p-ml-2" :icon="this.get_calorie_for(this.daily_status.date) ? 'pi pi-pencil' : 'pi pi-plus'" :label="this.get_calorie_for(this.daily_status.date) ? 'Edit' : 'Add'" @click="open_calorie_modal()" />
            </div>
            <div class="p-col-4">Trend Calories: </div>
            <div class="p-col-8">
              <span v-if="this.current_calorie_trend" :class="this.get_calorie_trend_class(this.current_calorie_trend.lostCalories)">{{ this.format_calorie_trend(this.current_calorie_trend.lostCalories) }}</span>
              <span v-else>Not enough data</span>
            </div>
            <div class="p-col-12">
              <hr class="status-panel-divider">
            </div>
            <div class="p-col-12">
              <strong>Sleep Charts</strong>
            </div>
            <div class="p-col-12" v-if="this.sleep_heart_rate_chart_data && this.sleep_hrv_chart_data">
              <Chart type="line" :data="sleep_heart_rate_chart_data.data" :options="sleep_heart_rate_chart_data.options" :height="175" />
              <Chart type="line" :data="sleep_hrv_chart_data.data" :options="sleep_hrv_chart_data.options" :height="175" />
            </div>
            <div class="p-col-12" v-else>No sleep metric data yet.</div>
          </div>
        </Panel>
      </div>
      <MoodForm @onSave="refresh_daily_status" @onClose="close_mood_modal" v-model:show="display_mood_modal" v-model:mood="mood" :initial_date="mood_initial_date" />
      <SleepForm @onSave="refresh_sleep_status" @onClose="close_sleep_modal" v-model:show="display_sleep_modal" v-model:sleep="sleep" :initial_date="sleep_initial_date" />
      <CalorieForm @onSave="refresh_calorie_status" @onClose="close_calorie_modal" v-model:show="display_calorie_modal" v-model:calorie="calorie" :initial_date="calorie_initial_date" />
      <div class="p-col-12" v-if="this.daily_status && habits.length > 0" >
        <Panel>
          <template #header>
            <div class="table-header">
              <strong>Habits ({{this.habits.length}})</strong>
            </div>
          </template>
          <DataTable :value="this.habits" responsiveLayout="scroll"
                     paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                     currentPageReportTemplate="{first} to {last} of {totalRecords}" >
            <Column headerStyle="width: 55px" bodyStyle="text-align: center" >
              <template #body="habit">
                <Button icon="pi pi-plus" class="p-button-rounded p-button-success" @click="plusHabit(habit.data)" :disabled="habit.data.isDisabled(this.daily_status.date)" />
              </template>
            </Column>
            <Column>
              <template #body="habit" >
                {{ habit.data.name }}
              </template>
            </Column>
            <Column header="Strike" headerStyle="width: 80px" bodyStyle="text-align: center" >
              <template #body="habit" >
                {{ habit.data.print_strike() }}
              </template>
            </Column>
            <Column  headerStyle="width: 80px" bodyStyle="text-align: center" >
              <template #body="habit" >
                {{ habit.data.daily_percentage() }}%
              </template>
            </Column>
          </DataTable>
        </Panel>
      </div>
      <div class="p-col-12" v-if="this.daily_status && routines.length > 0" >
        <Panel class="p-panel-content-without-padding" >
          <template #header>
            <div class="table-header">
              <strong>Routines ({{this.routines.length}})</strong>
            </div>
          </template>
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
      </div>
      <div class="p-col-12">
        <Panel>
          <template #header>
            <div class="table-header">
              <strong>Last Weight</strong>
              <CreateWeight @onSave="load_all" />
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
            <div class="p-col-5">Current Lost Trend: </div>
            <div class="p-col-7"><span v-bind:class="{'bad': current_weight_trend.lost_weight > 0, 'good': current_weight_trend.lost_weight <= 0}">{{ current_weight_trend.lost_weight > 0 ? '+' : '' }}{{ current_weight_trend.lost_weight }}kg</span> per month</div>
            <div class="p-col-5">Strike: </div>
            <div class="p-col-7">{{ current_weight_strike }} days below {{ last_weight.range() }} kg</div>
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
              <CreateBloodPressure @onSave="load_all" />
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
              <strong>Last Sleep</strong>
              <CreateSleep @onSave="load_all" />
            </div>
          </template>
          <div class="p-grid" v-if="last_sleep">
            <div class="p-col-5">Date: </div>
            <div class="p-col-7">{{ last_sleep.dateFormat }}</div>
            <div class="p-col-5">Bedtime: </div>
            <div class="p-col-7">{{ last_sleep.bedtimeWindowFormat() }}</div>
            <div class="p-col-5">Total Sleep: </div>
            <div class="p-col-7">{{ last_sleep.totalSleepDurationFormat() }}</div>
            <div class="p-col-5">Deep / REM / Light: </div>
            <div class="p-col-7">{{ last_sleep.deepSleepDurationFormat() }} / {{ last_sleep.remSleepDurationFormat() }} / {{ last_sleep.lightSleepDurationFormat() }}</div>
            <div class="p-col-5">Awake: </div>
            <div class="p-col-7">{{ last_sleep.awakeTimeFormat() }}</div>
            <div class="p-col-5">Average Heart Rate: </div>
            <div class="p-col-7">{{ last_sleep.heartRateFormat() }}</div>
            <div class="p-col-5">Average HRV: </div>
            <div class="p-col-7">{{ last_sleep.hrvFormat() }}</div>
            <div class="p-col-5">Current Total Sleep Trend: </div>
            <div class="p-col-7" v-if="current_sleep_trend">
              <span :class="get_sleep_trend_class(current_sleep_trend.lostTotalSleepDuration)">
                {{ format_sleep_trend(current_sleep_trend.lostTotalSleepDuration) }}
              </span> per month
            </div>
            <div class="p-col-7" v-else>Not enough data</div>
          </div>
        </Panel>
      </div>
    </div>
    <div class="p-grid p-mt-1" v-if="weight_chart_data || sleep_total_chart_data || calorie_chart_data || routines_chart_data" >
      <div class="p-col-4 p-text-right">
        <RadioButton id="chat_type2" name="chat_type" value="monthly" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type3" class="p-ml-1">Monthly</label>
      </div>
      <div class="p-col-4 p-text-center">
        <RadioButton id="chat_type1" name="chat_type" value="last_year" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type1" class="p-ml-1">Year</label>
      </div>
      <div class="p-col-4 p-text-left">
        <RadioButton id="chat_type2" name="chat_type" value="all" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type2" class="p-ml-1">All</label>
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
import habitService from '../services/HabitService';
import routineService from '../services/RoutineService';
import weightService from '../services/WeightService';
import summaryService from '../services/MeasuresSummaryService';
import dashboardService from '../services/DashboardService';
import bloodPressureService from '../services/BloodPressureService';
import sleepService from '../services/SleepService';
import calorieService from '../services/CalorieService';
import CreateWeight from "@/components/CreateWeight";
import CreateBloodPressure from "@/components/CreateBloodPressure";
import CreateSleep from "@/components/CreateSleep";
import MoodForm from "@/components/MoodForm";
import SleepForm from "@/components/SleepForm";
import CalorieForm from "@/components/CalorieForm";
import dayjs from 'dayjs';
import anychart from 'anychart/dist/js/anychart-base.min'
import anychartLinearGauge from 'anychart/dist/js/anychart-linear-gauge.min'
import {formatDuration, formatTimeOfDayFromMinutes} from "@/model/Sleep";

const isToday = require('dayjs/plugin/isToday');
dayjs.extend(isToday)

export default {
  components: {CreateWeight, CreateBloodPressure, CreateSleep, MoodForm, SleepForm, CalorieForm},
  data() {
    return {
      routines: [],
      habits: [],
      weights: [],
      blood_pressures: [],
      sleeps: [],
      calories: [],
      daily_status: undefined,
      week_status: undefined,
      week_ago_status: undefined,
      last_week_daily_status: undefined,
      last_weight: undefined,
      last_blood_pressure: undefined,
      last_sleep: undefined,
      current_blood_pressure_trend: undefined,
      current_weight_trend: undefined,
      current_sleep_trend: undefined,
      current_calorie_trend: undefined,
      current_weight_strike: undefined,
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
      calorie_chart_data: undefined,
      fat_status_bar: undefined,
      bmi_status_bar: undefined,
      display_mood_modal: false,
      mood: null,
      mood_initial_date: null,
      display_sleep_modal: false,
      sleep: null,
      sleep_initial_date: null,
      display_calorie_modal: false,
      calorie: null,
      calorie_initial_date: null,
      reflection_status: null,
      reflection: null,
      reflection_visible: false,
      reflection_loading: false,
      routine_action_loading_id: null,
      state: userState()
    }
  },
  async mounted() {
    this.state.loading = true;
    await this.load_all();
    await nextTick();
    if (this.last_weight) {
      await this.init_fat_status_bar();
      await this.init_bmi_status_bar();
    }
    this.state.loading = false;
  },
  methods: {
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
    async load_all_habits() {
      this.habits = await this.get_pending_habits();
    },
    get_day_mood(day) {
      return day?.mood ? day.mood.emoji() : '';
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
      return `${Math.round(value * 100) / 100}/5`;
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
      if (!currentMood || !lastMood) {
        return null;
      }
      return currentMood.value - lastMood.value;
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
    get_calorie_trend_class(value) {
      return {
        good: value < 0,
        bad: value > 0
      };
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
    format_week_sleep(date) {
      const sleep = this.get_sleep_for(date);
      if (!sleep) {
        return '';
      }
      return sleep.totalSleepDurationFormat();
    },
    format_week_sleep_average(weekStatus) {
      const sleeps = this.get_week_sleeps(weekStatus);
      if (sleeps.length === 0) {
        return 'Not recorded';
      }
      const average = sleeps.reduce((total, sleep) => total + sleep.totalSleepDuration, 0) / sleeps.length;
      return formatDuration(Math.round(average));
    },
    format_week_calories(date) {
      const calorie = this.get_calorie_for(date);
      if (!calorie) {
        return '';
      }
      return `${calorie.calories} kcal`;
    },
    format_week_calories_average(weekStatus) {
      const calories = this.get_week_calories(weekStatus);
      if (calories.length === 0) {
        return 'Not recorded';
      }
      const average = calories.reduce((total, calorie) => total + calorie.calories, 0) / calories.length;
      return `${Math.round(average * 100) / 100} kcal`;
    },
    get_sleep_for(date) {
      if (!date) {
        return null;
      }
      return this.sleeps.find(sleep => dayjs(sleep.date).isSame(date, 'day')) || null;
    },
    get_calorie_for(date) {
      if (!date) {
        return null;
      }
      return this.calories.find(calorie => dayjs(calorie.date).isSame(date, 'day')) || null;
    },
    get_week_sleeps(weekStatus) {
      return [
        weekStatus?.saturday?.date,
        weekStatus?.sunday?.date,
        weekStatus?.monday?.date,
        weekStatus?.tuesday?.date,
        weekStatus?.wednesday?.date,
        weekStatus?.thursday?.date,
        weekStatus?.friday?.date
      ].map(date => this.get_sleep_for(date)).filter(sleep => sleep);
    },
    get_week_calories(weekStatus) {
      return [
        weekStatus?.saturday?.date,
        weekStatus?.sunday?.date,
        weekStatus?.monday?.date,
        weekStatus?.tuesday?.date,
        weekStatus?.wednesday?.date,
        weekStatus?.thursday?.date,
        weekStatus?.friday?.date
      ].map(date => this.get_calorie_for(date)).filter(calorie => calorie);
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
    open_mood_modal() {
      this.mood = this.daily_status.mood ? Object.assign({}, this.daily_status.mood) : null;
      this.mood_initial_date = this.daily_status.date;
      this.display_mood_modal = true;
    },
    close_mood_modal() {
      this.display_mood_modal = false;
      this.mood = null;
    },
    open_sleep_modal() {
      this.sleep = this.get_sleep_for(this.daily_status.date);
      this.sleep = this.sleep ? Object.assign({}, this.sleep) : null;
      this.sleep_initial_date = this.daily_status.date;
      this.display_sleep_modal = true;
    },
    close_sleep_modal() {
      this.display_sleep_modal = false;
      this.sleep = null;
    },
    open_calorie_modal() {
      this.calorie = this.get_calorie_for(this.daily_status.date);
      this.calorie = this.calorie ? Object.assign({}, this.calorie) : null;
      this.calorie_initial_date = this.daily_status.date;
      this.display_calorie_modal = true;
    },
    close_calorie_modal() {
      this.display_calorie_modal = false;
      this.calorie = null;
    },
    get_current_date() {
      return this.daily_status.date;
    },
    get_difference(a, b) {
      return Math.round((a - b) * 100) / 100;
    },
    async new_daily_status() {
      const dashboard = await dashboardService.advance();
      this.apply_dashboard(dashboard);
    },
    async refresh_daily_status() {
      const dashboard = await dashboardService.refresh();
      this.apply_dashboard(dashboard);
    },
    async refresh_sleep_status() {
      await this.load_all_sleeps();
      await this.refresh_daily_status();
      await this.load_chart_data();
      await this.load_current_trend();
    },
    async refresh_calorie_status() {
      await this.load_all_calories();
      await this.load_current_trend();
      await this.load_chart_data();
    },
    async load_status() {
      this.apply_dashboard(await dashboardService.get());
    },
    apply_dashboard(dashboard) {
      this.daily_status = dashboard.dailyStatus;
      this.last_week_daily_status = dashboard.lastWeekDailyStatus;
      this.week_status = dashboard.weekStatus;
      this.week_ago_status = dashboard.weekAgoStatus;
    },
    async get_pending_habits() {
      let all_habits = await habitService.get_all_by(this.state.user.mail);
      return all_habits.filter(h => h.isPending());
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
    async plusHabit(habit) {
      await habitService.complete(habit.id, this.get_current_date())
          .then(() => {
            this.$toast.add({severity:'success', summary: 'Habit done it', life: 3000});
          })
          .catch(e => {
            this.handle_error(e)
          });
      await this.load_all_habits();
      this.$confetti.start();
      setTimeout(function (){
        this.$confetti.stop();
      }.bind(this), 2000);
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
    async load_all_sleeps() {
      this.sleeps = await sleepService.get_all();
      this.last_sleep = this.sleeps[0];
    },
    async load_all_calories() {
      this.calories = await calorieService.get_all();
    },
    async load_all() {
      await this.load_all_habits();
      await this.load_all_routines();
      await this.load_all_weights();
      await this.load_all_blood_pressures();
      await this.load_all_sleeps();
      await this.load_all_calories();
      await this.load_status();
      await this.load_chart_data();
      await this.load_current_trend();
      if (this.last_weight && this.current_weight_trend) {
        this.load_current_weight_strike();
        this.load_months_next_range();
      }
      this.set_fat_status_bar_data();
      this.set_bmi_status_bar_data();
    },
    async load_current_trend() {
      this.current_weight_trend = summaryService.get_weight_trend(this.weights);
      this.current_blood_pressure_trend = summaryService.get_blood_pressure_trend(this.blood_pressures);
      this.current_sleep_trend = summaryService.get_sleep_trend(this.sleeps);
      this.current_calorie_trend = summaryService.get_calorie_trend(this.calories);
    },
    load_current_weight_strike() {
      let range = this.last_weight.range();
      this.current_weight_strike = summaryService.get_weight_strike_days(range, this.weights);
    },
    load_months_next_range() {
      this.months_next_range = this.last_weight.months_next_range(this.current_weight_trend)
    },
    load_chart_data: async function () {
      if (!this.last_weight && !this.last_sleep && this.routines.length === 0 && this.calories.length === 0) {
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
      if (this.calories.length > 0) {
        let calorie_from_date = get_calories_from_date(this.chart_type, this.calories);
        let month_calories = get_month_calories_from(calorie_from_date, this.calories);
        this.calorie_chart_data = build_month_calorie_chart(month_calories, this.chart_type);
      } else {
        this.calorie_chart_data = undefined;
      }
      this.state.loading = false;

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
.status-panel-divider {
  margin: 0.5rem 0;
  border: 0;
  border-top: 1px solid #dfdada;
}
</style>
