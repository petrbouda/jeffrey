<template>
  <div class="detail-sidebar" :class="{ collapsed }">
    <div class="sidebar" :class="{ collapsed }">
      <div class="edge-toggle" @click="emit('toggle-collapse')">
        <div class="edge-toggle-line">
          <i class="bi" :class="collapsed ? 'bi-chevron-right' : 'bi-chevron-left'"></i>
        </div>
      </div>

      <div class="scrollbar" style="height: 100%">
        <!-- Profile Header spacer (hidden for Technologies drilled-in state) -->
        <div v-if="!(mode === 'Technologies' && activeTechnology)" class="p-2" />

        <div v-if="!collapsed" class="sidebar-menu">
          <!-- Technologies mode: back link + per-technology sub-pages -->
          <template v-if="mode === 'Technologies'">
            <template v-if="activeTechnology && technology">
              <div class="tech-sidebar-header">
                <a class="tech-back-link" @click.prevent="emit('navigate-technologies-hub')">
                  <i class="bi bi-arrow-left"></i>
                </a>
                <div class="tech-header-title">
                  <i class="bi" :class="technology.icon"></i>
                  {{ technology.name }}
                </div>
              </div>

              <div class="nav-section">
                <div class="nav-items">
                  <template v-for="(group, groupIndex) in technology.groups" :key="groupIndex">
                    <div v-if="group.title" class="nav-section-title">{{ group.title }}</div>
                    <ProfileSidebarItem
                      v-for="navItem in group.items"
                      :key="navItem.label"
                      :item="navItem"
                      :profile-id="profileId"
                      :is-feature-disabled="isFeatureDisabled"
                      :has-secondary-profile="hasSecondaryProfile"
                      @navigate-differential="onNavigateDifferential"
                    />
                  </template>
                </div>
              </div>
            </template>
          </template>

          <!-- All other modes: section list from the nav config -->
          <template v-else>
            <div v-for="section in sections" :key="section.title" class="nav-section">
              <div
                class="nav-section-title nav-section-toggle"
                @click="toggleSection(section.title)"
              >
                <span>{{ section.title }}</span>
                <i
                  class="bi bi-chevron-down section-arrow"
                  :class="{ collapsed: isSectionCollapsed(section.title) }"
                ></i>
              </div>
              <div v-show="!isSectionCollapsed(section.title)" class="nav-items">
                <template v-for="navItem in section.items" :key="navItem.label">
                  <!-- Item with submenu (e.g. Garbage Collection) -->
                  <div v-if="navItem.children" class="nav-item-group">
                    <div
                      class="nav-item nav-item-parent"
                      :class="{
                        active: isSubmenuActive(navItem),
                        expanded: isSubmenuExpanded(navItem)
                      }"
                      @click="toggleSubmenu(navItem)"
                    >
                      <i class="bi" :class="navItem.icon"></i>
                      <span>{{ navItem.label }}</span>
                      <i
                        class="bi bi-chevron-right submenu-arrow"
                        :class="{ rotated: isSubmenuExpanded(navItem) }"
                      ></i>
                    </div>
                    <div class="nav-submenu" :class="{ expanded: isSubmenuExpanded(navItem) }">
                      <ProfileSidebarItem
                        v-for="child in navItem.children"
                        :key="child.label"
                        :item="child"
                        subitem
                        :profile-id="profileId"
                        :is-feature-disabled="isFeatureDisabled"
                        :has-secondary-profile="hasSecondaryProfile"
                        @navigate-differential="onNavigateDifferential"
                      />
                    </div>
                  </div>

                  <!-- Plain item -->
                  <ProfileSidebarItem
                    v-else
                    :item="navItem"
                    :profile-id="profileId"
                    :is-feature-disabled="isFeatureDisabled"
                    :has-secondary-profile="hasSecondaryProfile"
                    @navigate-differential="onNavigateDifferential"
                  />
                </template>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import ProfileSidebarItem from '@/components/profile/ProfileSidebarItem.vue';
import {
  DifferentialType,
  ProfileMode,
  ProfileNavItem,
  ProfileNavSection,
  profileNavSections,
  technologiesNav,
  TechnologyNav
} from '@/views/profiles/navigation/profileNavConfig';

const props = defineProps<{
  profileId: string;
  mode: ProfileMode;
  collapsed: boolean;
  isFeatureDisabled: (key: string) => boolean;
  hasSecondaryProfile: boolean;
  activeTechnology: string | null;
}>();

const emit = defineEmits<{
  (e: 'toggle-collapse'): void;
  (e: 'navigate-differential', type: DifferentialType): void;
  (e: 'navigate-technologies-hub'): void;
}>();

const route = useRoute();

// The SUBSECOND section is hidden entirely when the backend reports the subsecond feature as
// disabled (e.g. pprof profiles, which are aggregated and carry no per-sample timestamps, so the
// subsecond/timeseries views collapse into a single spike).
const SUBSECOND_SECTION_TITLE = 'SUBSECOND';
const SUBSECOND_FEATURE_KEY = 'subsecond';

