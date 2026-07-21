import { describe, expect, it } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import { profileChildRoutes } from '@/router/profileChildRoutes';
import {
  getModeForPath,
  ProfileNavItem,
  profileNavSections,
  technologiesNav
} from '@/views/profiles/navigation/profileNavConfig';

const SAMPLE_PROFILE_ID = 'test-profile-id';

interface CollectedItem {
  origin: string;
  item: ProfileNavItem;
}

function collectAllItems(): CollectedItem[] {
  const collected: CollectedItem[] = [];

  for (const [mode, sections] of Object.entries(profileNavSections)) {
    for (const section of sections) {
      for (const item of section.items) {
        collected.push({ origin: `${mode} / ${section.title}`, item });
        if (item.children) {
          for (const child of item.children) {
            collected.push({ origin: `${mode} / ${section.title} / ${item.label}`, item: child });
          }
        }
      }
    }
  }

  for (const tech of Object.values(technologiesNav)) {
    for (const group of tech.groups) {
      for (const item of group.items) {
        collected.push({ origin: `Technologies / ${tech.key}`, item });
      }
    }
  }

  return collected;
}

// Minimal router mirroring the app's profile subtree (memory history keeps it node-friendly).
function buildRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/profiles/:profileId',
        children: [
          {
            path: '',
            children: profileChildRoutes
          }
        ]
      }
    ]
  });
}

describe('profileNavConfig', () => {
  const router = buildRouter();
  const allItems = collectAllItems();

  it('collects a sane number of nav items', () => {
    // 7 Overview (incl. Dashboards) + 19 JVM (incl. GC/JIT submenu parents + children)
    // + 16 Application (incl. Memory Issues submenu) + 4 Visualization + 13 HeapDump
    // + 4 Tools + 34 Technologies
    expect(allItems.length).toBe(97);
  });

  it('every item has a label and a bootstrap icon', () => {
    for (const { origin, item } of allItems) {
      expect(item.label, origin).toBeTruthy();
      expect(item.icon, `${origin} / ${item.label}`).toMatch(/^bi-/);
    }
  });

  it('every item path resolves to a defined route', () => {
    for (const { origin, item } of allItems) {
      if (!item.path) {
        continue;
      }
      const fullPath = item.path(SAMPLE_PROFILE_ID);
      const pathWithoutQuery = fullPath.split('?')[0];
      const resolved = router.resolve(pathWithoutQuery);
      const matchedLeaf = resolved.matched[resolved.matched.length - 1];

      expect(
        resolved.matched.length,
        `${origin} / ${item.label}: ${pathWithoutQuery} did not match any route`
      ).toBeGreaterThan(0);
      expect(
        matchedLeaf?.name,
        `${origin} / ${item.label}: ${pathWithoutQuery} matched no named leaf route`
      ).toBeTruthy();
    }
  });

  it('submenu parents carry children and an active-path matcher instead of a path', () => {
    const parents = Object.values(profileNavSections)
      .flat()
      .flatMap(section => section.items)
      .filter(item => item.children !== undefined);

    expect(parents.length).toBeGreaterThan(0);
    for (const parent of parents) {
      expect(parent.path, parent.label).toBeUndefined();
      expect(parent.activePathIncludes, parent.label).toBeTruthy();
      expect(parent.children?.length, parent.label).toBeGreaterThan(0);
    }
  });

  // Pages that moved under the Memory Issues submenu: old sub-path -> new sub-path
  const LEGACY_REDIRECTS: Array<[string, string]> = [
    ['leak-candidates', 'memory-issues/leak-candidates'],
    ['garbage-collection/finalizers', 'memory-issues/finalizers'],
    ['garbage-collection/reference-processing', 'memory-issues/reference-processing']
  ];

  it('legacy paths redirect to their new location', () => {
    for (const [oldSubPath, newSubPath] of LEGACY_REDIRECTS) {
      const record = profileChildRoutes.find(route => route.path === oldSubPath);
      expect(record, oldSubPath).toBeDefined();

      const redirect = (
        record as unknown as {
          redirect: (to: { params: { profileId: string } }) => string;
        }
      ).redirect;
      expect(redirect, `${oldSubPath} must be a redirect record`).toBeTypeOf('function');

      const target = redirect({ params: { profileId: SAMPLE_PROFILE_ID } });
      expect(target, oldSubPath).toBe(`/profiles/${SAMPLE_PROFILE_ID}/${newSubPath}`);
    }
  });

  it('derives the mode pill from a route path', () => {
    const profilePath = (subPath: string) => `/profiles/${SAMPLE_PROFILE_ID}${subPath}`;

    expect(getModeForPath(profilePath('/overview'))).toBe('Overview');
    expect(getModeForPath(profilePath('/ai-analysis'))).toBe('Overview');
    expect(getModeForPath(profilePath('/event-types'))).toBe('Overview');
    expect(getModeForPath(profilePath('/events'))).toBe('Overview');
    expect(getModeForPath(profilePath('/thread-statistics'))).toBe('Application');
    expect(getModeForPath(profilePath('/file-io'))).toBe('Application');
    expect(getModeForPath(profilePath('/socket-io'))).toBe('Application');
    expect(getModeForPath(profilePath('/security'))).toBe('Application');
    expect(getModeForPath(profilePath('/blocking-operations'))).toBe('Application');
    expect(getModeForPath(profilePath('/allocations'))).toBe('Application');
    expect(getModeForPath(profilePath('/memory-issues/leak-candidates'))).toBe('Application');
    expect(getModeForPath(profilePath('/container/configuration'))).toBe('Application');
    expect(getModeForPath(profilePath('/container/cpu-throttling'))).toBe('Application');
    expect(getModeForPath(profilePath('/garbage-collection'))).toBe('JVM');
    expect(getModeForPath(profilePath('/string-symbol-tables'))).toBe('JVM');
    expect(getModeForPath(profilePath('/technologies/hub'))).toBe('Technologies');
    expect(getModeForPath(profilePath('/flamegraphs/primary'))).toBe('Visualization');
    expect(getModeForPath(profilePath('/subsecond/primary'))).toBe('Visualization');
    expect(getModeForPath(profilePath('/heap-dump/settings'))).toBe('HeapDump');
    expect(getModeForPath(profilePath('/tools/rename-frames'))).toBe('Tools');
  });
});
