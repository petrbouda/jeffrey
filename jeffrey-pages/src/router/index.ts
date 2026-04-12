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
    path: '/release-notes',
    name: 'ReleaseNotes',
    component: () => import('@/views/ReleaseNotesView.vue')
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

      // ──── Getting Started ────
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

      // ──── Architecture ────
      {
        path: 'architecture/overview',
        name: 'DocsArchitectureOverview',
        component: () => import('@/views/docs/architecture/ArchitectureOverviewPage.vue')
      },
      {
        path: 'architecture/storage',
        name: 'DocsStorage',
        component: () => import('@/views/docs/architecture/ArchitectureStoragePage.vue')
      },

      // ──── Jeffrey Local ────
      {
        path: 'local/overview',
        name: 'DocsLocalOverview',
        component: () => import('@/views/docs/local/LocalOverviewPage.vue')
      },
      {
        path: 'local/quick-analysis',
        name: 'DocsQuickAnalysis',
        component: () => import('@/views/docs/local/QuickAnalysisPage.vue')
      },
      // Profiles
      {
        path: 'local/profiles',
        name: 'DocsProfiles',
        component: () => import('@/views/docs/local/profiles/ProfilesPage.vue')
      },
      // Workspaces & Event Log
      {
        path: 'local/workspaces',
        name: 'DocsWorkspaces',
        component: () => import('@/views/docs/local/WorkspacesPage.vue')
      },
      {
        path: 'local/event-log',
        name: 'DocsEventLog',
        component: () => import('@/views/docs/local/EventLogPage.vue')
      },
      {
        path: 'local/profiler-settings',
        name: 'DocsLocalProfilerSettings',
        component: () => import('@/views/docs/local/LocalProfilerSettingsPage.vue')
      },
      {
        path: 'local/settings',
        name: 'DocsLocalSettings',
        component: () => import('@/views/docs/local/LocalSettingsPage.vue')
      },
      // Projects
      {
        path: 'local/projects',
        name: 'DocsProjects',
        component: () => import('@/views/docs/local/projects/ProjectsOverviewPage.vue')
      },
      {
        path: 'local/projects/profiles',
        name: 'DocsProjectsProfiles',
        component: () => import('@/views/docs/local/projects/ProjectsProfilesPage.vue')
      },
      {
        path: 'local/projects/recordings',
        name: 'DocsProjectsRecordings',
        component: () => import('@/views/docs/local/projects/ProjectsRecordingsPage.vue')
      },
      {
        path: 'local/projects/repository',
        name: 'DocsProjectsRepository',
        component: () => import('@/views/docs/local/projects/ProjectsRepositoryPage.vue')
      },
      {
        path: 'local/projects/instances',
        name: 'DocsProjectsInstances',
        component: () => import('@/views/docs/local/projects/ProjectsInstancesPage.vue')
      },
      {
        path: 'local/projects/profiler-settings',
        name: 'DocsProjectsProfilerSettings',
        component: () => import('@/views/docs/local/projects/ProjectsProfilerSettingsPage.vue')
      },
      {
        path: 'local/projects/scheduler',
        name: 'DocsProjectsScheduler',
        component: () => import('@/views/docs/local/projects/ProjectsSchedulerPage.vue')
      },
      {
        path: 'local/projects/event-streaming',
        name: 'DocsProjectsEventStreaming',
        component: () => import('@/views/docs/local/projects/ProjectsEventStreamingPage.vue')
      },
      // Local Deployment
      {
        path: 'local/deployment/jar-execution',
        name: 'DocsLocalDeploymentJar',
        component: () => import('@/views/docs/local/deployment/DeploymentsSimpleJarPage.vue')
      },
      {
        path: 'local/deployment/docker-container',
        name: 'DocsLocalDeploymentContainer',
        component: () => import('@/views/docs/local/deployment/DeploymentsSimpleContainerPage.vue')
      },
      {
        path: 'local/deployment/container-examples',
        name: 'DocsLocalDeploymentExamples',
        component: () => import('@/views/docs/local/deployment/DeploymentsContainerExamplesPage.vue')
      },
      // Local Configuration
      {
        path: 'local/configuration/application-properties',
        name: 'DocsLocalConfigAppProps',
        component: () => import('@/views/docs/local/configuration/LocalConfigApplicationPropertiesPage.vue')
      },
      {
        path: 'local/configuration/advanced-properties',
        name: 'DocsLocalConfigAdvancedProps',
        component: () => import('@/views/docs/local/configuration/LocalConfigAdvancedPropertiesPage.vue')
      },
      {
        path: 'local/configuration/secrets',
        name: 'DocsLocalConfigSecrets',
        component: () => import('@/views/docs/local/configuration/ConfigurationSecretsPage.vue')
      },

      // ──── Jeffrey Server ────
      {
        path: 'server/overview',
        name: 'DocsServerOverview',
        component: () => import('@/views/docs/server/ServerOverviewPage.vue')
      },
      // Continuous Recording
      {
        path: 'server/continuous-recording/overview',
        name: 'DocsContinuousRecordingOverview',
        component: () => import('@/views/docs/server/continuous-recording/LiveRecordingOverviewPage.vue')
      },
      {
        path: 'server/continuous-recording/jeffrey-deployment',
        name: 'DocsContinuousRecordingJeffreyDeployment',
        component: () => import('@/views/docs/server/continuous-recording/LiveRecordingJeffreyDeploymentPage.vue')
      },
      {
        path: 'server/continuous-recording/service-deployment',
        name: 'DocsContinuousRecordingServiceDeployment',
        component: () => import('@/views/docs/server/continuous-recording/LiveRecordingServiceDeploymentPage.vue')
      },
      // Recording Sessions
      {
        path: 'server/recording-sessions/overview',
        name: 'DocsRecordingSessionsOverview',
        component: () => import('@/views/docs/server/recording-sessions/RecordingSessionsOverviewPage.vue')
      },
      {
        path: 'server/recording-sessions/configuration',
        name: 'DocsRecordingSessionsConfiguration',
        component: () => import('@/views/docs/server/recording-sessions/RecordingSessionsConfigurationPage.vue')
      },
      {
        path: 'server/recording-sessions/lifecycle',
        name: 'DocsRecordingSessionsLifecycle',
        component: () => import('@/views/docs/server/recording-sessions/RecordingSessionsLifecyclePage.vue')
      },
      // Server gRPC API
      {
        path: 'server/grpc-api',
        name: 'DocsServerGrpcApi',
        component: () => import('@/views/docs/server/ArchitecturePublicApiPage.vue')
      },
      // Server Deployment
      {
        path: 'server/deployment',
        name: 'DocsServerDeployment',
        component: () => import('@/views/docs/server/deployment/ServerDeploymentPage.vue')
      },
      // Server Configuration
      {
        path: 'server/configuration/application-properties',
        name: 'DocsServerConfigAppProps',
        component: () => import('@/views/docs/server/configuration/ServerConfigApplicationPropertiesPage.vue')
      },
      {
        path: 'server/configuration/advanced-properties',
        name: 'DocsServerConfigAdvancedProps',
        component: () => import('@/views/docs/server/configuration/ServerConfigAdvancedPropertiesPage.vue')
      },

      // ──── Jeffrey CLI ────
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
      {
        path: 'cli/generated-output',
        name: 'DocsCliGeneratedOutput',
        component: () => import('@/views/docs/cli/CliGeneratedOutputPage.vue')
      },

      // ──── Jeffrey Agent ────
      {
        path: 'agent/overview',
        name: 'DocsAgentOverview',
        component: () => import('@/views/docs/agent/AgentOverviewPage.vue')
      },

      // ──── AI Analysis ────
      {
        path: 'ai/overview',
        name: 'DocsAiOverview',
        component: () => import('@/views/docs/ai/AiOverviewPage.vue')
      },
      {
        path: 'ai/jfr-analysis',
        name: 'DocsAiJfrAnalysis',
        component: () => import('@/views/docs/ai/AiJfrAnalysisPage.vue')
      },
      {
        path: 'ai/heap-dump-analysis',
        name: 'DocsAiHeapDumpAnalysis',
        component: () => import('@/views/docs/ai/AiHeapDumpAnalysisPage.vue')
      },
      {
        path: 'ai/oql-assistant',
        name: 'DocsAiOqlAssistant',
        component: () => import('@/views/docs/ai/AiOqlAssistantPage.vue')
      },

      // ──── Jeffrey Events ────
      {
        path: 'events/overview',
        name: 'DocsJeffreyEvents',
        component: () => import('@/views/docs/events/JeffreyJfrEventsPage.vue')
      },

      // ──── Redirects from old paths ────
      { path: 'goals/overview', redirect: '/docs/getting-started/introduction' },
      { path: 'platform/workspaces', redirect: '/docs/local/workspaces' },
      { path: 'platform/quick-analysis', redirect: '/docs/local/quick-analysis' },
      { path: 'platform/event-log', redirect: '/docs/local/event-log' },
      { path: 'platform/projects', redirect: '/docs/local/projects' },
      { path: 'platform/projects/profiles', redirect: '/docs/local/projects/profiles' },
      { path: 'platform/projects/recordings', redirect: '/docs/local/projects/recordings' },
      { path: 'platform/projects/repository', redirect: '/docs/local/projects/repository' },
      { path: 'platform/projects/instances', redirect: '/docs/local/projects/instances' },
      { path: 'platform/projects/profiler-settings', redirect: '/docs/local/projects/profiler-settings' },
      { path: 'platform/projects/scheduler', redirect: '/docs/local/projects/scheduler' },
      { path: 'platform/projects/alerts-messages', redirect: '/docs/local/projects/event-streaming' },
      { path: 'platform/recording-sessions/overview', redirect: '/docs/server/recording-sessions/overview' },
      { path: 'platform/recording-sessions/configuration', redirect: '/docs/server/recording-sessions/configuration' },
      { path: 'platform/recording-sessions/lifecycle', redirect: '/docs/server/recording-sessions/lifecycle' },
      { path: 'profiles/overview', redirect: '/docs/local/profiles' },
      { path: 'profiles/jvm-internals', redirect: '/docs/local/profiles' },
      { path: 'profiles/application', redirect: '/docs/local/profiles' },
      { path: 'profiles/visualization', redirect: '/docs/local/profiles' },
      { path: 'profiles/heap-dump-analysis', redirect: '/docs/local/profiles' },
      { path: 'profiles/tools', redirect: '/docs/local/profiles' },
      { path: 'local/profiles/overview', redirect: '/docs/local/profiles' },
      { path: 'local/profiles/jvm-internals', redirect: '/docs/local/profiles' },
      { path: 'local/profiles/application', redirect: '/docs/local/profiles' },
      { path: 'local/profiles/visualization', redirect: '/docs/local/profiles' },
      { path: 'local/profiles/heap-dump-analysis', redirect: '/docs/local/profiles' },
      { path: 'local/profiles/tools', redirect: '/docs/local/profiles' },
      { path: 'deployments/overview', redirect: '/docs/local/deployment/jar-execution' },
      { path: 'deployments/simple-jar', redirect: '/docs/local/deployment/jar-execution' },
      { path: 'deployments/simple-container', redirect: '/docs/local/deployment/docker-container' },
      { path: 'deployments/container-examples', redirect: '/docs/local/deployment/container-examples' },
      { path: 'deployments/live-recording', redirect: '/docs/server/continuous-recording/overview' },
      { path: 'live-recording/overview', redirect: '/docs/server/continuous-recording/overview' },
      { path: 'live-recording/jeffrey-deployment', redirect: '/docs/server/continuous-recording/jeffrey-deployment' },
      { path: 'live-recording/service-deployment', redirect: '/docs/server/continuous-recording/service-deployment' },
      { path: 'configuration/overview', redirect: '/docs/local/configuration/application-properties' },
      { path: 'configuration/application-properties', redirect: '/docs/local/configuration/application-properties' },
      { path: 'configuration/advanced-properties', redirect: '/docs/local/configuration/advanced-properties' },
      { path: 'configuration/secrets', redirect: '/docs/local/configuration/secrets' },
      { path: 'architecture/public-api', redirect: '/docs/server/grpc-api' },
      { path: 'jeffrey-jfr-events/overview', redirect: '/docs/events/overview' },
      { path: 'features/overview', redirect: '/docs/getting-started/introduction' }
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
