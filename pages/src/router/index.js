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

import {createRouter, createWebHashHistory} from 'vue-router';
import AppLayout from '@/layout/AppLayout.vue';
import LayoutHeaderOnly from "@/layout/LayoutHeaderOnly.vue";

const router = createRouter({
    history: createWebHashHistory(),
    routes: [
        {
            path: '/projects',
            component: LayoutHeaderOnly,
            children: [
                {
                    path: '/projects/:projectId',
                    component: () => import('@/views/project/Project.vue'),
                    children: [
                        {
                            path: '/projects/:projectId/profiles',
                            name: 'projects-profiles',
                            component: () => import('@/views/project/Profiles.vue'),
                        },
                        {
                            path: '/projects/:projectId/recordings',
                            name: 'projects-recordings',
                            component: () => import('@/views/project/Recordings.vue'),
                        },
                        {
                            path: '/projects/:projectId/repository',
                            name: 'projects-repository',
                            component: () => import('@/views/project/Repository.vue'),
                        },
                        {
                            path: '/projects/:projectId/scheduler',
                            name: 'projects-scheduler',
                            component: () => import('@/views/project/Scheduler.vue'),
                        },
                        {
                            path: '/projects/:projectId/settings',
                            name: 'projects-settings',
                            component: () => import('@/views/project/Settings.vue'),
                        },
                    ]
                }
            ]
        },
        {
            path: '/',
            component: LayoutHeaderOnly,
            children: [
                {
                    path: '/',
                    name: 'index',
                    component: () => import('@/views/Index.vue')
                },
            ]
        },
        {
            path: '/',
            component: AppLayout,
            children: [
                {
                    path: '/projects/:projectId/profiles/:profileId/flamegraph',
                    name: 'flamegraph',
                    component: () => import('@/views/flamegraph/Flamegraph.vue'),
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/flamegraph-sections',
                    name: 'flamegraph-sections',
                    component: () => import('@/views/flamegraph/FlamegraphSections.vue'),
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/diff-flamegraph-sections',
                    name: 'diff-flamegraph-sections',
                    component: () => import('@/views/flamegraph/DiffFlamegraphSections.vue'),
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/subsecond-sections',
                    name: 'subsecond-sections',
                    component: () => import('@/views/subsecond/SubSecondSections.vue')
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/diff-subsecond-sections',
                    name: 'diff-subsecond-sections',
                    component: () => import('@/views/subsecond/DiffSubSecondSections.vue')
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/subsecond',
                    name: 'subsecond',
                    component: () => import('@/views/subsecond/SubSecond.vue')
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/savedgraphs',
                    name: 'saved-graphs',
                    component: () => import('@/views/profile/SavedGraphs.vue')
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/threads',
                    name: 'threads',
                    component: () => import('@/views/profile/Threads.vue')
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/showSimple',
                    name: 'flamegraph-simple',
                    component: () => import('@/views/flamegraph/ShowSimple.vue')
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/information',
                    name: 'profile-information',
                    component: () => import('@/views/profile/Information.vue')
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/guardian',
                    name: 'profile-guardian',
                    component: () => import('@/views/profile/Guardian.vue')
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/autoAnalysis',
                    name: 'profile-auto-analysis',
                    component: () => import('@/views/profile/AutoAnalysis.vue')
                },
                {
                    path: '/projects/:projectId/profiles/:profileId/eventViewer',
                    name: 'event-viewer',
                    component: () => import('@/views/profile/EventViewer.vue')
                }
            ]
        }
    ]
});

export default router;
