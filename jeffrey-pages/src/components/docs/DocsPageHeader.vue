<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { getBreadcrumbs } from '@/composables/useDocsNavigation';

defineProps<{
  title: string
  icon: string
}>()

const route = useRoute();

const breadcrumbs = computed(() => getBreadcrumbs(route.path));
</script>

<template>
  <nav class="docs-breadcrumb">
    <router-link to="/docs" class="breadcrumb-item">
      <i class="bi bi-book me-1"></i>Docs
    </router-link>
    <template v-for="(item, index) in breadcrumbs" :key="index">
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
        :class="{ active: index === breadcrumbs.length - 1 }"
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
