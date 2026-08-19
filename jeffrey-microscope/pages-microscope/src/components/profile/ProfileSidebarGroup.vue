<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<!--
  One sidebar entry, whichever of the two shapes it has: a plain link, or a parent that toggles a
  submenu open.

  Extracted so the Technologies rail and the mode sections render the same thing. They used to
  differ — only the mode sections handled `children`, so a submenu declared under a technology
  silently rendered as a link to nowhere, since a parent carries no `path` of its own.

  The expanded state stays with the sidebar rather than living here: it survives navigation between
  technologies, and the sidebar is what auto-expands the parent owning the active route.
-->
<template>
  <div v-if="item.children" class="nav-item-group">
    <div
      class="nav-item nav-item-parent"
      :class="{ active: active, expanded: expanded }"
      @click="emit('toggle')"
    >
      <i class="bi" :class="item.icon"></i>
      <span>{{ item.label }}</span>
      <i class="bi bi-chevron-right submenu-arrow" :class="{ rotated: expanded }"></i>
    </div>

    <div class="nav-submenu" :class="{ expanded: expanded }">
      <ProfileSidebarItem
        v-for="child in item.children"
        :key="child.label"
        :item="child"
        subitem
        :profile-id="profileId"
        :is-feature-disabled="isFeatureDisabled"
        :has-secondary-profile="hasSecondaryProfile"
        @navigate-differential="emit('navigate-differential', $event)"
      />
    </div>
  </div>

  <ProfileSidebarItem
    v-else
    :item="item"
    :profile-id="profileId"
    :is-feature-disabled="isFeatureDisabled"
    :has-secondary-profile="hasSecondaryProfile"
    @navigate-differential="emit('navigate-differential', $event)"
  />
</template>

<script setup lang="ts">
import ProfileSidebarItem from '@/components/profile/ProfileSidebarItem.vue';
import type { DifferentialType, ProfileNavItem } from '@/views/profiles/navigation/profileNavConfig';

defineProps<{
  item: ProfileNavItem;
  profileId: string;
  isFeatureDisabled: (key: string) => boolean;
  hasSecondaryProfile?: boolean;
  /** Whether this parent's submenu is open. Meaningless for a plain item. */
  expanded?: boolean;
  /** Whether the active route sits under this parent. Meaningless for a plain item. */
  active?: boolean;
}>();

const emit = defineEmits<{
  toggle: [];
  'navigate-differential': [type: DifferentialType];
}>();
</script>
