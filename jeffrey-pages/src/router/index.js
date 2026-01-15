import { createRouter, createWebHashHistory } from 'vue-router'
import Home from '../pages/Home.vue'
import QuickStart from '../pages/LaunchIt.vue'
import TourWithExamples from '../pages/TourWithExamples.vue'
import NewFeatures from '../pages/NewFeatures.vue'
import Blog from '../pages/Blog.vue'
import JavaProfilingLiterature from '../pages/blog/JavaProfilingLiterature.vue'
import JfrInDepth from '../pages/blog/JfrInDepth.vue'
import Jeffrey04Announcement from '../pages/blog/Jeffrey04Announcement.vue'
import DocsLayout from '../pages/docs/DocsLayout.vue'
import DocsPage from '../pages/docs/DocsPage.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/features',
    name: 'HomeFeatures',
    component: Home
  },
  {
    path: '/launch-it',
    name: 'LaunchIt',
    component: QuickStart
  },
  {
    path: '/tour-with-examples',
    name: 'TourWithExamples',
    component: TourWithExamples
  },
  {
    path: '/new-features',
    name: 'NewFeatures',
    component: NewFeatures
  },
  {
    path: '/blog',
    name: 'Blog',
    component: Blog
  },
  {
    path: '/blog/java-profiling-literature',
    name: 'JavaProfilingLiterature',
    component: JavaProfilingLiterature
  },
  {
    path: '/blog/jfr-in-depth',
    name: 'JfrInDepth',
    component: JfrInDepth
  },
  {
    path: '/blog/jeffrey-04-announcement',
    name: 'Jeffrey04Announcement',
    component: Jeffrey04Announcement
  },
  {
    path: '/docs',
    component: DocsLayout,
    children: [
      {
        path: '',
        redirect: '/docs/getting-started/introduction'
      },
      {
        path: ':category/:page',
        name: 'DocsPage',
        component: DocsPage,
        props: true
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    if (to.hash) {
      return {
        el: to.hash,
        behavior: 'smooth',
        top: 80 // Offset for fixed header if any
      }
    }
    return { top: 0 }
  }
})

export default router
