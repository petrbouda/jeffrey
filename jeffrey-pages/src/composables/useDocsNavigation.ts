/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import { computed } from 'vue';
import { useRoute } from 'vue-router';
import type { DocSection, DocPage, AdjacentPages, DocPageWithCategory, CurrentPageInfo } from '@/types/docs';

export interface BreadcrumbItem {
  label: string
  to?: string
}

export type Product = 'microscope' | 'server';

export interface ProductInfo {
  id: Product;
  title: string;
  icon: string;
  hubPath: string;
}

export const PRODUCTS: Record<Product, ProductInfo> = {
  microscope: {
    id: 'microscope',
    title: 'Jeffrey Microscope',
    icon: 'bi-pc-display',
    hubPath: '/docs/microscope'
  },
  server: {
    id: 'server',
    title: 'Jeffrey Server',
    icon: 'bi-cloud',
    hubPath: '/docs/server'
  }
};

// Top-level path segments owned by each product. Used for product detection from the route.
// 'local' is kept here so a direct hit on a legacy URL still resolves to the Microscope sidebar
// in the brief moment before the router redirects to /docs/microscope/*.
const MICROSCOPE_SEGMENTS = new Set(['microscope', 'local', 'events', 'ai']);
const SERVER_SEGMENTS = new Set(['server', 'cli', 'agent', 'jib']);

export const microscopeNavigation: DocSection[] = [
  // Top-level single-page entries — promoted out of the "Jeffrey Microscope" group
  // so the most-used links sit at the root of the sidebar. Synthetic section paths
  // (prefixed with `_`) keep them out of the breadcrumb/section auto-expand logic
  // that matches against the URL's first segment.
  {
    title: 'Overview',
    path: '_microscope-overview',
    icon: 'bi-info-circle',
    children: [{ title: 'Overview', to: '/docs/microscope' }]
  },
  {
    title: 'Install Microscope',
    path: '_microscope-install',
    icon: 'bi-download',
    children: [{ title: 'Install Microscope', to: '/docs/microscope/installation' }]
  },
  {
    title: 'Quick Start',
    path: '_microscope-quickstart',
    icon: 'bi-rocket-takeoff',
    children: [{ title: 'Quick Start', to: '/docs/microscope/quick-start' }]
  },
  {
    title: 'Architecture',
    path: '_microscope-architecture',
    icon: 'bi-diagram-3',
    children: [
      { title: 'Diagram', to: '/docs/microscope#architecture' },
      { title: 'Storage', to: '/docs/microscope/storage' }
    ]
  },
  {
    title: 'Jeffrey Microscope',
    path: 'microscope',
    icon: 'bi-pc-display',
    children: [
      { title: 'Recordings', path: 'recordings' },
      { title: 'Profiles', path: 'profiles' },
      { title: 'Workspaces', path: 'workspaces' },
      {
        title: 'Projects',
        path: 'projects',
        children: [
          { title: 'Overview', path: 'projects' },
          { title: 'Profiles', path: 'projects/profiles' },
          { title: 'Recordings', path: 'projects/recordings' },
          { title: 'Repository', path: 'projects/repository' },
          { title: 'Instances', path: 'projects/instances' },
          { title: 'Profiler Settings', path: 'projects/profiler-settings' },
          { title: 'Scheduler', path: 'projects/scheduler' },
          { title: 'Event Streaming', path: 'projects/event-streaming' }
        ]
      },
      { title: 'Event Log', path: 'event-log' },
      { title: 'Profiler Settings', path: 'profiler-settings' },
      { title: 'Settings', path: 'settings' },
      {
        title: 'Deployment',
        path: 'deployment',
        children: [
          { title: 'JAR Execution', path: 'deployment/jar-execution' },
          { title: 'Docker Container', path: 'deployment/docker-container' },
          { title: 'Container with Examples', path: 'deployment/container-examples' }
        ]
      },
      {
        title: 'Configuration',
        path: 'configuration',
        children: [
          { title: 'Application Properties', path: 'configuration/application-properties' },
          { title: 'Advanced Properties', path: 'configuration/advanced-properties' },
          { title: 'Secrets', path: 'configuration/secrets' }
        ]
      }
    ]
  },
  {
    title: 'Jeffrey Events',
    path: 'events',
    icon: 'bi-activity',
    children: [
      { title: 'Overview', path: 'overview' }
    ]
  },
  {
    title: 'AI Analysis',
    path: 'ai',
    icon: 'bi-robot',
    children: [
      { title: 'Overview & Configuration', path: 'overview' },
      { title: 'JFR Analysis', path: 'jfr-analysis' },
      { title: 'Heap Dump Analysis', path: 'heap-dump-analysis' },
      { title: 'OQL Assistant', path: 'oql-assistant' }
    ]
  }
];

