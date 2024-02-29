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
                    name: 'index',
                    component: () => import('@/views/Index.vue'),
                    children: [
                        {
                            path: '/index/profiles',
                            name: 'profiles',
                            component: () => import('@/views/Profiles.vue'),
                        },
                        {
                            path: '/index/recordings',
                            name: 'recordings',
                            component: () => import('@/views/Recordings.vue'),
                        },
                    ]
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
                    path: '/flamegraph/showSimple',
                    name: 'flamegraph-show-simple',
                    component: () => import('@/views/flamegraph/ShowSimple.vue')
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
                },
                {
                    path: '/sections',
                    name: 'sections',
                    component: () => import('@/views/Sections.vue')
                },
            ]
        }
    ]
});

export default router;
