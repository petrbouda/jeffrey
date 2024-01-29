import { createRouter, createWebHashHistory } from 'vue-router';
import AppLayout from '@/layout/AppLayout.vue';

const router = createRouter({
    history: createWebHashHistory(),
    routes: [
        {
            path: '/',
            component: AppLayout,
            children: [
                {
                    path: '/',
                    name: 'recordings',
                    component: () => import('@/views/Recordings.vue')
                },
                {
                    path: '/profiles',
                    name: 'profiles',
                    component: () => import('@/views/Profiles.vue')
                },
                {
                    path: '/flamegraph/complete',
                    name: 'flamegraph-complete',
                    component: () => import('@/views/flamegraph/Complete.vue'),
                },
                {
                    path: '/flamegraph/show',
                    name: 'flamegraph-show',
                    component: () => import('@/views/flamegraph/Show.vue')
                },
                {
                    path: '/flamegraph/startup',
                    name: 'flamegraph-startup',
                    component: () => import('@/views/flamegraph/Startup.vue')
                },
                {
                    path: '/profile/information',
                    name: 'profile-information',
                    component: () => import('@/views/profile/Information.vue')
                },
                {
                    path: '/profile/hints',
                    name: 'profile-hints',
                    component: () => import('@/views/profile/Hints.vue')
                },
                {
                    path: '/flamegraph/differential',
                    name: 'flamegraph-differential',
                    component: () => import('@/views/flamegraph/Differential.vue')
                }
            ]
        }
    ]
});

export default router;
