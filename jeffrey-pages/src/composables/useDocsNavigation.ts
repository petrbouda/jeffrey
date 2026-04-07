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

export const docsNavigation: DocSection[] = [
  {
    title: 'Getting Started',
    path: 'getting-started',
    icon: 'bi-rocket-takeoff',
    children: [
      { title: 'Introduction', path: 'introduction' },
      { title: 'Installation', path: 'installation' },
      { title: 'Quick Start', path: 'quick-start' }
    ]
  },
  {
    title: 'Architecture',
    path: 'architecture',
    icon: 'bi-diagram-3',
    children: [
      { title: 'Overview', path: 'overview' },
      { title: 'Storage', path: 'storage' }
    ]
  },
  {
    title: 'Jeffrey Local',
    path: 'local',
    icon: 'bi-pc-display',
    children: [
      { title: 'Overview', path: 'overview' },
      { title: 'Quick Analysis', path: 'quick-analysis' },
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
    title: 'Jeffrey Server',
    path: 'server',
    icon: 'bi-cloud',
    children: [
      { title: 'Overview', path: 'overview' },
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

export function getAllDocs(): { title: string; section: string; path: string }[] {
  const docs: { title: string; section: string; path: string }[] = [];
  docsNavigation.forEach(section => {
    section.children.forEach(page => {
      // If this item has children, it's a parent category - add its children instead
      if (page.children) {
        page.children.forEach(child => {
          docs.push({
            title: child.title,
            section: `${section.title} / ${page.title}`,
            path: `/docs/${section.path}/${child.path}`
          });
        });
      } else {
        // Regular page without children
        docs.push({
          title: page.title,
          section: section.title,
          path: `/docs/${section.path}/${page.path}`
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
      // If this item has children, it's a parent category - add its children instead
      if (p.children) {
        p.children.forEach(child => {
          allPages.push({
            ...child,
            category: section.path,
            // Include parent title in the section path
            section: `${section.title} / ${p.title}`
          });
        });
      } else {
        // Regular page without children
        allPages.push({
          ...p,
          // For single-page sections, use section title instead of child title
          title: isSinglePageSection ? section.title : p.title,
          category: section.path,
          // Don't show section for single-page sections (it would be redundant)
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
  // Parse: /docs/concepts/projects/profiles → ['concepts', 'projects', 'profiles']
  const pathWithoutDocs = routePath.replace(/^\/docs\/?/, '');
  if (!pathWithoutDocs) return [];

  const breadcrumbs: BreadcrumbItem[] = [];

  // Find section by first path segment
  const firstSegment = pathWithoutDocs.split('/')[0];
  const section = docsNavigation.find(s => s.path === firstSegment);
  if (!section) return [];

  // Build the remaining path after section
  const remainingPath = pathWithoutDocs.substring(firstSegment.length + 1) || '';

  // Find the page in section children
  let foundPage: DocPage | null = null;
  let parentPage: DocPage | null = null;

  for (const page of section.children) {
    // Check if this page has children (nested navigation)
    if (page.children) {
      // Check if any child matches the remaining path
      for (const child of page.children) {
        if (child.path === remainingPath || child.path === pathWithoutDocs.substring(firstSegment.length + 1)) {
          parentPage = page;
          foundPage = child;
          break;
        }
      }
      if (foundPage) break;
    }

    // Check direct match (for simple pages like 'overview', 'workspaces')
    if (page.path === remainingPath) {
      foundPage = page;
      break;
    }
  }

  // Build breadcrumbs
  // Check if this is a single-page section (only one child with same or similar title)
  const isSinglePageSection = section.children.length === 1;

  // Section level (e.g., "Platform" for path "concepts")
  const firstChildPath = section.children[0]?.path || '';

  if (isSinglePageSection) {
    // For single-page sections, just show the section title (no link, it's current page)
    breadcrumbs.push({
      label: section.title
    });
  } else {
    // For multi-page sections, show section with link
    breadcrumbs.push({
      label: section.title,
      to: `/docs/${section.path}/${firstChildPath}`
    });

    // Parent level (if nested, e.g., "Projects")
    if (parentPage) {
      const parentFirstChild = parentPage.children?.[0]?.path || parentPage.path;
      breadcrumbs.push({
        label: parentPage.title,
        to: `/docs/${section.path}/${parentFirstChild}`
      });
    }

    // Current page level (no link - it's the current page)
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
