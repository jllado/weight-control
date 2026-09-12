import {createApp} from 'vue';
import App from './App.vue';
import {configure} from 'spike-library';
import 'primeicons/primeicons.css';
const app = createApp(App);
configure(app);
app.mount('#app');
