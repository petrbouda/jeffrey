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
    path: '/blog/getting-started-with-jeffrey',
    name: 'GettingStartedWithJeffrey',
    component: () => import('@/views/blog/GettingStartedWithJeffreyView.vue')
  },
  {
    path: '/docs',
    component: () => import('@/views/docs/DocsLayout.vue'),
    children: [
      {
        path: '',
        name: 'DocsIndex',
        component: () => import('@/views/docs/DocsIndexPage.vue')
      },
      {
        path: 'microscope',
        name: 'DocsMicroscope',
        component: () => import('@/views/docs/microscope/MicroscopeOverviewPage.vue')
      },
      {
        path: 'microscope/architecture',
        name: 'DocsMicroscopeArchitecture',
        component: () => import('@/views/docs/DocsMicroscopePage.vue')
      },
      // Legacy URL — install + quick-start were merged into a single Quick Start page.
      { path: 'microscope/installation', redirect: '/docs/microscope/quick-start' },
      {
        path: 'microscope/quick-start',
        name: 'DocsMicroscopeQuickStart',
        component: () => import('@/views/docs/microscope/MicroscopeQuickStartPage.vue')
      },
      {
        path: 'server',
        name: 'DocsServer',
        component: () => import('@/views/docs/server/ServerOverviewPage.vue')
      },
      // Legacy / consolidated entry-points: installation + quick-start replaced by
      // the Deployment section; /docs/server/overview was superseded by /docs/server.
      { path: 'server/installation', redirect: '/docs/server/deployment' },
      { path: 'server/overview', redirect: '/docs/server' },
      { path: 'server/quick-start', redirect: '/docs/server/deployment' },
      // Deployment — worked example based on the jeffrey-testapp repo.
      {
        path: 'server/deployment',
        name: 'DocsServerDeploymentOverview',
        component: () => import('@/views/docs/server/deployment/DeploymentOverviewPage.vue')
      },
      {
        path: 'server/deployment/jeffrey-jib',
        name: 'DocsServerDeploymentJeffreyJib',
        component: () => import('@/views/docs/server/deployment/DeploymentJeffreyJibPage.vue')
      },
      {
        path: 'server/deployment/shared-volume',
        name: 'DocsServerDeploymentSharedVolume',
        component: () => import('@/views/docs/server/deployment/DeploymentSharedVolumePage.vue')
      },
      {
        path: 'server/deployment/jeffrey-cli',
        name: 'DocsServerDeploymentJeffreyCli',
        component: () => import('@/views/docs/server/deployment/DeploymentJeffreyCliPage.vue')
      },
      {
        path: 'server/deployment/helm-chart',
        name: 'DocsServerDeploymentHelmChart',
        component: () => import('@/views/docs/server/deployment/DeploymentHelmChartPage.vue')
      },
      {
        path: 'server/architecture',
        name: 'DocsServerArchitecture',
        component: () => import('@/views/docs/server/ServerArchitectureOverviewPage.vue')
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
      // /docs/architecture/storage was retired in favor of per-product storage pages.
      // Default the redirect to the Microscope variant; users can navigate to the
      // Server one from its sidebar.
      { path: 'architecture/storage', redirect: '/docs/microscope/storage' },

      // ──── Jeffrey Microscope ────
      // Legacy URL — kept as a redirect so existing inbound links (HomeView CTA,
      // GettingStarted intro) keep working. The product overview now lives at
      // /docs/microscope.
      { path: 'microscope/overview', redirect: '/docs/microscope' },
      {
        path: 'microscope/storage',
        name: 'DocsMicroscopeStorage',
        component: () => import('@/views/docs/microscope/MicroscopeStoragePage.vue')
      },
      {
        path: 'microscope/recordings',
        name: 'DocsRecordings',
        component: () => import('@/views/docs/microscope/RecordingsPage.vue')
      },
      // Profiles
      {
        path: 'microscope/profiles',
        name: 'DocsProfiles',
        component: () => import('@/views/docs/microscope/profiles/ProfilesPage.vue')
      },
      {
        path: 'microscope/profiles/guardian',
        name: 'DocsProfilesGuardian',
        component: () => import('@/views/docs/microscope/profiles/ProfileGuardianPage.vue')
      },
      {
        path: 'microscope/profiles/garbage-collection',
        name: 'DocsProfilesGarbageCollection',
        component: () => import('@/views/docs/microscope/profiles/ProfileGarbageCollectionPage.vue')
      },
      // Workspaces & Event Log
      {
        path: 'microscope/workspaces',
        name: 'DocsWorkspaces',
        component: () => import('@/views/docs/microscope/WorkspacesPage.vue')
      },
      {
        path: 'microscope/event-log',
        name: 'DocsEventLog',
        component: () => import('@/views/docs/microscope/EventLogPage.vue')
      },
      {
        path: 'microscope/profiler-settings',
        name: 'DocsMicroscopeProfilerSettings',
        component: () => import('@/views/docs/microscope/MicroscopeProfilerSettingsPage.vue')
      },
      // Projects
      {
        path: 'microscope/projects',
        name: 'DocsProjects',
        component: () => import('@/views/docs/microscope/projects/ProjectsOverviewPage.vue')
      },
      {
        path: 'microscope/projects/instances',
        name: 'DocsProjectsInstances',
        component: () => import('@/views/docs/microscope/projects/ProjectsInstancesPage.vue')
      },
      {
        path: 'microscope/projects/profiler-settings',
        name: 'DocsProjectsProfilerSettings',
        component: () => import('@/views/docs/microscope/projects/ProjectsProfilerSettingsPage.vue')
      },
      {
        path: 'microscope/projects/event-streaming',
        name: 'DocsProjectsEventStreaming',
        component: () => import('@/views/docs/microscope/projects/ProjectsEventStreamingPage.vue')
      },
      // Legacy paths — features moved to global pages or were removed in the UI.
      { path: 'microscope/projects/profiles', redirect: '/docs/microscope/profiles' },
      { path: 'microscope/projects/recordings', redirect: '/docs/microscope/recordings' },
      { path: 'microscope/projects/repository', redirect: '/docs/microscope/projects/instances' },
      { path: 'microscope/projects/scheduler', redirect: '/docs/microscope/projects' },
      // Microscope Configuration
      {
        path: 'microscope/configuration/application-properties',
        name: 'DocsMicroscopeConfigAppProps',
        component: () => import('@/views/docs/microscope/configuration/MicroscopeConfigApplicationPropertiesPage.vue')
      },
      {
        path: 'microscope/configuration/advanced-properties',
        name: 'DocsMicroscopeConfigAdvancedProps',
        component: () => import('@/views/docs/microscope/configuration/MicroscopeConfigAdvancedPropertiesPage.vue')
      },
      {
        path: 'microscope/configuration/secrets',
        name: 'DocsMicroscopeConfigSecrets',
        component: () => import('@/views/docs/microscope/configuration/ConfigurationSecretsPage.vue')
      },

      // ──── Jeffrey Server ────
      {
        path: 'server/storage',
        name: 'DocsServerStorage',
        component: () => import('@/views/docs/server/ServerStoragePage.vue')
      },
      // Legacy: Continuous Recording was superseded by the Deployment section.
      { path: 'server/continuous-recording/overview', redirect: '/docs/server/deployment' },
      { path: 'server/continuous-recording/jeffrey-deployment', redirect: '/docs/server/deployment/helm-chart' },
      { path: 'server/continuous-recording/service-deployment', redirect: '/docs/server/deployment/helm-chart' },
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
        component: () => import('@/views/docs/server/ServerGrpcApiPage.vue')
      },
      // Server Configuration — single merged page
      {
        path: 'server/configuration',
        name: 'DocsServerConfiguration',
        component: () => import('@/views/docs/server/configuration/ServerConfigurationPage.vue')
      },
      // Legacy: split application/advanced pages were merged into a single Configuration page.
      { path: 'server/configuration/application-properties', redirect: '/docs/server/configuration' },
      { path: 'server/configuration/advanced-properties', redirect: '/docs/server/configuration' },

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

      // ──── Jeffrey JIB ────
      {
        path: 'jib/overview',
        name: 'DocsJeffreyJib',
        component: () => import('@/views/docs/jib/JeffreyJibPage.vue')
      },

      // ──── Redirects from old paths ────
      { path: 'goals/overview', redirect: '/docs/getting-started/introduction' },
      { path: 'platform/workspaces', redirect: '/docs/microscope/workspaces' },
      { path: 'platform/recordings', redirect: '/docs/microscope/recordings' },
      { path: 'platform/event-log', redirect: '/docs/microscope/event-log' },
      { path: 'platform/projects', redirect: '/docs/microscope/projects' },
      { path: 'platform/projects/profiles', redirect: '/docs/microscope/profiles' },
      { path: 'platform/projects/recordings', redirect: '/docs/microscope/recordings' },
      { path: 'platform/projects/repository', redirect: '/docs/microscope/projects/instances' },
      { path: 'platform/projects/instances', redirect: '/docs/microscope/projects/instances' },
      { path: 'platform/projects/profiler-settings', redirect: '/docs/microscope/projects/profiler-settings' },
      { path: 'platform/projects/scheduler', redirect: '/docs/microscope/projects' },
      { path: 'platform/projects/alerts-messages', redirect: '/docs/microscope/projects/event-streaming' },
      { path: 'platform/recording-sessions/overview', redirect: '/docs/server/recording-sessions/overview' },
      { path: 'platform/recording-sessions/configuration', redirect: '/docs/server/recording-sessions/configuration' },
      { path: 'platform/recording-sessions/lifecycle', redirect: '/docs/server/recording-sessions/lifecycle' },
      { path: 'profiles/overview', redirect: '/docs/microscope/profiles' },
      { path: 'profiles/jvm-internals', redirect: '/docs/microscope/profiles' },
      { path: 'profiles/application', redirect: '/docs/microscope/profiles' },
      { path: 'profiles/visualization', redirect: '/docs/microscope/profiles' },
      { path: 'profiles/heap-dump-analysis', redirect: '/docs/microscope/profiles' },
      { path: 'profiles/tools', redirect: '/docs/microscope/profiles' },
      { path: 'local/profiles/overview', redirect: '/docs/microscope/profiles' },
      { path: 'local/profiles/jvm-internals', redirect: '/docs/microscope/profiles' },
      { path: 'local/profiles/application', redirect: '/docs/microscope/profiles' },
      { path: 'local/profiles/visualization', redirect: '/docs/microscope/profiles' },
      { path: 'local/profiles/heap-dump-analysis', redirect: '/docs/microscope/profiles' },
      { path: 'local/profiles/tools', redirect: '/docs/microscope/profiles' },
      { path: 'deployments/live-recording', redirect: '/docs/server/deployment' },
      { path: 'live-recording/overview', redirect: '/docs/server/deployment' },
      { path: 'live-recording/jeffrey-deployment', redirect: '/docs/server/deployment/helm-chart' },
      { path: 'live-recording/service-deployment', redirect: '/docs/server/deployment/helm-chart' },
      { path: 'configuration/overview', redirect: '/docs/microscope/configuration/application-properties' },
      { path: 'configuration/application-properties', redirect: '/docs/microscope/configuration/application-properties' },
      { path: 'configuration/advanced-properties', redirect: '/docs/microscope/configuration/advanced-properties' },
      { path: 'configuration/secrets', redirect: '/docs/microscope/configuration/secrets' },
      { path: 'architecture/public-api', redirect: '/docs/server/grpc-api' },
      { path: 'jeffrey-jfr-events/overview', redirect: '/docs/events/overview' },
      { path: 'features/overview', redirect: '/docs/getting-started/introduction' },

      // Legacy /docs/local/* — keep working by mapping any remaining sub-path to /docs/microscope/*.
      // Listed last so specific local/profiles/* entries above can land on existing /docs/microscope/profiles.
      {
        path: 'local/:pathMatch(.*)*',
        redirect: to => {
          const sub = (to.params.pathMatch as string[] | undefined)?.join('/') ?? '';
          return sub ? `/docs/microscope/${sub}` : '/docs/microscope';
        }
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
