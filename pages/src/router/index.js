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
                    path: '/common/flamegraphs',
                    name: 'flamegraphs',
                    component: () => import('@/views/common/Flamegraphs.vue'),
                },
                {
                    path: '/common/subsecond',
                    name: 'subsecond',
                    component: () => import('@/views/common/SubSecond.vue')
                },
                {
                    path: '/common/showSimple',
                    name: 'flamegraph-simple',
                    component: () => import('@/views/common/ShowSimple.vue')
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
                    component: () => import('@/views/common/Differential.vue')
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
