<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
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
import type {
  DifferentialType,
  ProfileNavItem
} from '@/views/profiles/navigation/profileNavConfig';

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