export const serverNavigation: DocSection[] = [
  {
    title: 'Jeffrey Server',
    path: 'server',
    icon: 'bi-cloud',
    children: [
      { title: 'Overview', to: '/docs/server' },
      { title: 'Install Server', to: '/docs/server/installation' },
      { title: 'Quick Start', to: '/docs/server/quick-start' },
      { title: 'Architecture', to: '/docs/server#architecture' },
      { title: 'Storage', to: '/docs/server/storage' },
      {
        title: 'Continuous Recording',
        path: 'continuous-recording',
        children: [
          { title: 'Overview', path: 'continuous-recording/overview' },
          { title: 'Jeffrey Deployment', path: 'continuous-recording/jeffrey-deployment' },
          { title: 'Service Deployment', path: 'continuous-recording/service-deployment' }
        ]
      },
      {
        title: 'Recording Sessions',
        path: 'recording-sessions',
        children: [
          { title: 'Overview', path: 'recording-sessions/overview' },
          { title: 'Configuration', path: 'recording-sessions/configuration' },
          { title: 'Lifecycle', path: 'recording-sessions/lifecycle' }
        ]
      },
      { title: 'gRPC API', path: 'grpc-api' },
      { title: 'Deployment', path: 'deployment' },
      {
        title: 'Configuration',
        path: 'configuration',
        children: [
          { title: 'Application Properties', path: 'configuration/application-properties' },
          { title: 'Advanced Properties', path: 'configuration/advanced-properties' }
        ]
      }
    ]
  },
  {
    title: 'Jeffrey CLI',
    path: 'cli',
    icon: 'bi-terminal',
    children: [
      { title: 'Overview', path: 'overview' },
      { title: 'Configuration', path: 'configuration' },
      { title: 'Generated Output', path: 'generated-output' },
      { title: 'Directory Structure', path: 'directory-structure' }
    ]
  },
  {
    title: 'Jeffrey Agent',
    path: 'agent',
    icon: 'bi-heart-pulse',
    children: [
      { title: 'Overview', path: 'overview' }
    ]
  },
  {
    title: 'Jeffrey JIB',
    path: 'jib',
    icon: 'bi-box-seam',
    children: [
      { title: 'Overview', path: 'overview' }
    ]
  }
];

// Union — used by global helpers like getAllDocs/search and as a back-compat export.
export const docsNavigation: DocSection[] = [...microscopeNavigation, ...serverNavigation];

export function getProductForPath(routePath: string): Product | null {
  const cleaned = routePath.replace(/^\/docs\/?/, '');
  if (!cleaned) return null;
  const first = cleaned.split('/')[0];
  if (MICROSCOPE_SEGMENTS.has(first)) return 'microscope';
  if (SERVER_SEGMENTS.has(first)) return 'server';
  return null;
}

export function navigationForProduct(product: Product): DocSection[] {
  return product === 'microscope' ? microscopeNavigation : serverNavigation;
}

// Resolve the URL for a sidebar page entry, honoring the absolute `to` override.
export function pageHref(sectionPath: string, page: DocPage): string {
  if (page.to) return page.to;
  return `/docs/${sectionPath}/${page.path ?? ''}`;
}

