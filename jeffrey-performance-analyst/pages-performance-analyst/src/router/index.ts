/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import { createRouter, createWebHistory } from 'vue-router';
import AppLayout from '@/layout/AppLayout.vue';
import OverviewView from '@/views/OverviewView.vue';
import ProjectsView from '@/views/projects/ProjectsView.vue';
import ProjectPlaceholderView from '@/views/projects/ProjectPlaceholderView.vue';

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/',
            component: AppLayout,
            children: [
                {
                    path: '',
                    name: 'overview',
                    component: OverviewView
                },
                {
                    path: 'workspaces',
                    name: 'workspaces',
                    component: ProjectsView
                },
                {
                    path: 'remote-servers/:serverId/workspaces/:workspaceId/projects/:projectId',
                    name: 'project',
                    component: ProjectPlaceholderView
                }
            ]
        },
        {
            path: '/:pathMatch(.*)*',
            redirect: '/'
        }
    ]
});

export default router;
