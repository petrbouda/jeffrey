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
        alias: 'hub',
        name: 'DocsServer',
        component: () => import('@/views/docs/hub/HubOverviewPage.vue')
      },
      // Legacy / consolidated entry-points: installation + quick-start replaced by
      // the Deployment section; /docs/hub/overview was superseded by /docs/hub.
      { path: 'server/installation', redirect: '/docs/hub/deployment' },
      { path: 'server/overview', redirect: '/docs/hub' },
      { path: 'server/quick-start', redirect: '/docs/hub/deployment' },
      // Deployment — worked example based on the jeffrey-testapp repo.
      {
        path: 'server/deployment',
        alias: 'hub/deployment',
        name: 'DocsServerDeploymentOverview',
        component: () => import('@/views/docs/hub/deployment/DeploymentOverviewPage.vue')
      },
      {
        path: 'server/deployment/jeffrey-jib',
        alias: 'hub/deployment/jeffrey-jib',
        name: 'DocsServerDeploymentJeffreyJib',
        component: () => import('@/views/docs/hub/deployment/DeploymentJeffreyJibPage.vue')
      },
      {
        path: 'server/deployment/shared-volume',
        alias: 'hub/deployment/shared-volume',
        name: 'DocsServerDeploymentSharedVolume',
        component: () => import('@/views/docs/hub/deployment/DeploymentSharedVolumePage.vue')
      },
      {
        path: 'server/deployment/jeffrey-provisioner',
        alias: ['hub/deployment/jeffrey-provisioner', 'server/deployment/jeffrey-cli', 'hub/deployment/jeffrey-cli'],
        name: 'DocsServerDeploymentJeffreyProvisioner',
        component: () => import('@/views/docs/hub/deployment/DeploymentJeffreyProvisionerPage.vue')
      },
      {
        path: 'server/deployment/helm-chart',
        alias: 'hub/deployment/helm-chart',
        name: 'DocsServerDeploymentHelmChart',
        component: () => import('@/views/docs/hub/deployment/DeploymentHelmChartPage.vue')
      },
      {
        path: 'server/architecture',
        alias: 'hub/architecture',
        name: 'DocsServerArchitecture',
        component: () => import('@/views/docs/hub/HubArchitectureOverviewPage.vue')
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
      // The IntelliJ Plugin moved to its own top-level product at /docs/intellij-plugin.
      { path: 'microscope/intellij-plugin', redirect: '/docs/intellij-plugin' },
      { path: 'microscope/intellij-plugin/jeffrey-microscope', redirect: '/docs/intellij-plugin' },
      { path: 'microscope/intellij-plugin/jfr-profiler', redirect: '/docs/intellij-plugin/jfr-profiler' },
      // Profiles
      {
        path: 'microscope/profiles',
        name: 'DocsProfiles',
        component: () => import('@/views/docs/microscope/profiles/ProfilesPage.vue')
      },
      {
        path: 'microscope/profiles/heap-dump',
        name: 'DocsProfilesHeapDump',
        component: () => import('@/views/docs/microscope/profiles/HeapDumpAnalysisPage.vue')
      },
      {
        path: 'microscope/profiles/guardian',
        name: 'DocsProfilesGuardian',
        component: () => import('@/views/docs/microscope/profiles/ProfileGuardianPage.vue')
      },
      {
        path: 'microscope/profiles/advisor',
        name: 'DocsProfilesAdvisor',
        component: () => import('@/views/docs/microscope/profiles/ProfileAdvisorPage.vue')
      },
      {
        path: 'microscope/profiles/garbage-collection',
        name: 'DocsProfilesGarbageCollection',
        component: () => import('@/views/docs/microscope/profiles/ProfileGarbageCollectionPage.vue')
      },
      {
        path: 'microscope/profiles/garbage-collection/g1',
        name: 'DocsProfilesG1Analysis',
        component: () => import('@/views/docs/microscope/profiles/ProfileG1AnalysisPage.vue')
      },
      {
        path: 'microscope/profiles/garbage-collection/zgc',
        name: 'DocsProfilesZgcAnalysis',
        component: () => import('@/views/docs/microscope/profiles/ProfileZgcAnalysisPage.vue')
      },
      {
        path: 'microscope/profiles/garbage-collection/string-symbol-tables',
        name: 'DocsProfilesStringSymbolTables',
        component: () => import('@/views/docs/microscope/profiles/ProfileGCStringSymbolTablesPage.vue')
      },
      {
        path: 'microscope/profiles/garbage-collection/finalizers',
        name: 'DocsProfilesFinalizers',
        component: () => import('@/views/docs/microscope/profiles/ProfileGCFinalizersPage.vue')
      },
      {
        path: 'microscope/profiles/class-loading',
        name: 'DocsProfilesClassLoading',
        component: () => import('@/views/docs/microscope/profiles/ProfileClassLoadingPage.vue')
      },
      {
        path: 'microscope/profiles/jit-compilation',
        name: 'DocsProfilesJitCompilation',
        component: () => import('@/views/docs/microscope/profiles/ProfileJitCompilationPage.vue')
      },
      {
        path: 'microscope/profiles/exceptions',
        name: 'DocsProfilesExceptions',
        component: () => import('@/views/docs/microscope/profiles/ProfileExceptionsPage.vue')
      },
      {
        path: 'microscope/profiles/native-memory',
        name: 'DocsProfilesNativeMemory',
        component: () => import('@/views/docs/microscope/profiles/ProfileNativeMemoryPage.vue')
      },
      {
        path: 'microscope/profiles/thread-dumps',
        name: 'DocsProfilesThreadDumps',
        component: () => import('@/views/docs/microscope/profiles/ProfileThreadDumpsPage.vue')
      },
      {
        path: 'microscope/profiles/thread-timeline',
        name: 'DocsProfilesThreadTimeline',
        component: () => import('@/views/docs/microscope/profiles/ProfileThreadTimelinePage.vue')
      },
      {
        path: 'microscope/profiles/system',
        name: 'DocsProfilesSystem',
        component: () => import('@/views/docs/microscope/profiles/ProfileSystemPage.vue')
      },
      {
        path: 'microscope/profiles/nmt',
        name: 'DocsProfilesNmt',
        component: () => import('@/views/docs/microscope/profiles/ProfileNmtPage.vue')
      },
      {
        path: 'microscope/profiles/vm-operations',
        name: 'DocsProfilesVmOperations',
        component: () => import('@/views/docs/microscope/profiles/ProfileVmOperationsPage.vue')
      },
      {
        path: 'microscope/profiles/blocking-operations',
        name: 'DocsProfilesBlockingOperations',
        component: () => import('@/views/docs/microscope/profiles/ProfileBlockingOperationsPage.vue')
      },
      {
        path: 'microscope/profiles/virtual-threads',
        name: 'DocsProfilesVirtualThreads',
        component: () => import('@/views/docs/microscope/profiles/ProfileVirtualThreadsPage.vue')
      },
      {
        path: 'microscope/profiles/security',
        name: 'DocsProfilesSecurity',
        component: () => import('@/views/docs/microscope/profiles/ProfileSecurityPage.vue')
      },
      {
        path: 'microscope/profiles/socket-io',
        name: 'DocsProfilesSocketIo',
        component: () => import('@/views/docs/microscope/profiles/ProfileSocketIoPage.vue')
      },
      {
        path: 'microscope/profiles/file-io',
        name: 'DocsProfilesFileIo',
        component: () => import('@/views/docs/microscope/profiles/ProfileFileIoPage.vue')
      },
      {
        path: 'microscope/profiles/allocations',
        name: 'DocsProfilesAllocations',
        component: () => import('@/views/docs/microscope/profiles/ProfileAllocationsPage.vue')
      },
      {
        path: 'microscope/profiles/leak-candidates',
        name: 'DocsProfilesLeakCandidates',
        component: () => import('@/views/docs/microscope/profiles/ProfileLeakCandidatesPage.vue')
      },
      // Traces moved to the standalone Jeffrey Tracing product at /docs/tracing.
      { path: 'microscope/profiles/traces', redirect: '/docs/tracing/analysis' },
      { path: 'microscope/profiles/traces/api', redirect: '/docs/tracing/instrumentation' },
      // Workspaces & Event Log
      {
        path: 'microscope/workspaces',
        name: 'DocsWorkspaces',
        component: () => import('@/views/docs/microscope/WorkspacesPage.vue')
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

      // ──── Jeffrey Hub ────
      {
        path: 'server/storage',
        alias: 'hub/storage',
        name: 'DocsServerStorage',
        component: () => import('@/views/docs/hub/HubStoragePage.vue')
      },
      // Legacy: Continuous Recording was superseded by the Deployment section.
      { path: 'server/continuous-recording/overview', redirect: '/docs/hub/deployment' },
      { path: 'server/continuous-recording/jeffrey-deployment', redirect: '/docs/hub/deployment/helm-chart' },
      { path: 'server/continuous-recording/service-deployment', redirect: '/docs/hub/deployment/helm-chart' },
      // Recording Sessions
      {
        path: 'server/recording-sessions/overview',
        alias: 'hub/recording-sessions/overview',
        name: 'DocsRecordingSessionsOverview',
        component: () => import('@/views/docs/hub/recording-sessions/RecordingSessionsOverviewPage.vue')
      },
      {
        path: 'server/recording-sessions/configuration',
        alias: 'hub/recording-sessions/configuration',
        name: 'DocsRecordingSessionsConfiguration',
        component: () => import('@/views/docs/hub/recording-sessions/RecordingSessionsConfigurationPage.vue')
      },
      {
        path: 'server/recording-sessions/lifecycle',
        alias: 'hub/recording-sessions/lifecycle',
        name: 'DocsRecordingSessionsLifecycle',
        component: () => import('@/views/docs/hub/recording-sessions/RecordingSessionsLifecyclePage.vue')
      },
      // Server gRPC API
      {
        path: 'server/grpc-api',
        alias: 'hub/grpc-api',
        name: 'DocsServerGrpcApi',
        component: () => import('@/views/docs/hub/HubGrpcApiPage.vue')
      },
      // Server Configuration — single merged page
      {
        path: 'server/configuration',
        alias: 'hub/configuration',
        name: 'DocsServerConfiguration',
        component: () => import('@/views/docs/hub/configuration/HubConfigurationPage.vue')
      },
      // Legacy: split application/advanced pages were merged into a single Configuration page.
      { path: 'server/configuration/application-properties', redirect: '/docs/hub/configuration' },
      { path: 'server/configuration/advanced-properties', redirect: '/docs/hub/configuration' },

      // ──── Jeffrey Provisioner (standalone product) ────
      {
        path: 'provisioner',
        name: 'DocsProvisioner',
        component: () => import('@/views/docs/provisioner/ProvisionerOverviewPage.vue')
      },
      {
        path: 'provisioner/configuration',
        name: 'DocsProvisionerConfiguration',
        component: () => import('@/views/docs/provisioner/ProvisionerConfigurationPage.vue')
      },
      {
        path: 'provisioner/directory-structure',
        name: 'DocsProvisionerDirectoryStructure',
        component: () => import('@/views/docs/provisioner/ProvisionerDirectoryStructurePage.vue')
      },
      {
        path: 'provisioner/generated-output',
        name: 'DocsProvisionerGeneratedOutput',
        component: () => import('@/views/docs/provisioner/ProvisionerGeneratedOutputPage.vue')
      },
      // Back-compat redirects: the old promoted overview path and the legacy /docs/cli/* paths.
      { path: 'provisioner/overview', redirect: '/docs/provisioner' },
      { path: 'cli/overview', redirect: '/docs/provisioner' },
      { path: 'cli/configuration', redirect: '/docs/provisioner/configuration' },
      { path: 'cli/directory-structure', redirect: '/docs/provisioner/directory-structure' },
      { path: 'cli/generated-output', redirect: '/docs/provisioner/generated-output' },

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
      // The Tracer API reference moved to the standalone Jeffrey Tracing product.
      { path: 'events/tracer', redirect: '/docs/tracing/instrumentation' },

      // ──── Jeffrey Tracing (standalone product) ────
      {
        path: 'tracing',
        name: 'DocsTracing',
        component: () => import('@/views/docs/tracing/TracingOverviewPage.vue')
      },
      {
        path: 'tracing/getting-started',
        name: 'DocsTracingGettingStarted',
        component: () => import('@/views/docs/tracing/TracingGettingStartedPage.vue')
      },
      {
        path: 'tracing/concepts',
        name: 'DocsTracingConcepts',
        component: () => import('@/views/docs/tracing/TracingConceptsPage.vue')
      },
      {
        path: 'tracing/instrumentation',
        name: 'DocsTracingInstrumentation',
        component: () => import('@/views/docs/tracing/TracingInstrumentationOverviewPage.vue')
      },
      // The single-page Tracer API reference was split into one page per method;
      // its general material lives on the Instrumentation Overview.
      { path: 'tracing/tracer-api', redirect: '/docs/tracing/instrumentation' },
      {
        path: 'tracing/tracer-api/run',
        name: 'DocsTracingTracerRun',
        component: () => import('@/views/docs/tracing/tracer-api/TracerRunPage.vue')
      },
      {
        path: 'tracing/tracer-api/call',
        name: 'DocsTracingTracerCall',
        component: () => import('@/views/docs/tracing/tracer-api/TracerCallPage.vue')
      },
      {
        path: 'tracing/tracer-api/current',
        name: 'DocsTracingTracerCurrent',
        component: () => import('@/views/docs/tracing/tracer-api/TracerCurrentPage.vue')
      },
      {
        path: 'tracing/tracer-api/in-span-of',
        name: 'DocsTracingTracerInSpanOf',
        component: () => import('@/views/docs/tracing/tracer-api/TracerInSpanOfPage.vue')
      },
      {
        path: 'tracing/tracer-api/stamp',
        name: 'DocsTracingTracerStamp',
        component: () => import('@/views/docs/tracing/tracer-api/TracerStampPage.vue')
      },
      {
        path: 'tracing/tracer-api/open-span-of',
        name: 'DocsTracingTracerOpenSpanOf',
        component: () => import('@/views/docs/tracing/tracer-api/TracerOpenSpanOfPage.vue')
      },
      {
        path: 'tracing/tracer-api/reenter',
        name: 'DocsTracingTracerReenter',
        component: () => import('@/views/docs/tracing/tracer-api/TracerReenterPage.vue')
      },
      {
        path: 'tracing/tracer-api/continue-in',
        name: 'DocsTracingTracerContinueIn',
        component: () => import('@/views/docs/tracing/tracer-api/TracerContinueInPage.vue')
      },
      {
        path: 'tracing/tracer-api/fork',
        name: 'DocsTracingTracerFork',
        component: () => import('@/views/docs/tracing/tracer-api/TracerForkPage.vue')
      },
      {
        path: 'tracing/tracer-api/fork-callable',
        name: 'DocsTracingTracerForkCallable',
        component: () => import('@/views/docs/tracing/tracer-api/TracerForkCallablePage.vue')
      },
      {
        path: 'tracing/tracer-api/propagating',
        name: 'DocsTracingTracerPropagating',
        component: () => import('@/views/docs/tracing/tracer-api/TracerPropagatingPage.vue')
      },
      {
        path: 'tracing/traced-annotation',
        name: 'DocsTracingTracedAnnotation',
        component: () => import('@/views/docs/tracing/TracingTracedAnnotationPage.vue')
      },
      {
        path: 'tracing/http-events',
        name: 'DocsTracingHttpEvents',
        component: () => import('@/views/docs/tracing/TracingHttpEventsPage.vue')
      },
      {
        path: 'tracing/grpc-events',
        name: 'DocsTracingGrpcEvents',
        component: () => import('@/views/docs/tracing/TracingGrpcEventsPage.vue')
      },
      {
        path: 'tracing/jdbc-events',
        name: 'DocsTracingJdbcEvents',
        component: () => import('@/views/docs/tracing/TracingJdbcEventsPage.vue')
      },
      {
        path: 'tracing/custom-events',
        name: 'DocsTracingCustomEvents',
        component: () => import('@/views/docs/tracing/TracingCustomEventsPage.vue')
      },
      {
        path: 'tracing/notifications-exceptions',
        name: 'DocsTracingNotificationsExceptions',
        component: () => import('@/views/docs/tracing/TracingNotificationsExceptionsPage.vue')
      },
      {
        path: 'tracing/jdk-events',
        name: 'DocsTracingJdkEvents',
        component: () => import('@/views/docs/tracing/TracingJdkEventsPage.vue')
      },
      {
        path: 'tracing/gc-safepoints',
        name: 'DocsTracingGcSafepoints',
        component: () => import('@/views/docs/tracing/TracingGcSafepointsPage.vue')
      },
      {
        path: 'tracing/analysis',
        name: 'DocsTracingAnalysis',
        component: () => import('@/views/docs/tracing/TracingAnalysisPage.vue')
      },
      {
        path: 'tracing/configuration',
        name: 'DocsTracingConfiguration',
        component: () => import('@/views/docs/tracing/TracingConfigurationPage.vue')
      },
      // Back-compat redirect: the promoted overview path.
      { path: 'tracing/overview', redirect: '/docs/tracing' },

      // ──── Jeffrey JIB (standalone product) ────
      {
        path: 'jib',
        name: 'DocsJeffreyJib',
        component: () => import('@/views/docs/jib/JibOverviewPage.vue')
      },
      {
        path: 'jib/configuration',
        name: 'DocsJeffreyJibConfiguration',
        component: () => import('@/views/docs/jib/JibConfigurationPage.vue')
      },
      {
        path: 'jib/setup',
        name: 'DocsJeffreyJibSetup',
        component: () => import('@/views/docs/jib/JibSetupPage.vue')
      },
      // Back-compat redirect: the old promoted overview path.
      { path: 'jib/overview', redirect: '/docs/jib' },

      // ──── IntelliJ Plugin (standalone product) ────
      {
        path: 'intellij-plugin',
        name: 'DocsIntelliJPlugin',
        component: () => import('@/views/docs/intellij-plugin/IntelliJPluginOverviewPage.vue')
      },
      {
        path: 'intellij-plugin/configuration',
        name: 'DocsIntelliJPluginConfiguration',
        component: () => import('@/views/docs/intellij-plugin/IntelliJPluginConfigurationPage.vue')
      },
      {
        path: 'intellij-plugin/setup',
        name: 'DocsIntelliJPluginSetup',
        component: () => import('@/views/docs/intellij-plugin/IntelliJPluginSetupPage.vue')
      },
      {
        path: 'intellij-plugin/jfr-profiler',
        name: 'DocsIntelliJJfrProfilerPlugin',
        component: () => import('@/views/docs/intellij-plugin/JfrProfilerPluginPage.vue')
      },

      // ──── Redirects from old paths ────
      { path: 'goals/overview', redirect: '/docs/getting-started/introduction' },
      { path: 'platform/workspaces', redirect: '/docs/microscope/workspaces' },
      { path: 'platform/recordings', redirect: '/docs/microscope/recordings' },
      { path: 'platform/projects', redirect: '/docs/microscope/projects' },
      { path: 'platform/projects/profiles', redirect: '/docs/microscope/profiles' },
      { path: 'platform/projects/recordings', redirect: '/docs/microscope/recordings' },
      { path: 'platform/projects/repository', redirect: '/docs/microscope/projects/instances' },
      { path: 'platform/projects/instances', redirect: '/docs/microscope/projects/instances' },
      { path: 'platform/projects/profiler-settings', redirect: '/docs/microscope/projects/profiler-settings' },
      { path: 'platform/projects/scheduler', redirect: '/docs/microscope/projects' },
      { path: 'platform/projects/alerts-messages', redirect: '/docs/microscope/projects/event-streaming' },
      { path: 'platform/recording-sessions/overview', redirect: '/docs/hub/recording-sessions/overview' },
      { path: 'platform/recording-sessions/configuration', redirect: '/docs/hub/recording-sessions/configuration' },
      { path: 'platform/recording-sessions/lifecycle', redirect: '/docs/hub/recording-sessions/lifecycle' },
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
      { path: 'deployments/live-recording', redirect: '/docs/hub/deployment' },
      { path: 'live-recording/overview', redirect: '/docs/hub/deployment' },
      { path: 'live-recording/jeffrey-deployment', redirect: '/docs/hub/deployment/helm-chart' },
      { path: 'live-recording/service-deployment', redirect: '/docs/hub/deployment/helm-chart' },
      { path: 'configuration/overview', redirect: '/docs/microscope/configuration/application-properties' },
      { path: 'configuration/application-properties', redirect: '/docs/microscope/configuration/application-properties' },
      { path: 'configuration/advanced-properties', redirect: '/docs/microscope/configuration/advanced-properties' },
      { path: 'configuration/secrets', redirect: '/docs/microscope/configuration/secrets' },
      { path: 'architecture/public-api', redirect: '/docs/hub/grpc-api' },
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
