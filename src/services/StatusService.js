import dashboardService from './DashboardService';

export default {
    async get_dashboard() {
        return dashboardService.get();
    },
    async advance() {
        return dashboardService.advance();
    },
    async refresh() {
        return dashboardService.refresh();
    }
}
