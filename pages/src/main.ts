import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import VueResizeObserver from 'vue-resize-observer';

// Import Bootstrap
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';

// Import custom styles
import './assets/styles.scss';
import './styles/global.css';

// Create Vue app
const app = createApp(App);

// Use plugins
app.use(router);
app.use(VueResizeObserver);

// Mount app
app.mount('#app');