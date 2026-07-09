import { createApp } from 'vue';
import App from './App.vue';
import router from './router';

// Import Bootstrap
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';

// Design tokens — the single source of the --color-*/--shadow-*/--radius-* custom
// properties that the views reference via var(...)
import '@shared/assets/design-tokens.css';

// Create Vue app
const app = createApp(App);

// Use plugins
app.use(router);

// Mount app
app.mount('#app');
