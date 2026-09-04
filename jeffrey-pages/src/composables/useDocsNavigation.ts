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

export type Product = 'microscope' | 'microscope-mcp' | 'hub' | 'provisioner' | 'jib' | 'intellij-plugin' | 'tracing';

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
  'microscope-mcp': {
    id: 'microscope-mcp',
    title: 'Microscope MCP',
    icon: 'bi-plugin',
    hubPath: '/docs/microscope-mcp'
  },
  hub: {
    id: 'hub',
    title: 'Jeffrey Hub',
    icon: 'bi-cloud',
    hubPath: '/docs/hub'
  },
  provisioner: {
    id: 'provisioner',
    title: 'Jeffrey Provisioner',
    icon: 'bi-terminal',
    hubPath: '/docs/provisioner'
  },
  jib: {
    id: 'jib',
    title: 'Jeffrey JIB',
    icon: 'bi-box-seam',
    hubPath: '/docs/jib'
  },
  'intellij-plugin': {
    id: 'intellij-plugin',
    title: 'IntelliJ Plugin',
    icon: 'bi-window-stack',
    hubPath: '/docs/intellij-plugin'
  },
  tracing: {
    id: 'tracing',
    title: 'Jeffrey Tracing',
    icon: 'bi-bezier2',
    hubPath: '/docs/tracing'
  }
};

// Top-level path segments owned by each product. Used for product detection from the route.
// 'local' is kept here so a direct hit on a legacy URL still resolves to the Microscope sidebar
// in the brief moment before the router redirects to /docs/microscope/*.
const MICROSCOPE_SEGMENTS = new Set(['microscope', 'local', 'events', 'ai']);
// The MCP integration is its own product. Its segment is distinct from 'microscope', so a
// /docs/microscope-mcp/* URL never resolves to the Microscope sidebar.
const MICROSCOPE_MCP_SEGMENTS = new Set(['microscope-mcp']);
// 'server' is kept alongside 'hub' so a direct hit on a legacy /docs/server/* URL still
// resolves to the Hub sidebar in the brief moment before the router redirects to /docs/hub/*.
const HUB_SEGMENTS = new Set(['hub', 'server', 'agent']);
// The Provisioner is its own product. 'cli' is kept so a direct hit on a legacy
// /docs/cli/* URL still resolves to the Provisioner sidebar before the router redirects.
const PROVISIONER_SEGMENTS = new Set(['provisioner', 'cli']);
// Jeffrey JIB is its own product (previously a single page under Hub).
const JIB_SEGMENTS = new Set(['jib']);
// The IntelliJ Plugin is its own product (previously a group under Microscope).
const INTELLIJ_PLUGIN_SEGMENTS = new Set(['intellij-plugin']);
// Jeffrey Tracing is its own product (previously spread across Microscope's
// events/profiles pages).
const TRACING_SEGMENTS = new Set(['tracing']);

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
    // The IntelliJ Plugin has its own top-level documentation section; Microscope keeps just a link.
    title: 'IntelliJ Plugin',
    path: '_microscope-intellij-plugin-link',
    icon: 'bi-window-stack',
    crossLink: true,
    children: [{ title: 'IntelliJ Plugin docs', to: '/docs/intellij-plugin' }]
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
    // The event catalog moved to the Jeffrey Tracing product; Microscope keeps just a link.
    title: 'Jeffrey Events',
    path: '_microscope-events-link',
    icon: 'bi-activity',
    crossLink: true,
    children: [{ title: 'Event catalog', to: '/docs/tracing/events' }]
  },
  {
    // Jeffrey Tracing has its own top-level documentation section; Microscope keeps just a link.
    title: 'Jeffrey Tracing',
    path: '_microscope-tracing-link',
    icon: 'bi-bezier2',
    crossLink: true,
    children: [{ title: 'Jeffrey Tracing docs', to: '/docs/tracing' }]
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
  },
  {
    // The MCP integration has its own top-level documentation section; Microscope keeps just a link.
    title: 'Microscope MCP',
    path: '_microscope-mcp-link',
    icon: 'bi-plugin',
    crossLink: true,
    children: [{ title: 'Microscope MCP docs', to: '/docs/microscope-mcp' }]
  }
];

