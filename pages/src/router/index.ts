import { createRouter, createWebHistory } from 'vue-router';
import AppLayout from '@/layout/AppLayout.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/projects'
    },
    {
      path: '/',
      component: () => import('@/views/Index.vue'),
      children: [
        {
          path: 'projects',
          name: 'projects',
          component: () => import('@/views/projects/ProjectsView.vue')
        },
        {
          path: 'global-scheduler',
          name: 'global-scheduler',
          component: () => import('@/views/scheduler/GlobalSchedulerView.vue')
        },
      ]
    },
    {
      path: '/projects/:projectId',
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
              redirect: to => `/projects/${to.params.projectId}/profiles`
            },
            {
              path: 'settings',
              name: 'project-settings',
              component: () => import('@/views/settings/SettingsView.vue'),
              meta: { layout: 'project' }
            },
            {
              path: 'profiles',
              name: 'project-profiles',
              component: () => import('@/views/profiles/ProfilesList.vue'),
              meta: { layout: 'project' }
            },
            {
              path: 'recordings',
              name: 'project-recordings',
              component: () => import('@/views/recordings/RecordingsList.vue'),
              meta: { layout: 'project' }
            },
            {
              path: 'repository',
              name: 'project-repository',
              component: () => import('@/views/repository/RepositoryView.vue'),
              meta: { layout: 'project' }
            },
            {
              path: 'scheduler',
              name: 'project-scheduler',
              component: () => import('@/views/scheduler/SchedulerList.vue'),
              meta: { layout: 'project' }
            }
          ]
        },
        {
          path: 'profiles/:profileId',
          component: () => import('@/views/profiles/detail/ProfileDetail.vue'),
          meta: { layout: 'profile' },
          children: [
            {
              path: '',
              redirect: to => `/projects/${to.params.projectId}/profiles/${to.params.profileId}/overview`
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
              path: 'flamegraphs/saved',
              name: 'profile-flamegraphs-saved',
              component: () => import('@/views/profiles/detail/ProfileFlamegraphsSaved.vue'),
              meta: { layout: 'profile' }
            },
            {
              path: 'flamegraphs/saved/:graphId',
              name: 'profile-flamegraph-simple',
              component: () => import('@/views/profiles/detail/ProfileFlamegraphSimple.vue'),
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
              path: 'threads',
              name: 'profile-threads',
              component: () => import('@/views/profiles/detail/ProfileThreads.vue'),
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
            }
          ]
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
});

export default router;
