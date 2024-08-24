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

const router = createRouter({
    history: createWebHashHistory(),
    routes: [
        {
            path: '/',
            component: AppLayout,
            children: [
                {
                    path: '/',
                    name: 'index',
                    component: () => import('@/views/Index.vue'),
                    children: [
                        {
                            path: '/index/profiles',
                            name: 'profiles',
                            component: () => import('@/views/Profiles.vue'),
                        },
                        {
                            path: '/index/recordings',
                            name: 'recordings',
                            component: () => import('@/views/Recordings.vue'),
                        },
                    ]
                },
                {
                    path: '/common/flamegraph',
                    name: 'flamegraph',
                    component: () => import('@/views/flamegraph/Flamegraph.vue'),
                },
                {
                    path: '/common/flamegraph-sections',
                    name: 'flamegraph-sections',
                    component: () => import('@/views/flamegraph/FlamegraphSections.vue'),
                },
                {
                    path: '/common/diff-flamegraph-sections',
                    name: 'diff-flamegraph-sections',
                    component: () => import('@/views/flamegraph/DiffFlamegraphSections.vue'),
                },
                {
                    path: '/common/subsecond-sections',
                    name: 'subsecond-sections',
                    component: () => import('@/views/subsecond/SubSecondSections.vue')
                },
                {
                    path: '/common/diff-subsecond-sections',
                    name: 'diff-subsecond-sections',
                    component: () => import('@/views/subsecond/DiffSubSecondSections.vue')
                },
                {
                    path: '/common/subsecond',
                    name: 'subsecond',
                    component: () => import('@/views/subsecond/SubSecond.vue')
                },
                {
                    path: '/common/savedgraphs',
                    name: 'saved-graphs',
                    component: () => import('@/views/common/SavedGraphs.vue')
                },
                {
                    path: '/common/showSimple',
                    name: 'flamegraph-simple',
                    component: () => import('@/views/flamegraph/ShowSimple.vue')
                },
                {
                    path: '/profile/information',
                    name: 'profile-information',
                    component: () => import('@/views/profile/Information.vue')
                },
                {
                    path: '/profile/guardian',
                    name: 'profile-guardian',
                    component: () => import('@/views/profile/Guardian.vue')
                },
                {
                    path: '/profile/autoAnalysis',
                    name: 'profile-auto-analysis',
                    component: () => import('@/views/profile/AutoAnalysis.vue')
                },
                {
                    path: '/profile/eventViewer',
                    name: 'event-viewer',
                    component: () => import('@/views/profile/EventViewer.vue')
                }
            ]
        }
    ]
});

export default router;