export const microscopeMcpNavigation: DocSection[] = [
  // Standalone product section for the MCP integration — the server that lets an outside client
  // (an interactive Claude Code session) read the profiles this Microscope has analysed. Single-page
  // entries use synthetic `_` paths with absolute `to:` children; groups render as collapsible sections.
  {
    title: 'Overview',
    path: '_microscope-mcp-overview',
    icon: 'bi-info-circle',
    children: [{ title: 'Overview', to: '/docs/microscope-mcp' }]
  },
  {
    title: 'Getting Started',
    path: '_microscope-mcp-getting-started',
    icon: 'bi-rocket-takeoff',
    children: [
      { title: 'Enabling the Server', to: '/docs/microscope-mcp/enabling' },
      { title: 'Claude Code Plugin', to: '/docs/microscope-mcp/plugin' }
    ]
  },
  {
    title: 'Reference',
    path: '_microscope-mcp-reference',
    icon: 'bi-list-columns',
    children: [
      { title: 'Tool Reference', to: '/docs/microscope-mcp/tools' },
      { title: 'Skills', to: '/docs/microscope-mcp/skills' },
      { title: 'Subagent', to: '/docs/microscope-mcp/agent' }
    ]
  },
  {
    title: 'Recipes',
    path: '_microscope-mcp-recipes',
    icon: 'bi-lightbulb',
    children: [{ title: 'Recipes', to: '/docs/microscope-mcp/recipes' }]
  },
  {
    title: 'Other Clients',
    path: '_microscope-mcp-other-clients',
    icon: 'bi-terminal-split',
    children: [{ title: 'Other Clients', to: '/docs/microscope-mcp/other-clients' }]
  },
  {
    // The in-app assistant is the other direction; Microscope MCP keeps just a link.
    title: 'AI Analysis',
    path: '_microscope-mcp-ai-link',
    icon: 'bi-robot',
    crossLink: true,
    children: [{ title: 'AI Analysis docs', to: '/docs/ai/overview' }]
  }
];

export const hubNavigation: DocSection[] = [
  // Top-level single-page entries — promoted out of the "Jeffrey Hub" group
  // so the most-used links sit at the root of the sidebar. Synthetic section paths
  // (prefixed with `_`) keep them out of the breadcrumb/section auto-expand logic
  // that matches against the URL's first segment.
  {
    title: 'Overview',
    path: '_hub-overview',
    icon: 'bi-info-circle',
    children: [{ title: 'Overview', to: '/docs/hub' }]
  },
  {
    title: 'Deployment by Example',
    path: 'hub/deployment',
    icon: 'bi-cloud-upload',
    children: [
      { title: 'Overview', to: '/docs/hub/deployment' },
      { title: 'Jeffrey JIB Extension', to: '/docs/hub/deployment/jeffrey-jib' },
      { title: 'Shared Volume', to: '/docs/hub/deployment/shared-volume' },
      { title: 'Jeffrey Provisioner', to: '/docs/hub/deployment/jeffrey-provisioner' },
      { title: 'Helm Chart', to: '/docs/hub/deployment/helm-chart' }
    ]
  },
  {
    title: 'Architecture',
    path: '_hub-architecture',
    icon: 'bi-diagram-3',
    children: [
      { title: 'Overview', to: '/docs/hub/architecture' },
      { title: 'Storage', to: '/docs/hub/storage' },
      { title: 'gRPC API', to: '/docs/hub/grpc-api' }
    ]
  },
  {
    title: 'Recording Sessions',
    path: 'hub/recording-sessions',
    icon: 'bi-collection',
    children: [
      { title: 'Overview', to: '/docs/hub/recording-sessions/overview' },
      { title: 'Configuration', to: '/docs/hub/recording-sessions/configuration' },
      { title: 'Lifecycle', to: '/docs/hub/recording-sessions/lifecycle' }
    ]
  },
  {
    title: 'Configuration',
    path: '_hub-configuration',
    icon: 'bi-gear',
    children: [{ title: 'Configuration', to: '/docs/hub/configuration' }]
  },
  {
    // The Provisioner has its own top-level documentation section; Hub keeps just a link.
    title: 'Jeffrey Provisioner',
    path: '_hub-provisioner-link',
    icon: 'bi-terminal',
    crossLink: true,
    children: [{ title: 'Provisioner docs', to: '/docs/provisioner' }]
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
    // Jeffrey JIB has its own top-level documentation section; Hub keeps just a link.
    title: 'Jeffrey JIB',
    path: '_hub-jib-link',
    icon: 'bi-box-seam',
    crossLink: true,
    children: [{ title: 'Jeffrey JIB docs', to: '/docs/jib' }]
  }
];

