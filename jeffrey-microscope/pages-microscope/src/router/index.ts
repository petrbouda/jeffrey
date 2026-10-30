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
import AppLayout from '@/layout/AppLayout.vue';
import { profileChildRoutes } from '@/router/profileChildRoutes';
import { globalChildRoutes } from '@/router/globalRoutes';

export { profileChildRoutes };

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }
    if (to.hash) {
      return {
        el: to.hash,
        behavior: 'smooth'
      };
    }
    return { top: 0 };
  },
  routes: [
    {
      path: '/',
      redirect: '/recordings'
    },
    // Standalone deep link: import a JFR by path, wait, then jump into the profile.
    // Opened by external tools (e.g. the IntelliJ plugin): /quick-open?path=/abs/file.jfr
    {
      path: '/quick-open',
      name: 'quick-open',
      component: () => import('@/views/global/QuickOpenView.vue')
    },
    {
      path: '/',
      component: () => import('@/views/Index.vue'),
      children: globalChildRoutes
    },
    // Simplified profile routes - /profiles/:profileId/...
    {
      path: '/profiles/:profileId',
      component: AppLayout,
      children: [
        {
          path: '',
          component: () => import('@/views/profiles/ProfileDetail.vue'),
          meta: { layout: 'profile' },
          children: profileChildRoutes
        }
      ]
    },
    // The remote is browsed on one page, /remote, its picks held in the query. The paths of the
    // former project pages still open: they redirect to the browser at the same project or instance
    {
      path: '/remote/workspaces/:workspaceId/projects/:projectId/instances/:instanceId',
      redirect: to => ({
        path: '/remote',
        query: {
          workspaceId: to.params.workspaceId,
          projectId: to.params.projectId,
          instanceId: to.params.instanceId
        }
      })
    },
    {
      path: '/remote/workspaces/:workspaceId/projects/:projectId/:rest(.*)*',
      redirect: to => ({
        path: '/remote',
        query: { workspaceId: to.params.workspaceId, projectId: to.params.projectId }
      })
    },
    {
      path: '/remote/workspaces/:workspaceId/:rest(.*)*',
      redirect: to => ({ path: '/remote', query: { workspaceId: to.params.workspaceId } })
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
});

export default router;
