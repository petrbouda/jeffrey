<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
-->

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { getBreadcrumbs, type BreadcrumbItem } from '@/composables/useDocsNavigation';

const props = defineProps<{
  title: string
  icon: string
  breadcrumbs?: BreadcrumbItem[]
}>()

const route = useRoute();

const effectiveBreadcrumbs = computed(() => {
  if (props.breadcrumbs && props.breadcrumbs.length > 0) {
    return props.breadcrumbs;
  }
  return getBreadcrumbs(route.path);
});
</script>

<template>
  <nav class="docs-breadcrumb">
    <router-link to="/docs" class="breadcrumb-item">
      <i class="bi bi-book me-1"></i>Docs
    </router-link>
    <template v-for="(item, index) in effectiveBreadcrumbs" :key="index">
      <span class="breadcrumb-separator">/</span>
      <router-link
        v-if="item.to"
        :to="item.to"
        class="breadcrumb-item"
      >
        {{ item.label }}
      </router-link>
      <span
        v-else
        class="breadcrumb-item"
        :class="{ active: index === effectiveBreadcrumbs.length - 1 }"
      >
        {{ item.label }}
      </span>
    </template>
  </nav>

  <header class="docs-header">
    <div class="header-icon">
      <i :class="icon"></i>
    </div>
    <div class="header-content">
      <h1 class="docs-title">{{ title }}</h1>
    </div>
  </header>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
