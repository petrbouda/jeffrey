import {createRouter, createWebHashHistory} from 'vue-router';
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
                    path: '/common/flamegraph',
                    name: 'flamegraph',
                    component: () => import('@/views/flamegraph/Flamegraph.vue'),
                },
                {
                    path: '/common/flamegraph-sections',
                    name: 'flamegraph-sections',
                    component: () => import('@/views/flamegraph/FlamegraphSections.vue'),
                },
                {
                    path: '/common/diff-flamegraph-sections',
                    name: 'diff-flamegraph-sections',
                    component: () => import('@/views/flamegraph/DiffFlamegraphSections.vue'),
                },
                {
                    path: '/common/subsecond-sections',
                    name: 'subsecond-sections',
                    component: () => import('@/views/subsecond/SubSecondSections.vue')
                },
                {
                    path: '/common/diff-subsecond-sections',
                    name: 'diff-subsecond-sections',
                    component: () => import('@/views/subsecond/DiffSubSecondSections.vue')
                },
                {
                    path: '/common/subsecond',
                    name: 'subsecond',
                    component: () => import('@/views/subsecond/SubSecond.vue')
                },
                {
                    path: '/common/savedgraphs',
                    name: 'saved-graphs',
                    component: () => import('@/views/common/SavedGraphs.vue')
                },
                {
                    path: '/common/showSimple',
                    name: 'flamegraph-simple',
                    component: () => import('@/views/flamegraph/ShowSimple.vue')
                },
                {
                    path: '/profile/information',
                    name: 'profile-information',
                    component: () => import('@/views/profile/Information.vue')
                },
                {
                    path: '/profile/autoAnalysis',
                    name: 'profile-auto-analysis',
                    component: () => import('@/views/profile/AutoAnalysis.vue')
                },
                {
                    path: '/profile/eventViewer',
                    name: 'event-viewer',
                    component: () => import('@/views/profile/EventViewer.vue')
                }
            ]
        }
    ]
});

export default router;
