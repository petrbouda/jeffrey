/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import type { DocSection, AdjacentPages, DocPageWithCategory, CurrentPageInfo } from '@/types/docs';

export const docsNavigation: DocSection[] = [
  {
    title: 'Goals',
    path: 'goals',
    icon: 'bi-bullseye',
    children: [
      { title: 'Overview', path: 'overview' }
    ]
  },
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
      { title: 'Public API', path: 'public-api' },
      { title: 'Storage', path: 'storage' }
    ]
  },
  {
    title: 'Platform',
    path: 'concepts',
    icon: 'bi-layers',
    children: [
      { title: 'Workspaces', path: 'workspaces' },
      {
        title: 'Projects',
        path: 'projects',
        children: [
          { title: 'Overview', path: 'projects' },
          { title: 'Profiles', path: 'projects/profiles' },
          { title: 'Recordings', path: 'projects/recordings' },
          { title: 'Repository', path: 'projects/repository' },
          { title: 'Profiler Settings', path: 'projects/profiler-settings' },
          { title: 'Scheduler', path: 'projects/scheduler' },
          { title: 'Alerts & Messages', path: 'projects/alerts-messages' }
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
      }
    ]
  },
  {
    title: 'Profiles',
    path: 'profiles',
    icon: 'bi-speedometer2',
    children: [
      { title: 'Overview', path: 'overview' }
    ]
  },
  {
    title: 'Jeffrey CLI',
    path: 'cli',
    icon: 'bi-terminal',
    children: [
      { title: 'Overview', path: 'overview' },
      { title: 'Configuration', path: 'configuration' },
      { title: 'Directory Structure', path: 'directory-structure' }
    ]
  },
  {
    title: 'Deployments',
    path: 'deployments',
    icon: 'bi-cloud-upload',
    children: [
      { title: 'Overview', path: 'overview' },
      { title: 'Simple JAR Execution', path: 'simple-jar' },
      { title: 'Simple as a Container', path: 'simple-container' },
      { title: 'Container with Examples', path: 'container-examples' },
      { title: 'Live with Recording Collection', path: 'live-recording' },
      { title: 'Kubernetes', path: 'kubernetes' }
    ]
  },
  {
    title: 'Jeffrey JFR Events',
    path: 'jeffrey-jfr-events',
    icon: 'bi-broadcast',
    children: [
      { title: 'Overview', path: 'overview' }
    ]
  },
  {
    title: 'Highlighted Features',
    path: 'features',
    icon: 'bi-stars',
    children: [
      { title: 'Flamegraphs', path: 'flamegraphs' },
      { title: 'Timeseries', path: 'timeseries' },
      { title: 'Guardian', path: 'guardian' },
      { title: 'Auto Analysis', path: 'auto-analysis' }
    ]
  }
];

export function getAllDocs(): { title: string; section: string; path: string }[] {
  const docs: { title: string; section: string; path: string }[] = [];
  docsNavigation.forEach(section => {
    section.children.forEach(page => {
      docs.push({
        title: page.title,
        section: section.title,
        path: `/docs/${section.path}/${page.path}`
      });
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
    section.children.forEach(p => {
      allPages.push({
        ...p,
        category: section.path,
        section: section.title
      });
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
