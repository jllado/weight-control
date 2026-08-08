<template>
  <article class="routine-analytics-card">
    <header class="routine-card-header">
      <div>
        <h2>{{ routine.name }}</h2>
        <div class="routine-types">
          <span v-for="type in routine.types" :key="type.name" class="routine-type">{{ format_type(type.name) }}</span>
        </div>
      </div>
      <span class="routine-start-date">Since {{ routine.start_date_format }}</span>
    </header>

    <div class="routine-metrics">
      <div class="routine-metric">
        <span class="routine-metric-value">{{ routine.times.length }}</span>
        <span class="routine-metric-label">Completions</span>
      </div>
      <div class="routine-metric">
        <span class="routine-metric-value">{{ current_streak }}</span>
        <span class="routine-metric-label">Current streak</span>
      </div>
      <div class="routine-metric">
        <span class="routine-metric-value">{{ routine.best_strike }}</span>
        <span class="routine-metric-label">Best streak</span>
      </div>
    </div>

    <section class="routine-heatmap-section" aria-label="Daily completion heatmap">
      <div class="routine-section-header">
        <strong>Daily completion</strong>
        <span>{{ heatmap_range }}</span>
      </div>
      <div class="routine-heatmap-scroll">
        <div class="routine-heatmap-layout">
          <div></div>
          <div class="routine-heatmap-months" :style="heatmap_grid_style">
            <span v-for="month in heatmap_months" :key="month.key" class="routine-heatmap-month" :style="month.style">{{ month.label }}</span>
          </div>
          <div class="routine-heatmap-weekdays" aria-hidden="true">
            <span>Mon</span>
            <span></span>
            <span>Wed</span>
            <span></span>
            <span>Fri</span>
            <span></span>
            <span></span>
          </div>
          <div class="routine-heatmap-grid" :style="heatmap_grid_style">
            <span
                v-for="day in heatmap_days"
                :key="day.key"
                class="routine-heatmap-day"
                :class="[`routine-heatmap-day-${day.status}`, {'routine-heatmap-day-today': day.today}]"
                :title="day.label"
                :aria-label="day.label || undefined"
                :aria-hidden="day.status === 'outside' ? 'true' : undefined"
                :role="day.status === 'outside' ? undefined : 'img'"
            ></span>
          </div>
        </div>
      </div>
      <div class="routine-heatmap-legend" aria-hidden="true">
        <span><i class="routine-heatmap-day routine-heatmap-day-inactive"></i>Not started</span>
        <span><i class="routine-heatmap-day routine-heatmap-day-missed"></i>Missed</span>
        <span><i class="routine-heatmap-day routine-heatmap-day-completed"></i>Completed</span>
      </div>
    </section>

    <div class="routine-trend-chart">
      <Chart type="line" :data="chart_data" :options="chart_options" :height="210" />
    </div>
  </article>
</template>

<script>
import dayjs from 'dayjs';