export function getAllDocs(): { title: string; section: string; path: string }[] {
  const docs: { title: string; section: string; path: string }[] = [];
  docsNavigation.forEach(section => {
    section.children.forEach(page => {
      if (page.children) {
        page.children.forEach(child => {
          docs.push({
            title: child.title,
            section: `${section.title} / ${page.title}`,
            path: pageHref(section.path, child)
          });
        });
      } else {
        docs.push({
          title: page.title,
          section: section.title,
          path: pageHref(section.path, page)
        });
      }
    });
  });
  return docs;
}

export function findCurrentPage(category: string, page: string): CurrentPageInfo | null {
  const section = docsNavigation.find(s => s.path === category);
  if (!section) return null;
  const pageInfo = section.children.find(p => p.path === page);
  if (!pageInfo) return null;
  return {
    ...pageInfo,
    section: section.title,
    sectionPath: section.path
  };
}

export function getAdjacentPages(category: string, page: string): AdjacentPages {
  const allPages: DocPageWithCategory[] = [];

  docsNavigation.forEach(section => {
    const isSinglePageSection = section.children.length === 1 && !section.children[0].children;

    section.children.forEach(p => {
      if (p.children) {
        p.children.forEach(child => {
          allPages.push({
            ...child,
            category: section.path,
            section: `${section.title} / ${p.title}`
          });
        });
      } else {
        allPages.push({
          ...p,
          title: isSinglePageSection ? section.title : p.title,
          category: section.path,
          section: isSinglePageSection ? '' : section.title
        });
      }
    });
  });

  const currentIndex = allPages.findIndex(
    p => p.category === category && p.path === page
  );

  return {
    prev: currentIndex > 0 ? allPages[currentIndex - 1] : null,
    next: currentIndex < allPages.length - 1 ? allPages[currentIndex + 1] : null
  };
}

export function getBreadcrumbs(routePath: string): BreadcrumbItem[] {
  const pathWithoutDocs = routePath.replace(/^\/docs\/?/, '');
  if (!pathWithoutDocs) return [];

  const breadcrumbs: BreadcrumbItem[] = [];

  const firstSegment = pathWithoutDocs.split('/')[0];
  const section = docsNavigation.find(s => s.path === firstSegment);
  if (!section) return [];

  const remainingPath = pathWithoutDocs.substring(firstSegment.length + 1) || '';

  let foundPage: DocPage | null = null;
  let parentPage: DocPage | null = null;

  for (const page of section.children) {
    if (page.children) {
      for (const child of page.children) {
        if (child.path === remainingPath || child.path === pathWithoutDocs.substring(firstSegment.length + 1)) {
          parentPage = page;
          foundPage = child;
          break;
        }
      }
      if (foundPage) break;
    }

    if (page.path === remainingPath) {
      foundPage = page;
      break;
    }
  }

  const isSinglePageSection = section.children.length === 1;

  const firstChildPath = section.children[0]?.path || '';

  if (isSinglePageSection) {
    breadcrumbs.push({
      label: section.title
    });
  } else {
    breadcrumbs.push({
      label: section.title,
      to: `/docs/${section.path}/${firstChildPath}`
    });

    if (parentPage) {
      const parentFirstChild = parentPage.children?.[0]?.path || parentPage.path;
      breadcrumbs.push({
        label: parentPage.title,
        to: `/docs/${section.path}/${parentFirstChild}`
      });
    }

    if (foundPage) {
      breadcrumbs.push({
        label: foundPage.title
      });
    }
  }

  return breadcrumbs;
}

export function useDocsNavigation() {
  const route = useRoute();

  const currentCategory = computed(() => {
    const parts = route.path.split('/');
    return parts[2] || '';
  });

  const currentPage = computed(() => {
    const parts = route.path.split('/');
    return parts[3] || '';
  });

  const currentPageInfo = computed(() => {
    return findCurrentPage(currentCategory.value, currentPage.value);
  });

  const adjacentPages = computed(() => {
    return getAdjacentPages(currentCategory.value, currentPage.value);
  });

  return {
    docsNavigation,
    currentCategory,
    currentPage,
    currentPageInfo,
    adjacentPages,
    findCurrentPage,
    getAdjacentPages,
    getAllDocs
  };
}