export const provisionerNavigation: DocSection[] = [
  // Standalone product section for the Provisioner. Overview is promoted to the root
  // of the sidebar (synthetic `_` path); the remaining pages follow as single entries.
  {
    title: 'Overview',
    path: '_provisioner-overview',
    icon: 'bi-info-circle',
    children: [{ title: 'Overview', to: '/docs/provisioner' }]
  },
  {
    title: 'Configuration',
    path: '_provisioner-configuration',
    icon: 'bi-gear',
    children: [{ title: 'Configuration', to: '/docs/provisioner/configuration' }]
  },
  {
    title: 'Generated Output',
    path: '_provisioner-generated-output',
    icon: 'bi-file-earmark-text',
    children: [{ title: 'Generated Output', to: '/docs/provisioner/generated-output' }]
  },
  {
    title: 'Directory Structure',
    path: '_provisioner-directory-structure',
    icon: 'bi-folder2-open',
    children: [{ title: 'Directory Structure', to: '/docs/provisioner/directory-structure' }]
  },
  {
    // Cross-link into the Hub deployment example that wires the Provisioner into a cluster.
    title: 'Deployment',
    path: '_provisioner-deployment',
    icon: 'bi-cloud-upload',
    crossLink: true,
    children: [{ title: 'Deploying with the Hub', to: '/docs/hub/deployment/jeffrey-provisioner' }]
  }
];

export const jibNavigation: DocSection[] = [
  // Standalone product section for Jeffrey JIB. Overview is promoted to the root of the
  // sidebar (synthetic `_` path); the remaining pages follow as single entries.
  {
    title: 'Overview',
    path: '_jib-overview',
    icon: 'bi-info-circle',
    children: [{ title: 'Overview', to: '/docs/jib' }]
  },
  {
    title: 'Configuration',
    path: '_jib-configuration',
    icon: 'bi-gear',
    children: [{ title: 'Configuration', to: '/docs/jib/configuration' }]
  },
  {
    title: 'Build Setup',
    path: '_jib-setup',
    icon: 'bi-hammer',
    children: [{ title: 'Build Setup', to: '/docs/jib/setup' }]
  },
  {
    // Cross-link into the Hub deployment example that wires JIB into a cluster.
    title: 'Deployment',
    path: '_jib-deployment',
    icon: 'bi-cloud-upload',
    crossLink: true,
    children: [{ title: 'Deploying with the Hub', to: '/docs/hub/deployment/jeffrey-jib' }]
  }
];

export const intellijPluginNavigation: DocSection[] = [
  // Standalone product section for the IntelliJ Plugin. Overview is promoted to the root of the
  // sidebar (synthetic `_` path); the remaining pages follow as single entries.
  {
    title: 'Overview',
    path: '_intellij-plugin-overview',
    icon: 'bi-info-circle',
    children: [{ title: 'Overview', to: '/docs/intellij-plugin' }]
  },
  {
    title: 'Configuration',
    path: '_intellij-plugin-configuration',
    icon: 'bi-gear',
    children: [{ title: 'Configuration', to: '/docs/intellij-plugin/configuration' }]
  },
  {
    title: 'Setup',
    path: '_intellij-plugin-setup',
    icon: 'bi-download',
    children: [{ title: 'Setup', to: '/docs/intellij-plugin/setup' }]
  },
  {
    title: 'Java JFR Profiler Plugin',
    path: '_intellij-plugin-jfr-profiler',
    icon: 'bi-window-stack',
    children: [{ title: 'Java JFR Profiler Plugin', to: '/docs/intellij-plugin/jfr-profiler' }]
  }
];

