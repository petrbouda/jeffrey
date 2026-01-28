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
    path: 'heap-dump/dashboard',
    name: 'profile-heap-dump-dashboard',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpDashboard.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/biggest-objects',
    name: 'profile-heap-dump-biggest-objects',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpBiggestObjects.vue'),
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
  // Application mode routes
  {
    path: 'application/http/overview',
    name: 'profile-application-http-overview',
    component: () => import('@/views/profiles/detail/application/ProfileHttpOverview.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'application/http/endpoints',
    name: 'profile-application-http-endpoints',
    component: () => import('@/views/profiles/detail/application/ProfileHttpEndpoints.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'application/jdbc',
    name: 'profile-application-jdbc',
    component: () => import('@/views/profiles/detail/application/ProfileJdbc.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'application/jdbc/overview',
    name: 'profile-application-jdbc-overview',
    component: () => import('@/views/profiles/detail/application/ProfileJdbcOverview.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'application/jdbc/statement-groups',
    name: 'profile-application-jdbc-statement-groups',
    component: () => import('@/views/profiles/detail/application/ProfileJdbcStatementGroups.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'application/jdbc-pool',
    name: 'profile-application-jdbc-pool',
    component: () => import('@/views/profiles/detail/application/ProfileJdbcPool.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'application/method-tracing/overview',
    name: 'profile-application-method-tracing-overview',
    component: () => import('@/views/profiles/detail/application/ProfileMethodTracingOverview.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'application/method-tracing/flamegraph',
    name: 'profile-application-method-tracing-flamegraph',
    component: () => import('@/views/profiles/detail/application/ProfileMethodTracingFlamegraph.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'application/method-tracing/slowest',
    name: 'profile-application-method-tracing-slowest',
    component: () => import('@/views/profiles/detail/application/ProfileMethodTracingSlowest.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'application/method-tracing/cumulated',
    name: 'profile-application-method-tracing-cumulated',
    component: () => import('@/views/profiles/detail/application/ProfileMethodTracingCumulated.vue'),
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
          path: 'global-scheduler',
          name: 'global-scheduler',
          component: () => import('@/views/global/GlobalSchedulerView.vue')
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
                  redirect: to => `/workspaces/${to.params.workspaceId}/projects/${to.params.projectId}/profiles`
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
                  component: () => import('@/views/projects/detail/ProfilesList.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'recordings',
                  name: 'project-recordings',
                  component: () => import('@/views/projects/detail/RecordingsList.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'repository',
                  name: 'project-repository',
                  component: () => import('@/views/projects/detail/RepositoryView.vue'),
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
                  component: () => import('@/views/projects/detail/ProjectProfilerSettingsView.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'alerts',
                  name: 'project-alerts',
                  component: () => import('@/views/projects/detail/AlertsView.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'messages',
                  name: 'project-messages',
                  component: () => import('@/views/projects/detail/ImportantMessagesView.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'instances',
                  name: 'project-instances',
                  component: () => import('@/views/projects/detail/instances/InstancesList.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'instances/history',
                  name: 'project-instances-history',
                  component: () => import('@/views/projects/detail/instances/InstancesHistory.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'instances/timeline',
                  name: 'project-instances-timeline',
                  component: () => import('@/views/projects/detail/instances/InstancesTimeline.vue'),
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
        const subPath = Array.isArray(pathMatch) ? pathMatch.join('/') : (pathMatch || 'overview');
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
