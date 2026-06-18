import { createApp } from 'vue';
import App from './App.vue';
import router from './router';

// Import Bootstrap
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';

// Import design tokens (single source of :root CSS custom properties)
import './assets/design-tokens.css';
// Global styles (toast notifications, scrollbars, utilities)
import './styles/global.css';

// Create Vue app
const app = createApp(App);

// Use plugins
app.use(router);

// Mount app
app.mount('#app');
