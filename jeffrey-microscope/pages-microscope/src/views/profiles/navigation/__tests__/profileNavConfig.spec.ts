import { describe, expect, it } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import { profileChildRoutes } from '@/router/profileChildRoutes';
import {
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
    // 38 JVM (incl. GC parent + 5 GC children) + 4 Visualization + 13 HeapDump + 3 Tools
    // + 34 Technologies
    expect(allItems.length).toBe(92);
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
});
