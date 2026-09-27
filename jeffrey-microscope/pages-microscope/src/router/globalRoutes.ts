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

import type { RouteRecordRaw } from 'vue-router';

// The top-level pages of the application shell, rendered inside Index.vue under '/'. Kept apart from
// the router so the manifest test can read them without creating a router, the way
// profileChildRoutes.ts is.
export const globalChildRoutes = [
  {
    path: 'recordings',
    name: 'recordings',
    component: () => import('@/views/global/RecordingsView.vue')
  },
  {
    path: 'hubs',
    name: 'hubs',
    component: () => import('@/views/hubs/HubsView.vue')
  },
  {
    path: 'profiler-builder',
    name: 'profiler-builder',
    component: () => import('@/views/global/ProfilerBuilderView.vue')
  }
] satisfies RouteRecordRaw[];