export default {
  name: 'RoutineAnalyticsCard',
  props: {
    routine: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      today: dayjs().startOf('day')
    };
  },
  computed: {
    window_start() {
      return this.today.subtract(11, 'month').startOf('month');
    },
    grid_start() {
      return this.window_start.subtract((this.window_start.day() + 6) % 7, 'day');
    },
    grid_end() {
      return this.today.add(6 - ((this.today.day() + 6) % 7), 'day');
    },
    heatmap_week_count() {
      return this.grid_end.diff(this.grid_start, 'week') + 1;
    },
    heatmap_grid_style() {
      return {'--heatmap-weeks': this.heatmap_week_count};
    },
    heatmap_range() {
      return `${this.window_start.format('MMM YYYY')} – ${this.today.format('MMM YYYY')}`;
    },
    routine_start() {
      return dayjs(this.routine.start_date).startOf('day');
    },
    checkin_dates() {
      return new Set(this.routine.times.map(time => dayjs(time).format('YYYY-MM-DD')));
    },
    monthly_checkin_counts() {
      let counts = new Map();
      this.checkin_dates.forEach(date => {
        const month = date.slice(0, 7);
        counts.set(month, (counts.get(month) || 0) + 1);
      });
      return counts;
    },
    heatmap_months() {
      let month = this.window_start;
      let months = [];
      while (!month.isAfter(this.today, 'month')) {
        const column = Math.floor(month.diff(this.grid_start, 'day') / 7) + 1;
        const next_column = Math.min(Math.floor(month.add(1, 'month').diff(this.grid_start, 'day') / 7) + 1, this.heatmap_week_count + 1);
        months.push({
          key: month.format('YYYY-MM'),
          label: month.format('MMM'),
          style: {gridColumn: `${column} / span ${Math.max(1, next_column - column)}`}
        });
        month = month.add(1, 'month');
      }
      return months;
    },
    heatmap_days() {
      let date = this.grid_start;
      let days = [];
      while (!date.isAfter(this.grid_end, 'day')) {
        const status = this.get_day_status(date);
        days.push({
          key: date.format('YYYY-MM-DD'),
          status,
          today: date.isSame(this.today, 'day'),
          label: status === 'outside' ? '' : `${date.format('D MMMM YYYY')}: ${this.format_day_status(status)}`
        });
        date = date.add(1, 'day');
      }
      return days;
    },
    current_streak() {
      return this.routine.strike(this.today.toDate());
    },
    monthly_completion() {
      let month = this.window_start;
      let completion = [];
      while (!month.isAfter(this.today, 'month')) {
        const month_end = month.endOf('month');
        if (month_end.isBefore(this.routine_start, 'day')) {
          completion.push({label: month.format('MMM YYYY'), percentage: null});
        } else {
          const active_start = this.routine_start.isAfter(month, 'day') ? this.routine_start : month;
          const active_end = this.today.isBefore(month_end, 'day') ? this.today : month_end;
          const active_days = active_end.diff(active_start, 'day') + 1;
          const completed_days = this.monthly_checkin_counts.get(month.format('YYYY-MM')) || 0;
          completion.push({label: month.format('MMM YYYY'), percentage: Math.round(completed_days * 10000 / active_days) / 100});
        }
        month = month.add(1, 'month');
      }
      return completion;
    },
    chart_data() {
      return {
        labels: this.monthly_completion.map(month => month.label),
        datasets: [{
          label: 'Completion',
          data: this.monthly_completion.map(month => month.percentage),
          borderColor: '#1976d2',
          backgroundColor: 'rgba(25, 118, 210, 0.12)',
          pointBackgroundColor: '#1976d2',
          pointRadius: 3,
          tension: 0.25,
          fill: true,
          spanGaps: false
        }]
      };
    },
    chart_options() {
      return {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
          intersect: false,
          mode: 'index'
        },
        plugins: {
          legend: {
            display: false
          },
          title: {
            display: true,
            text: 'Monthly completion'
          },
          tooltip: {
            callbacks: {
              label(context) {
                return `${context.parsed.y}% completion`;
              }
            }
          }
        },
        scales: {
          x: {
            grid: {
              display: false
            }
          },
          y: {
            min: 0,
            max: 100,
            ticks: {
              stepSize: 25,
              callback(value) {
                return `${value}%`;
              }
            }
          }
        }
      };
    }
  },
  methods: {
    get_day_status(date) {
      if (date.isBefore(this.window_start, 'day') || date.isAfter(this.today, 'day')) {
        return 'outside';
      }
      if (date.isBefore(this.routine_start, 'day')) {
        return 'inactive';
      }
      return this.checkin_dates.has(date.format('YYYY-MM-DD')) ? 'completed' : 'missed';
    },
    format_day_status(status) {
      return {
        inactive: 'Routine not started',
        missed: 'Missed',
        completed: 'Completed'
      }[status];
    },
    format_type(type) {
      return type.toLowerCase().split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
    }
  }
};
</script>

