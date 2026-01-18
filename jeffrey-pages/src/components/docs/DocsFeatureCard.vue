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