export const tracingNavigation: DocSection[] = [
  // Standalone product section for Jeffrey Tracing. Single-page entries use synthetic
  // `_` paths with absolute `to:` children; multi-page groups render as collapsible sections.
  {
    title: 'Overview',
    path: '_tracing-overview',
    icon: 'bi-info-circle',
    children: [{ title: 'Overview', to: '/docs/tracing' }]
  },
  {
    title: 'Getting Started',
    path: '_tracing-getting-started',
    icon: 'bi-rocket-takeoff',
    children: [{ title: 'Getting Started', to: '/docs/tracing/getting-started' }]
  },
  {
    title: 'Core Concepts',
    path: '_tracing-concepts',
    icon: 'bi-box',
    children: [{ title: 'Core Concepts', to: '/docs/tracing/concepts' }]
  },
  {
    title: 'Instrumentation',
    path: '_tracing-instrumentation',
    icon: 'bi-code-square',
    children: [
      { title: 'Overview', to: '/docs/tracing/instrumentation' },
      {
        title: 'Tracer API Reference',
        path: 'tracer-api',
        children: [
          { title: 'run', to: '/docs/tracing/tracer-api/run' },
          { title: 'call', to: '/docs/tracing/tracer-api/call' },
          { title: 'current', to: '/docs/tracing/tracer-api/current' },
          { title: 'inSpanOf', to: '/docs/tracing/tracer-api/in-span-of' },
          { title: 'stamp', to: '/docs/tracing/tracer-api/stamp' },
          { title: 'openSpanOf', to: '/docs/tracing/tracer-api/open-span-of' },
          { title: 'reenter', to: '/docs/tracing/tracer-api/reenter' },
          { title: 'continueIn', to: '/docs/tracing/tracer-api/continue-in' },
          { title: 'fork', to: '/docs/tracing/tracer-api/fork' },
          { title: 'forkCallable', to: '/docs/tracing/tracer-api/fork-callable' },
          { title: 'propagating', to: '/docs/tracing/tracer-api/propagating' }
        ]
      },
      { title: '@Traced & the Agent', to: '/docs/tracing/traced-annotation' },
      { title: 'JFR Method Tracing', to: '/docs/tracing/method-tracing' },
      { title: 'HTTP Events', to: '/docs/tracing/http-events' },
      { title: 'gRPC Events', to: '/docs/tracing/grpc-events' },
      { title: 'JDBC Events', to: '/docs/tracing/jdbc-events' },
      { title: 'MyBatis Events', to: '/docs/tracing/mybatis-events' },
      { title: 'Custom Traced Events', to: '/docs/tracing/custom-events' },
      { title: 'Notifications & Exceptions', to: '/docs/tracing/notifications-exceptions' },
      { title: 'Spring Support', to: '/docs/tracing/spring-support' }
    ]
  },
  {
    title: 'JVM Correlation',
    path: '_tracing-jvm-correlation',
    icon: 'bi-cpu',
    children: [
      { title: 'JDK Events in Traces', to: '/docs/tracing/jdk-events' },
      { title: 'GC Pauses & Safepoints', to: '/docs/tracing/gc-safepoints' }
    ]
  },
  {
    title: 'Analyzing Traces',
    path: '_tracing-analysis',
    icon: 'bi-bar-chart',
    children: [{ title: 'Analyzing Traces', to: '/docs/tracing/analysis' }]
  },
  {
    title: 'Provisioner & Hub',
    path: '_tracing-provisioner-hub',
    icon: 'bi-hdd-network',
    children: [{ title: 'Provisioner & Hub', to: '/docs/tracing/provisioner-hub' }]
  },
  {
    title: 'Configuration & Testing',
    path: '_tracing-configuration',
    icon: 'bi-gear',
    children: [{ title: 'Configuration & Testing', to: '/docs/tracing/configuration' }]
  },
  {
    title: 'Jeffrey Events',
    path: '_tracing-events',
    icon: 'bi-activity',
    children: [{ title: 'Event catalog', to: '/docs/tracing/events' }]
  }
];

// Union — used by global helpers like getAllDocs/search and as a back-compat export.
export const docsNavigation: DocSection[] = [...microscopeNavigation, ...microscopeMcpNavigation, ...hubNavigation, ...provisionerNavigation, ...jibNavigation, ...intellijPluginNavigation, ...tracingNavigation];

export function getProductForPath(routePath: string): Product | null {
  const cleaned = routePath.replace(/^\/docs\/?/, '');
  if (!cleaned) return null;
  const first = cleaned.split('/')[0];
  if (MICROSCOPE_MCP_SEGMENTS.has(first)) return 'microscope-mcp';
  if (MICROSCOPE_SEGMENTS.has(first)) return 'microscope';
  if (HUB_SEGMENTS.has(first)) return 'hub';
  if (PROVISIONER_SEGMENTS.has(first)) return 'provisioner';
  if (JIB_SEGMENTS.has(first)) return 'jib';
  if (INTELLIJ_PLUGIN_SEGMENTS.has(first)) return 'intellij-plugin';
  if (TRACING_SEGMENTS.has(first)) return 'tracing';
  return null;
}

export function navigationForProduct(product: Product): DocSection[] {
  if (product === 'microscope-mcp') {
    return microscopeMcpNavigation;
  }
  if (product === 'hub') {
    return hubNavigation;
  }
  if (product === 'provisioner') {
    return provisionerNavigation;
  }
  if (product === 'jib') {
    return jibNavigation;
  }
  if (product === 'intellij-plugin') {
    return intellijPluginNavigation;
  }
  if (product === 'tracing') {
    return tracingNavigation;
  }
  return microscopeNavigation;
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

  // Strip query / hash from both sides — entries like `/docs/hub#architecture`
  // should still align with `/docs/hub` so the prev/next pair stays sensible.
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
