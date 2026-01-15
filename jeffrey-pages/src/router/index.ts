/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue')
  },
  {
    path: '/features',
    name: 'HomeFeatures',
    component: () => import('@/views/HomeView.vue')
  },
  {
    path: '/launch-it',
    name: 'LaunchIt',
    component: () => import('@/views/LaunchItView.vue')
  },
  {
    path: '/tour-with-examples',
    name: 'TourWithExamples',
    component: () => import('@/views/TourWithExamplesView.vue')
  },
  {
    path: '/new-features',
    name: 'NewFeatures',
    component: () => import('@/views/NewFeaturesView.vue')
  },
  {
    path: '/blog',
    name: 'Blog',
    component: () => import('@/views/BlogView.vue')
  },
  {
    path: '/blog/java-profiling-literature',
    name: 'JavaProfilingLiterature',
    component: () => import('@/views/blog/JavaProfilingLiteratureView.vue')
  },
  {
    path: '/blog/jfr-in-depth',
    name: 'JfrInDepth',
    component: () => import('@/views/blog/JfrInDepthView.vue')
  },
  {
    path: '/blog/jeffrey-04-announcement',
    name: 'Jeffrey04Announcement',
    component: () => import('@/views/blog/Jeffrey04AnnouncementView.vue')
  },
  {
    path: '/docs',
    component: () => import('@/views/docs/DocsLayout.vue'),
    children: [
      {
        path: '',
        redirect: '/docs/getting-started/introduction'
      },
      // Getting Started
      {
        path: 'getting-started/introduction',
        name: 'DocsIntroduction',
        component: () => import('@/views/docs/getting-started/IntroductionPage.vue')
      },
      {
        path: 'getting-started/installation',
        name: 'DocsInstallation',
        component: () => import('@/views/docs/getting-started/InstallationPage.vue')
      },
      {
        path: 'getting-started/quick-start',
        name: 'DocsQuickStart',
        component: () => import('@/views/docs/getting-started/QuickStartPage.vue')
      },
      // Concepts
      {
        path: 'concepts/workspaces',
        name: 'DocsWorkspaces',
        component: () => import('@/views/docs/concepts/WorkspacesPage.vue')
      },
      {
        path: 'concepts/projects',
        name: 'DocsProjects',
        component: () => import('@/views/docs/concepts/ProjectsPage.vue')
      },
      {
        path: 'concepts/projects/profiles',
        name: 'DocsProjectsProfiles',
        component: () => import('@/views/docs/concepts/projects/ProfilesFeaturePage.vue')
      },
      {
        path: 'concepts/projects/recordings',
        name: 'DocsProjectsRecordings',
        component: () => import('@/views/docs/concepts/projects/RecordingsFeaturePage.vue')
      },
      {
        path: 'concepts/projects/repository',
        name: 'DocsProjectsRepository',
        component: () => import('@/views/docs/concepts/projects/RepositoryFeaturePage.vue')
      },
      {
        path: 'concepts/projects/profiler-settings',
        name: 'DocsProjectsProfilerSettings',
        component: () => import('@/views/docs/concepts/projects/ProfilerSettingsPage.vue')
      },
      {
        path: 'concepts/projects/scheduler',
        name: 'DocsProjectsScheduler',
        component: () => import('@/views/docs/concepts/projects/SchedulerFeaturePage.vue')
      },
      {
        path: 'concepts/projects/alerts-messages',
        name: 'DocsProjectsAlertsMessages',
        component: () => import('@/views/docs/concepts/projects/AlertsMessagesPage.vue')
      },
      {
        path: 'concepts/profiles',
        name: 'DocsProfiles',
        component: () => import('@/views/docs/concepts/ProfilesPage.vue')
      },
      {
        path: 'concepts/jfr-events',
        name: 'DocsJfrEvents',
        component: () => import('@/views/docs/concepts/JfrEventsPage.vue')
      },
      // Features
      {
        path: 'features/flamegraphs',
        name: 'DocsFlamegraphs',
        component: () => import('@/views/docs/features/FlamegraphsPage.vue')
      },
      {
        path: 'features/timeseries',
        name: 'DocsTimeseries',
        component: () => import('@/views/docs/features/TimeseriesPage.vue')
      },
      {
        path: 'features/guardian',
        name: 'DocsGuardian',
        component: () => import('@/views/docs/features/GuardianPage.vue')
      },
      {
        path: 'features/auto-analysis',
        name: 'DocsAutoAnalysis',
        component: () => import('@/views/docs/features/AutoAnalysisPage.vue')
      },
      // Guides
      {
        path: 'guides/cpu-performance',
        name: 'DocsCpuPerformance',
        component: () => import('@/views/docs/guides/CpuPerformancePage.vue')
      },
      {
        path: 'guides/memory-analysis',
        name: 'DocsMemoryAnalysis',
        component: () => import('@/views/docs/guides/MemoryAnalysisPage.vue')
      },
      {
        path: 'guides/thread-analysis',
        name: 'DocsThreadAnalysis',
        component: () => import('@/views/docs/guides/ThreadAnalysisPage.vue')
      },
      // CLI
      {
        path: 'cli/overview',
        name: 'DocsCliOverview',
        component: () => import('@/views/docs/cli/CliOverviewPage.vue')
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }
    if (to.hash) {
      return {
        el: to.hash,
        behavior: 'smooth',
        top: 80
      };
    }
    return { top: 0 };
  }
});

export default router;
