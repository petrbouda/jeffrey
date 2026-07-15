import { createRouter, createWebHistory } from 'vue-router';
import AppLayout from '@/layout/AppLayout.vue';
import { profileChildRoutes } from '@/router/profileChildRoutes';

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
      children: [
        {
          path: 'recordings',
          name: 'recordings',
          component: () => import('@/views/global/RecordingsView.vue')
        },
        {
          path: 'workspaces',
          name: 'workspaces',
          component: () => import('@/views/workspaces/WorkspacesView.vue')
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/global/SettingsView.vue')
        },
        {
          path: 'guardian-guards',
          name: 'guardian-guards',
          component: () => import('@/views/global/GuardiansView.vue')
        }
      ]
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
    // Workspace-based routes (nested under hubs)
    {
      path: '/hubs/:hubId/workspaces/:workspaceId',
      component: () => import('@/layout/WorkspaceLayout.vue'),
      children: [
        {
          path: '',
          redirect: to => `/hubs/${to.params.hubId}/workspaces/${to.params.workspaceId}/projects`
        },
        {
          path: 'projects',
          name: 'workspace-projects',
          component: () => import('@/views/workspaces/WorkspacesView.vue')
        },
        {
          path: 'projects/:projectId',
          component: AppLayout,
          children: [
            {
              path: '',
              name: 'project',
              component: () => import('@/views/workspaces/detail/ProjectDetail.vue'),
              meta: { layout: 'project' },
              children: [
                {
                  path: '',
                  name: 'project-default',
                  redirect: to =>
                    `/hubs/${to.params.hubId}/workspaces/${to.params.workspaceId}/projects/${to.params.projectId}/instances/timeline`
                },
                {
                  path: 'settings',
                  name: 'project-settings',
                  component: () => import('@/views/workspaces/detail/SettingsView.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'profiler-settings',
                  name: 'project-profiler-settings',
                  component: () =>
                    import('@/views/workspaces/detail/ProjectProfilerSettingsView.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'events/live-stream',
                  name: 'project-live-stream',
                  component: () => import('@/views/workspaces/detail/ProjectLiveStreamView.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'events/replay-stream',
                  name: 'project-replay-stream',
                  component: () => import('@/views/workspaces/detail/ProjectReplayStreamView.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'instances',
                  name: 'project-instances-overview',
                  component: () => import('@instances/InstancesOverview.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'instances/timeline',
                  name: 'project-instances-timeline',
                  component: () => import('@instances/InstancesTimeline.vue'),
                  meta: { layout: 'project' }
                },
                {
                  path: 'instances/:instanceId',
                  name: 'project-instance-detail',
                  component: () => import('@instances/InstanceDetail.vue'),
                  meta: { layout: 'project' }
                }
              ]
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
