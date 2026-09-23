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
defineProps<{
  icon: string
  title: string
  description?: string
  color?: 'purple' | 'green' | 'orange' | 'red' | 'blue' | 'neutral'
  size?: 'sm' | 'md' | 'lg'
  layout?: 'horizontal' | 'vertical'
}>()
</script>

<template>
  <div
    class="docs-card"
    :class="[
      layout === 'vertical' ? 'docs-card-vertical docs-card-subtle' : 'docs-card-horizontal docs-card-subtle'
    ]"
  >
    <div
      class="docs-icon"
      :class="[
        `docs-icon-${size || 'md'}`,
        `docs-icon-${color || 'purple'}`
      ]"
    >
      <i :class="icon"></i>
    </div>
    <div v-if="layout !== 'vertical'" class="docs-card-content">
      <h4>
        <slot name="title">{{ title }}</slot>
      </h4>
      <p v-if="description || $slots.default">
        <slot>{{ description }}</slot>
      </p>
    </div>
    <template v-else>
      <h4>
        <slot name="title">{{ title }}</slot>
      </h4>
      <p v-if="description || $slots.default">
        <slot>{{ description }}</slot>
      </p>
    </template>
  </div>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
