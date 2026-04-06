import { createRouter, createWebHistory } from 'vue-router';
import AppLayout from '@/layout/AppLayout.vue';

// Profile child routes - shared between simplified and legacy routes
const profileChildRoutes = [
  {
    path: '',
    redirect: (to: { params: { profileId: string } }) => `/profiles/${to.params.profileId}/overview`
  },
  {
    path: 'overview',
    name: 'profile-overview',
    component: () => import('@/views/profiles/detail/ProfileConfiguration.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'guardian',
    name: 'profile-guardian',
    component: () => import('@/views/profiles/detail/ProfileGuardian.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'auto-analysis',
    name: 'profile-auto-analysis',
    component: () => import('@/views/profiles/detail/ProfileAutoAnalysis.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'ai-analysis',
    name: 'profile-ai-analysis',
    component: () => import('@/views/profiles/ProfileAiJfrAnalysis.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'event-types',
    name: 'profile-event-types',
    component: () => import('@/views/profiles/detail/ProfileEventTypes.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'flamegraphs/primary',
    name: 'profile-flamegraphs-primary',
    component: () => import('@/views/profiles/detail/ProfileFlamegraphsPrimary.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'flamegraphs/differential',
    name: 'profile-flamegraphs-differential',
    component: () => import('@/views/profiles/detail/ProfileFlamegraphsDifferential.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'subsecond/primary',
    name: 'profile-subsecond-primary',
    component: () => import('@/views/profiles/detail/ProfileSubsecondPrimary.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'subsecond/differential',
    name: 'profile-subsecond-differential',
    component: () => import('@/views/profiles/detail/ProfileSubsecondDifferential.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'flamegraph-view',
    name: 'flamegraph',
    component: () => import('@/views/profiles/detail/ProfileFlamegraphView.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'subsecond-view',
    name: 'subsecond',
    component: () => import('@/views/profiles/detail/ProfileSubSecondView.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'events',
    name: 'profile-events',
    component: () => import('@/views/profiles/detail/ProfileEvents.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'flags',
    name: 'profile-flags',
    component: () => import('@/views/profiles/detail/ProfileFlags.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'thread-statistics',
    name: 'profile-thread-statistics',
    component: () => import('@/views/profiles/detail/ProfileThreadStatistics.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'threads-timeline',
    name: 'profile-threads-timeline',
    component: () => import('@/views/profiles/detail/ProfileThreadsTimeline.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'jit-compilation',
    name: 'profile-jit-compilation',
    component: () => import('@/views/profiles/detail/ProfileJitCompilation.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-memory',
    name: 'profile-heap-memory',
    component: () => import('@/views/profiles/detail/ProfileHeapTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-memory/timeseries',
    name: 'profile-heap-memory-timeseries',
    component: () => import('@/views/profiles/detail/ProfileHeapTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'garbage-collection',
    name: 'profile-garbage-collection',
    component: () => import('@/views/profiles/detail/ProfileGarbageCollection.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'garbage-collection/timeseries',
    name: 'profile-garbage-collection-timeseries',
    component: () => import('@/views/profiles/detail/ProfileGCTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'garbage-collection/configuration',
    name: 'profile-garbage-collection-configuration',
    component: () => import('@/views/profiles/detail/ProfileGCConfiguration.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/settings',
    name: 'profile-heap-dump-settings',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpSettings.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/histogram',
    name: 'profile-heap-dump-histogram',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpHistogram.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/string-analysis',
    name: 'profile-heap-dump-string-analysis',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpStringAnalysis.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/oql',
    name: 'profile-heap-dump-oql',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpOQL.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/gc-roots',
    name: 'profile-heap-dump-gc-roots',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpGCRoots.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/gc-root-path',
    name: 'profile-heap-dump-gc-root-path',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpGCRootPath.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/threads',
    name: 'profile-heap-dump-threads',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpThreads.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/ai-analysis',
    name: 'profile-heap-dump-ai-analysis',
    component: () => import('@/views/profiles/ProfileHeapDumpAiAnalysis.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/dominator-tree',
    name: 'profile-heap-dump-dominator-tree',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpDominatorTree.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/collection-analysis',
    name: 'profile-heap-dump-collection-analysis',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpCollectionAnalysis.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/leak-suspects',
    name: 'profile-heap-dump-leak-suspects',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpLeakSuspects.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/biggest-collections',
    name: 'profile-heap-dump-biggest-collections',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpBiggestCollections.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/duplicate-objects',
    name: 'profile-heap-dump-duplicate-objects',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpDuplicateObjects.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/classloader-analysis',
    name: 'profile-heap-dump-classloader-analysis',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpClassLoaderAnalysis.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'tools/rename-frames',
    name: 'profile-tools-rename-frames',
    component: () => import('@/views/profiles/detail/ProfileToolsRenameFrames.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'tools/collapse-frames',
    name: 'profile-tools-collapse-frames',
    component: () => import('@/views/profiles/detail/ProfileToolsCollapseFrames.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'container/configuration',
    name: 'profile-container-configuration',
    component: () => import('@/views/profiles/detail/ProfileContainerConfiguration.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'performance-counters',
    name: 'profile-performance-counters',
    component: () => import('@/views/profiles/detail/ProfilePerformanceCounters.vue'),
    meta: { layout: 'profile' }
  },
  // Technologies mode routes
  {
    path: 'technologies/hub',
    name: 'profile-technologies-hub',
    component: () => import('@/views/profiles/detail/technologies/ProfileTechnologiesHub.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/http/overview',
    name: 'profile-technologies-http-overview',
    component: () => import('@/views/profiles/detail/technologies/ProfileHttpOverview.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/http/timeseries',
    name: 'profile-technologies-http-timeseries',
    component: () => import('@/views/profiles/detail/technologies/ProfileHttpTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/http/distribution',
    name: 'profile-technologies-http-distribution',
    component: () => import('@/views/profiles/detail/technologies/ProfileHttpDistribution.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/http/slowest',
    name: 'profile-technologies-http-slowest',
    component: () => import('@/views/profiles/detail/technologies/ProfileHttpSlowestRequests.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/http/endpoints',
    name: 'profile-technologies-http-endpoints',
    component: () => import('@/views/profiles/detail/technologies/ProfileHttpEndpoints.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc',
    name: 'profile-technologies-jdbc',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbc.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc/overview',
    name: 'profile-technologies-jdbc-overview',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbcOverview.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc/timeseries',
    name: 'profile-technologies-jdbc-timeseries',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbcTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc/distribution',
    name: 'profile-technologies-jdbc-distribution',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbcDistribution.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc/slowest-statements',
    name: 'profile-technologies-jdbc-slowest-statements',
    component: () =>
      import('@/views/profiles/detail/technologies/ProfileJdbcSlowestStatements.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc/statement-groups',
    name: 'profile-technologies-jdbc-statement-groups',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbcStatementGroups.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc-pool',
    name: 'profile-technologies-jdbc-pool',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbcPool.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/method-tracing/timeseries',
    name: 'profile-technologies-method-tracing-timeseries',
    component: () =>
      import('@/views/profiles/detail/technologies/ProfileMethodTracingTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/method-tracing/distribution',
    name: 'profile-technologies-method-tracing-distribution',
    component: () =>
      import('@/views/profiles/detail/technologies/ProfileMethodTracingDistribution.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/method-tracing/flamegraph',
    name: 'profile-technologies-method-tracing-flamegraph',
    component: () =>
      import('@/views/profiles/detail/technologies/ProfileMethodTracingFlamegraph.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/method-tracing/slowest',
    name: 'profile-technologies-method-tracing-slowest',
    component: () => import('@/views/profiles/detail/technologies/ProfileMethodTracingSlowest.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/method-tracing/cumulated',
    name: 'profile-technologies-method-tracing-cumulated',
    component: () =>
      import('@/views/profiles/detail/technologies/ProfileMethodTracingCumulated.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/overview',
    name: 'profile-technologies-grpc-overview',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcOverview.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/timeseries',
    name: 'profile-technologies-grpc-timeseries',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/distribution',
    name: 'profile-technologies-grpc-distribution',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcDistribution.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/slowest',
    name: 'profile-technologies-grpc-slowest',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcSlowestCalls.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/services',
    name: 'profile-technologies-grpc-services',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcServices.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/traffic',
    name: 'profile-technologies-grpc-traffic',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcTraffic.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/size-timeseries',
    name: 'profile-technologies-grpc-size-timeseries',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcSizeTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/size-distribution',
    name: 'profile-technologies-grpc-size-distribution',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcSizeDistribution.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/largest',
    name: 'profile-technologies-grpc-largest',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcLargestCalls.vue'),
    meta: { layout: 'profile' }
  }
];

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
      redirect: '/workspaces'
    },
    {
      path: '/',
      component: () => import('@/views/Index.vue'),
      children: [
        {
          path: 'workspaces',
          name: 'workspaces',
          component: () => import('@/views/projects/ProjectsView.vue')
        },
        {
          path: 'quick-analysis',
          name: 'quick-analysis',
          component: () => import('@/views/global/QuickAnalysisView.vue')
        },
        {
          path: 'event-log',
          name: 'event-log',
          component: () => import('@/views/global/EventLogView.vue')
        },
        {
          path: 'profiler-settings',
          name: 'profiler-settings',
          component: () => import('@/views/global/ProfilerSettingsView.vue')
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/global/SettingsView.vue')
        }
      ]
    },
    // Legacy redirect for old projects URL
    {
      path: '/projects',
      redirect: '/workspaces'
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
    // Workspace-based routes
    {
      path: '/workspaces/:workspaceId',
      component: () => import('@/layout/WorkspaceLayout.vue'),
      children: [
        {
          path: '',
          redirect: to => `/workspaces/${to.params.workspaceId}/projects`
        },
        {
          path: 'projects',
          name: 'workspace-projects',
          component: () => import('@/views/projects/ProjectsView.vue')
        },
        {
          path: 'projects/:projectId',
          component: AppLayout,
          children: [
            {
              path: '',
              name: 'project',
              component: () => import('@/views/projects/detail/ProjectDetail.vue'),
              meta: { layout: 'project' },
              children: [
                {
                  path: '',
                  name: 'project-default',
                  redirect: to =>
                    `/workspaces/${to.params.workspaceId}/projects/${to.params.projectId}/instances`
                },
                {
                  path: 'settings',
                  name: 'project-settings',
                  component: () => import('@/views/projects/detail/SettingsView.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'profiles',
                  name: 'project-profiles',
                  redirect: to =>
                    `/workspaces/${to.params.workspaceId}/projects/${to.params.projectId}/recordings`
                },
                {
                  path: 'recordings',
                  name: 'project-recordings',
                  component: () => import('@/views/projects/detail/RecordingsList.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'scheduler',
                  name: 'project-scheduler',
                  component: () => import('@/views/projects/detail/SchedulerList.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'profiler-settings',
                  name: 'project-profiler-settings',
                  component: () =>
                    import('@/views/projects/detail/ProjectProfilerSettingsView.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'event-streaming',
                  name: 'project-event-streaming',
                  component: () =>
                    import('@/views/projects/detail/EventStreamingDashboard.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'instances',
                  name: 'project-instances-overview',
                  component: () =>
                    import('@/views/projects/detail/instances/InstancesOverview.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'instances/timeline',
                  name: 'project-instances-timeline',
                  component: () =>
                    import('@/views/projects/detail/instances/InstancesTimeline.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'instances/:instanceId',
                  name: 'project-instance-detail',
                  component: () => import('@/views/projects/detail/instances/InstanceDetail.vue'),
                  meta: { layout: 'project' }
                }
              ]
            }
          ]
        }
      ]
    },
    // Legacy redirects for old project routes
    {
      path: '/projects/:projectId',
      redirect: () => {
        // For legacy routes, we'll need to determine workspace from project
        // For now, redirect to workspaces to let user select
        return '/workspaces';
      }
    },
    // Legacy redirect for old profile routes to simplified URLs
    {
      path: '/workspaces/:workspaceId/projects/:projectId/profiles/:profileId/:pathMatch(.*)*',
      redirect: to => {
        const profileId = to.params.profileId;
        const pathMatch = to.params.pathMatch;
        const subPath = Array.isArray(pathMatch) ? pathMatch.join('/') : pathMatch || 'overview';
        return `/profiles/${profileId}/${subPath}`;
      }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
});

export default router;
