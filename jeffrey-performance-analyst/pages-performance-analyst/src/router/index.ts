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
import ProjectDetail from '@/views/projects/detail/ProjectDetail.vue';
import InstancesOverview from '@instances/InstancesOverview.vue';
import InstancesTimeline from '@instances/InstancesTimeline.vue';
import InstanceDetail from '@instances/InstanceDetail.vue';
import RecordingsView from '@/views/global/RecordingsView.vue';

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
                    path: 'recordings',
                    name: 'recordings',
                    component: RecordingsView
                },
                {
                    path: 'hubs/:hubId/workspaces/:workspaceId/projects/:projectId',
                    component: ProjectDetail,
                    children: [
                        {
                            path: '',
                            name: 'project',
                            redirect: to => ({ name: 'project-timeline', params: to.params })
                        },
                        {
                            path: 'instances',
                            name: 'project-instances',
                            component: InstancesOverview
                        },
                        {
                            path: 'instances/timeline',
                            name: 'project-timeline',
                            component: InstancesTimeline
                        },
                        {
                            path: 'instances/:instanceId',
                            name: 'project-instance-detail',
                            component: InstanceDetail
                        }
                    ]
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
