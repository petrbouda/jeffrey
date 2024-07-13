/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import {createApp} from 'vue';

import PrimeVue from 'primevue/config';
import Button from 'primevue/button';
import ContextMenu from 'primevue/contextmenu';
import Ripple from 'primevue/ripple';
import StyleClass from 'primevue/styleclass';
import VueResizeObserver from "vue-resize-observer";
import Toast from 'primevue/toast';
import ToastService from 'primevue/toastservice';
import InputText from 'primevue/inputtext';
import SelectButton from 'primevue/selectbutton';

import '@/assets/styles.scss';
import SubSecondApp from "@/cli/SubSecondApp.vue";

const app = createApp(SubSecondApp);

app.use(PrimeVue, {ripple: true});
app.use(VueResizeObserver);
app.use(ToastService);

app.directive('ripple', Ripple);
app.directive('styleclass', StyleClass);

app.component('Button', Button);
app.component('ContextMenu', ContextMenu);
app.component('Toast', Toast);
app.component('InputText', InputText);
app.component('SelectButton', SelectButton);

app.mount('#app');
