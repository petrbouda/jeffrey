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
                    path: '/flamegraph/general',
                    name: 'flamegraph-general',
                    component: () => import('@/views/flamegraph/General.vue'),
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
                    path: '/flamegraph/startup/comparison',
                    name: 'flamegraph-startup-comparison',
                    component: () => import('@/views/flamegraph/Startup.vue')
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
