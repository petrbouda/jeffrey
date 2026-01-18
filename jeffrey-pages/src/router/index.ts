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
        redirect: '/docs/goals/overview'
      },
      // Goals
      {
        path: 'goals/overview',
        name: 'DocsGoalsOverview',
        component: () => import('@/views/docs/goals/GoalsOverviewPage.vue')
      },
      // Architecture
      {
        path: 'architecture/overview',
        name: 'DocsArchitectureOverview',
        component: () => import('@/views/docs/architecture/ArchitectureOverviewPage.vue')
      },
      {
        path: 'architecture/public-api',
        name: 'DocsPublicApi',
        component: () => import('@/views/docs/architecture/ArchitecturePublicApiPage.vue')
      },
      {
        path: 'architecture/storage',
        name: 'DocsStorage',
        component: () => import('@/views/docs/architecture/ArchitectureStoragePage.vue')
      },
      // Getting Started
      {
        path: 'getting-started/introduction',
        name: 'DocsIntroduction',
        component: () => import('@/views/docs/getting-started/GettingStartedIntroductionPage.vue')
      },
      {
        path: 'getting-started/installation',
        name: 'DocsInstallation',
        component: () => import('@/views/docs/getting-started/GettingStartedInstallationPage.vue')
      },
      {
        path: 'getting-started/quick-start',
        name: 'DocsQuickStart',
        component: () => import('@/views/docs/getting-started/GettingStartedQuickStartPage.vue')
      },
      // Concepts
      {
        path: 'platform/workspaces',
        name: 'DocsWorkspaces',
        component: () => import('@/views/docs/platform/WorkspacesPage.vue')
      },
      {
        path: 'platform/projects',
        name: 'DocsProjects',
        component: () => import('@/views/docs/platform/projects/ProjectsOverviewPage.vue')
      },
      {
        path: 'platform/projects/profiles',
        name: 'DocsProjectsProfiles',
        component: () => import('@/views/docs/platform/projects/ProjectsProfilesPage.vue')
      },
      {
        path: 'platform/projects/recordings',
        name: 'DocsProjectsRecordings',
        component: () => import('@/views/docs/platform/projects/ProjectsRecordingsPage.vue')
      },
      {
        path: 'platform/projects/repository',
        name: 'DocsProjectsRepository',
        component: () => import('@/views/docs/platform/projects/ProjectsRepositoryPage.vue')
      },
      {
        path: 'platform/recording-sessions/overview',
        name: 'DocsRecordingSessionsOverview',
        component: () => import('@/views/docs/platform/recording-sessions/RecordingSessionsOverviewPage.vue')
      },
      {
        path: 'platform/recording-sessions/configuration',
        name: 'DocsRecordingSessionsConfiguration',
        component: () => import('@/views/docs/platform/recording-sessions/RecordingSessionsConfigurationPage.vue')
      },
      {
        path: 'platform/recording-sessions/lifecycle',
        name: 'DocsRecordingSessionsLifecycle',
        component: () => import('@/views/docs/platform/recording-sessions/RecordingSessionsLifecyclePage.vue')
      },
      {
        path: 'platform/projects/profiler-settings',
        name: 'DocsProjectsProfilerSettings',
        component: () => import('@/views/docs/platform/projects/ProjectsProfilerSettingsPage.vue')
      },
      {
        path: 'platform/projects/scheduler',
        name: 'DocsProjectsScheduler',
        component: () => import('@/views/docs/platform/projects/ProjectsSchedulerPage.vue')
      },
      {
        path: 'platform/projects/alerts-messages',
        name: 'DocsProjectsAlertsMessages',
        component: () => import('@/views/docs/platform/projects/ProjectsAlertsMessagesPage.vue')
      },
      // Profiles
      {
        path: 'profiles/overview',
        name: 'DocsProfiles',
        component: () => import('@/views/docs/profiles/ProfilesOverviewPage.vue')
      },
      {
        path: 'profiles/jvm-internals',
        name: 'DocsProfilesJvmInternals',
        component: () => import('@/views/docs/profiles/ProfilesJvmInternalsSectionPage.vue')
      },
      {
        path: 'profiles/application',
        name: 'DocsProfilesApplication',
        component: () => import('@/views/docs/profiles/ProfilesApplicationSectionPage.vue')
      },
      {
        path: 'profiles/visualization',
        name: 'DocsProfilesVisualization',
        component: () => import('@/views/docs/profiles/ProfilesVisualizationSectionPage.vue')
      },
      {
        path: 'profiles/heap-dump-analysis',
        name: 'DocsProfilesHeapDumpAnalysis',
        component: () => import('@/views/docs/profiles/ProfilesHeapDumpAnalysisSectionPage.vue')
      },
      // Jeffrey JFR Events
      {
        path: 'jeffrey-jfr-events/overview',
        name: 'DocsJeffreyJfrEvents',
        component: () => import('@/views/docs/jeffrey-jfr-events/JeffreyJfrEventsPage.vue')
      },
      // Features
      {
        path: 'features/overview',
        name: 'DocsFeatures',
        component: () => import('@/views/docs/features/FeaturesPage.vue')
      },
      // CLI
      {
        path: 'cli/overview',
        name: 'DocsCliOverview',
        component: () => import('@/views/docs/cli/CliOverviewPage.vue')
      },
      {
        path: 'cli/configuration',
        name: 'DocsCliConfiguration',
        component: () => import('@/views/docs/cli/CliConfigurationPage.vue')
      },
      {
        path: 'cli/directory-structure',
        name: 'DocsCliDirectoryStructure',
        component: () => import('@/views/docs/cli/CliDirectoryStructurePage.vue')
      },
      // Configuration
      {
        path: 'configuration/overview',
        name: 'DocsConfigurationOverview',
        component: () => import('@/views/docs/configuration/ConfigurationOverviewPage.vue')
      },
      {
        path: 'configuration/application-properties',
        name: 'DocsConfigurationApplicationProperties',
        component: () => import('@/views/docs/configuration/ConfigurationApplicationPropertiesPage.vue')
      },
      {
        path: 'configuration/advanced-properties',
        name: 'DocsConfigurationAdvancedProperties',
        component: () => import('@/views/docs/configuration/ConfigurationAdvancedPropertiesPage.vue')
      },
      {
        path: 'configuration/secrets',
        name: 'DocsConfigurationSecrets',
        component: () => import('@/views/docs/configuration/ConfigurationSecretsPage.vue')
      },
      // Deployments
      {
        path: 'deployments/overview',
        name: 'DocsDeploymentsOverview',
        component: () => import('@/views/docs/deployments/DeploymentsOverviewPage.vue')
      },
      {
        path: 'deployments/simple-jar',
        name: 'DocsDeploymentsSimpleJar',
        component: () => import('@/views/docs/deployments/DeploymentsSimpleJarPage.vue')
      },
      {
        path: 'deployments/simple-container',
        name: 'DocsDeploymentsSimpleContainer',
        component: () => import('@/views/docs/deployments/DeploymentsSimpleContainerPage.vue')
      },
      {
        path: 'deployments/container-examples',
        name: 'DocsDeploymentsContainerExamples',
        component: () => import('@/views/docs/deployments/DeploymentsContainerExamplesPage.vue')
      },
      {
        path: 'deployments/live-recording',
        name: 'DocsDeploymentsLiveRecording',
        component: () => import('@/views/docs/deployments/DeploymentsLiveRecordingPage.vue')
      },
      // Live Recording
      {
        path: 'live-recording/overview',
        name: 'DocsLiveRecordingOverview',
        component: () => import('@/views/docs/live-recording/LiveRecordingOverviewPage.vue')
      },
      {
        path: 'live-recording/jeffrey-deployment',
        name: 'DocsLiveRecordingJeffreyDeployment',
        component: () => import('@/views/docs/live-recording/LiveRecordingJeffreyDeploymentPage.vue')
      },
      {
        path: 'live-recording/service-deployment',
        name: 'DocsLiveRecordingServiceDeployment',
        component: () => import('@/views/docs/live-recording/LiveRecordingServiceDeploymentPage.vue')
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
