import { createApp } from 'vue';
import {reactive} from 'vue';
import App from './App.vue';
import router from './router';
import { stateSymbol, createState } from './state';
import VueLogger from 'vuejs3-logger';
import VueConfetti from 'vue-confetti';
import Menubar from 'primevue/menubar';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Panel from 'primevue/panel';
import Dialog from 'primevue/dialog';
import InputNumber from 'primevue/inputnumber';
import InputText from 'primevue/inputtext';
import Password from 'primevue/password';
import Button from 'primevue/button';
import Dropdown from 'primevue/dropdown';
import TabView from 'primevue/tabview';
import TabPanel from 'primevue/tabpanel';
import Message from 'primevue/message';
import PickList from 'primevue/picklist';
import ToastService from 'primevue/toastservice';
import Toast from 'primevue/toast';
import Calendar from 'primevue/calendar';
import Chart from 'primevue/chart';
import RadioButton from 'primevue/radiobutton';

import 'primevue/resources/themes/nova/theme.css';
import 'primevue/resources/primevue.min.css';
import 'primeflex/primeflex.min.css';
import 'primeicons/primeicons.css';
import CreateWeight from "@/components/CreateWeight";
import WeightForm from "@/components/WeightForm";
import Loading from 'vue3-loading-overlay';
import 'vue3-loading-overlay/dist/vue3-loading-overlay.css';

const app = createApp(App);

app.config.globalProperties.$appState = reactive({inputStyle: 'outlined', darkTheme: false});

app.component('DataTable', DataTable);
app.component('Column', Column);
app.component('Panel', Panel);
app.component('Menubar', Menubar);
app.component('Dialog', Dialog);
app.component('InputNumber', InputNumber);
app.component('InputText', InputText);
app.component('Password', Password);
app.component('Button', Button);
app.component('Dropdown', Dropdown);
app.component('TabView', TabView);
app.component('TabPanel', TabPanel);
app.component('PickList', PickList);
app.component('Calendar', Calendar);
app.component('Toast', Toast);
app.component('CreateWeight', CreateWeight);
app.component('Message', Message);
app.component('WeightForm', WeightForm);
app.component('Loading', Loading);
app.component('Chart', Chart);
app.component('RadioButton', RadioButton);

const options = {
    isEnabled: true,
    logLevel : process.env.NODE_ENV === 'production' ? 'info' : 'debug',
    stringifyArguments : false,
    showLogLevel : true,
    showMethodName : false,
    separator: '|',
    showConsoleColors: true
};

app.provide(stateSymbol, createState());
app.use(VueLogger, options);
app.use(VueConfetti);
app.use(router);
app.use(ToastService);
app.mount('#app');

const shouldSW = 'serviceWorker' in navigator;
if (shouldSW) {
    navigator.serviceWorker.register('/service-worker.js').then(() => {
        console.log("Service Worker Registered!")
    })
}
