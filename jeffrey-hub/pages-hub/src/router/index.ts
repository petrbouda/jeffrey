/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { createRouter, createWebHistory } from 'vue-router';
import ServerDashboard from '@/views/server/ServerDashboard.vue';
import SchedulerView from '@/views/server/SchedulerView.vue';
import GrpcApiDocs from '@/views/server/GrpcApiDocs.vue';

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/',
            name: 'server-dashboard',
            component: ServerDashboard
        },
        {
            path: '/scheduler',
            name: 'scheduler',
            component: SchedulerView
        },
        {
            // Storage merged into the Workspaces dashboard; keep old bookmarks working
            path: '/storage',
            redirect: '/'
        },
        {
            path: '/api-docs',
            name: 'api-docs',
            component: GrpcApiDocs
        },
        {
            path: '/:pathMatch(.*)*',
            redirect: '/'
        }
    ]
});

export default router;