<style scoped>
.routine-analytics-card {
  min-width: 0;
  padding: 1.25rem;
  border: 1px solid #dce4ea;
  border-radius: 0.625rem;
  background: #fff;
  box-shadow: 0 0.25rem 0.75rem rgba(35, 52, 70, 0.08);
}
.routine-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}
.routine-card-header h2 {
  margin: 0 0 0.5rem;
  font-size: 1.25rem;
}
.routine-types {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}
.routine-type {
  padding: 0.2rem 0.5rem;
  border-radius: 9999px;
  color: #155a92;
  background: #e7f1fb;
  font-size: 0.7rem;
  font-weight: 600;
}
.routine-start-date {
  color: #666;
  font-size: 0.75rem;
  white-space: nowrap;
}
.routine-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin-bottom: 1.25rem;
}
.routine-metric {
  display: flex;
  flex-direction: column;
  padding: 0.75rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  background: #f8fafc;
}
.routine-metric-value {
  color: #233d4d;
  font-size: 1.5rem;
  font-weight: 700;
}
.routine-metric-label {
  color: #666;
  font-size: 0.7rem;
  text-transform: uppercase;
}
.routine-heatmap-section {
  margin-bottom: 1rem;
}
.routine-section-header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
  color: #666;
  font-size: 0.75rem;
}
.routine-section-header strong {
  color: #233d4d;
  font-size: 0.875rem;
}
.routine-heatmap-scroll {
  overflow-x: auto;
  padding-bottom: 0.375rem;
}
.routine-heatmap-layout {
  display: grid;
  grid-template-columns: 1.75rem max-content;
  grid-template-rows: 1rem auto;
  width: max-content;
}
.routine-heatmap-months {
  display: grid;
  grid-template-columns: repeat(var(--heatmap-weeks), 0.625rem);
  gap: 0.125rem;
}
.routine-heatmap-month {
  overflow: hidden;
  color: #666;
  font-size: 0.625rem;
  white-space: nowrap;
}
.routine-heatmap-weekdays {
  display: grid;
  grid-template-rows: repeat(7, 0.625rem);
  gap: 0.125rem;
  color: #666;
  font-size: 0.55rem;
  line-height: 0.625rem;
}
.routine-heatmap-grid {
  display: grid;
  grid-template-columns: repeat(var(--heatmap-weeks), 0.625rem);
  grid-template-rows: repeat(7, 0.625rem);
  grid-auto-flow: column;
  gap: 0.125rem;
}
.routine-heatmap-day {
  display: inline-block;
  width: 0.625rem;
  height: 0.625rem;
  border: 1px solid transparent;
  border-radius: 0.125rem;
  box-sizing: border-box;
}
.routine-heatmap-day-outside {
  visibility: hidden;
}
.routine-heatmap-day-inactive {
  border-color: #edf1f5;
  background: #f8fafc;
}
.routine-heatmap-day-missed {
  border-color: #d8e0e7;
  background: #e2e8f0;
}
.routine-heatmap-day-completed {
  border-color: #1565a8;
  background: #1976d2;
}
.routine-heatmap-day-today {
  outline: 2px solid #f59e0b;
  outline-offset: 1px;
}
.routine-heatmap-legend {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
  color: #666;
  font-size: 0.7rem;
}
.routine-heatmap-legend span {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}
.routine-trend-chart {
  height: 13.125rem;
}
@media (max-width: 575px) {
  .routine-analytics-card {
    padding: 1rem;
  }
  .routine-card-header {
    flex-direction: column;
    gap: 0.25rem;
  }
  .routine-metrics {
    gap: 0.375rem;
  }
  .routine-metric {
    padding: 0.5rem;
  }
  .routine-metric-value {
    font-size: 1.25rem;
  }
  .routine-metric-label {
    font-size: 0.6rem;
  }
}
</style>
