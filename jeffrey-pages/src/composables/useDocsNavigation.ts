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
      { title: 'Overview', to: '/docs/microscope/architecture' },
      { title: 'Storage', to: '/docs/microscope/storage' }
    ]
  },
  {
    title: 'Recordings & Profiles',
    path: '_microscope-core',
    icon: 'bi-collection-play',
    children: [
      { title: 'Recordings', to: '/docs/microscope/recordings' },
      { title: 'Profiles', to: '/docs/microscope/profiles' }
    ]
  },
  {
    title: 'Server Integration',
    path: '_microscope-server-integration',
    icon: 'bi-cloud',
    children: [
      { title: 'Workspaces', to: '/docs/microscope/workspaces' },
      {
        title: 'Projects',
        path: 'projects',
        children: [
          { title: 'Overview', to: '/docs/microscope/projects' },
          { title: 'Instances', to: '/docs/microscope/projects/instances' },
          { title: 'Event Streaming', to: '/docs/microscope/projects/event-streaming' },
          { title: 'Profiler Settings', to: '/docs/microscope/projects/profiler-settings' }
        ]
      },
      { title: 'Event Log', to: '/docs/microscope/event-log' },
      { title: 'Profiler Settings', to: '/docs/microscope/profiler-settings' }
    ]
  },
  {
    title: 'Configuration',
    path: 'microscope/configuration',
    icon: 'bi-gear',
    children: [
      { title: 'Application Properties', to: '/docs/microscope/configuration/application-properties' },
      { title: 'Advanced Properties', to: '/docs/microscope/configuration/advanced-properties' },
      { title: 'Secrets', to: '/docs/microscope/configuration/secrets' }
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

// Resolve adjacent (prev/next) pages by route URL.
// Sections may use synthetic paths (`_…`) that don't appear in URLs, and children may
// declare absolute `to:` overrides without a `path:`. Match by the resolved URL via
// `pageHref` so the lookup works regardless of how the entry was declared.
//
// The flat list is scoped to the current *product* — Microscope and Server are
// separate sidebars, so the last Microscope page must NOT chain into the first
// Server page (and vice versa). Routes that don't belong to either product
// (e.g. /docs/getting-started/…) default to Microscope, matching DocsSidebar.
export function getAdjacentPages(routePath: string): AdjacentPages {
  const product = getProductForPath(routePath) ?? 'microscope';
  const sections = navigationForProduct(product);
  const allPages: DocPageWithCategory[] = [];

  for (const section of sections) {
    const isSinglePageSection = section.children.length === 1 && !section.children[0].children;

    for (const p of section.children) {
      if (p.children) {
        for (const child of p.children) {
          allPages.push({
            ...child,
            to: pageHref(section.path, child),
            category: section.path,
            section: `${section.title} / ${p.title}`
          });
        }
      } else {
        allPages.push({
          ...p,
          to: pageHref(section.path, p),
          title: isSinglePageSection ? section.title : p.title,
          category: section.path,
          section: isSinglePageSection ? '' : section.title
        });
      }
    }
  }

  // Strip query / hash from both sides — entries like `/docs/server#architecture`
  // should still align with `/docs/server` so the prev/next pair stays sensible.
  const normalize = (url: string | undefined) => (url ?? '').split(/[#?]/)[0];
  const targetPath = normalize(routePath);
  const currentIndex = allPages.findIndex(p => normalize(p.to) === targetPath);

  if (currentIndex < 0) {
    return { prev: null, next: null };
  }

  return {
    prev: currentIndex > 0 ? allPages[currentIndex - 1] : null,
    next: currentIndex < allPages.length - 1 ? allPages[currentIndex + 1] : null
  };
}

export function getBreadcrumbs(routePath: string): BreadcrumbItem[] {
  // Sections may use synthetic paths (`_…`) that don't appear in URLs, so we can't
  // route by section.path anymore. Instead, find the section whose page (or grandchild
  // page) resolves — via `pageHref` — to the current route.
  for (const section of docsNavigation) {
    let foundPage: DocPage | null = null;
    let parentPage: DocPage | null = null;

    for (const page of section.children) {
      if (page.children) {
        for (const child of page.children) {
          if (pageHref(section.path, child) === routePath) {
            parentPage = page;
            foundPage = child;
            break;
          }
        }
        if (foundPage) break;
      }
      if (pageHref(section.path, page) === routePath) {
        foundPage = page;
        break;
      }
    }

    if (!foundPage) continue;

    const breadcrumbs: BreadcrumbItem[] = [];
    const isSinglePageSection = section.children.length === 1;

    if (isSinglePageSection) {
      breadcrumbs.push({ label: section.title });
    } else {
      const firstChild = section.children[0];
      breadcrumbs.push({
        label: section.title,
        to: pageHref(section.path, firstChild)
      });

      if (parentPage) {
        const parentTarget = parentPage.children?.[0] ?? parentPage;
        breadcrumbs.push({
          label: parentPage.title,
          to: pageHref(section.path, parentTarget)
        });
      }

      breadcrumbs.push({ label: foundPage.title });
    }

    return breadcrumbs;
  }

  return [];
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
    return getAdjacentPages(route.path);
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