const sections = computed<ProfileNavSection[]>(() => {
  if (props.mode === 'Technologies') {
    return [];
  }
  const modeSections = profileNavSections[props.mode];
  if (props.isFeatureDisabled(SUBSECOND_FEATURE_KEY)) {
    return modeSections.filter(section => section.title !== SUBSECOND_SECTION_TITLE);
  }
  return modeSections;
});

const technology = computed<TechnologyNav | null>(() => {
  if (!props.activeTechnology) {
    return null;
  }
  return technologiesNav[props.activeTechnology] ?? null;
});

// Submenu expand/collapse state (e.g. Garbage Collection), keyed by item label.
const submenuParents: ProfileNavItem[] = Object.values(profileNavSections)
  .flat()
  .flatMap(section => section.items)
  .filter(navItem => navItem.children !== undefined);

const expandedSubmenus = ref<Set<string>>(new Set());

const isSubmenuExpanded = (navItem: ProfileNavItem): boolean => {
  return expandedSubmenus.value.has(navItem.label);
};

const toggleSubmenu = (navItem: ProfileNavItem): void => {
  if (expandedSubmenus.value.has(navItem.label)) {
    expandedSubmenus.value.delete(navItem.label);
  } else {
    expandedSubmenus.value.add(navItem.label);
  }
};

const isSubmenuActive = (navItem: ProfileNavItem): boolean => {
  if (!navItem.activePathIncludes) {
    return false;
  }
  return route.path.includes(navItem.activePathIncludes);
};

// Collapsible sections: collapsed state is keyed by `${mode}:${title}` (default: all
// expanded) and persisted so the preference survives reloads.
const COLLAPSED_SECTIONS_STORAGE_KEY = 'jeffrey.profile-sidebar.collapsed-sections';

function loadCollapsedSections(): Set<string> {
  try {
    const stored = localStorage.getItem(COLLAPSED_SECTIONS_STORAGE_KEY);
    return stored ? new Set(JSON.parse(stored)) : new Set();
  } catch {
    return new Set();
  }
}

const collapsedSections = ref<Set<string>>(loadCollapsedSections());

const sectionKey = (title: string): string => {
  return `${props.mode}:${title}`;
};

const isSectionCollapsed = (title: string): boolean => {
  return collapsedSections.value.has(sectionKey(title));
};

const persistCollapsedSections = (): void => {
  localStorage.setItem(
    COLLAPSED_SECTIONS_STORAGE_KEY,
    JSON.stringify([...collapsedSections.value])
  );
};

const toggleSection = (title: string): void => {
  const key = sectionKey(title);
  if (collapsedSections.value.has(key)) {
    collapsedSections.value.delete(key);
  } else {
    collapsedSections.value.add(key);
  }
  persistCollapsedSections();
};

// True when the section owns the current route (direct item path, prefix, an
// `activePathIncludes` matcher, or any submenu child).
const sectionContainsRoute = (section: ProfileNavSection, path: string): boolean => {
  const matchesItem = (navItem: ProfileNavItem): boolean => {
    if (navItem.activePathIncludes && path.includes(navItem.activePathIncludes)) {
      return true;
    }
    if (navItem.path) {
      const resolved = navItem.path(props.profileId).split('?')[0];
      if (path === resolved || path.startsWith(`${resolved}/`)) {
        return true;
      }
    }
    return (navItem.children ?? []).some(matchesItem);
  };
  return section.items.some(matchesItem);
};

// Auto-expand submenus and the section owning the active route (the user can still
// collapse other sections freely; only the active one is forced open).
watch(
  [() => route.path, () => props.mode],
  ([newPath]) => {
    for (const parent of submenuParents) {
      if (parent.activePathIncludes && newPath.includes(parent.activePathIncludes)) {
        expandedSubmenus.value.add(parent.label);
      }
    }
    for (const section of sections.value) {
      if (sectionContainsRoute(section, newPath)) {
        const key = sectionKey(section.title);
        if (collapsedSections.value.has(key)) {
          collapsedSections.value.delete(key);
          persistCollapsedSections();
        }
      }
    }
  },
  { immediate: true }
);

const onNavigateDifferential = (type: DifferentialType): void => {
  emit('navigate-differential', type);
};
</script>

<style scoped>
/* Technology sidebar header */
.tech-sidebar-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-3) var(--spacing-4);
  margin-bottom: var(--spacing-2);
}

.tech-back-link {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  color: var(--color-primary);
  cursor: pointer;
  transition: background var(--transition-fast);
  flex-shrink: 0;
}

.tech-back-link:hover {
  background: var(--color-primary-light);
}

.tech-header-title {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-base);
  color: var(--color-dark);
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.tech-header-title i {
  color: var(--color-primary);
  font-size: var(--font-size-md);
}
</style>
