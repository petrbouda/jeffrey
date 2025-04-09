import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import VueApexCharts from 'vue3-apexcharts';

// Import Bootstrap
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';

// Import custom styles
import './assets/styles.scss';
import './styles/global.css';
import './styles/tooltips.css';

// Create Vue app
const app = createApp(App);

// Use plugins
app.use(router);
app.component('apexchart', VueApexCharts);

// Mount app
app.mount('#app');
