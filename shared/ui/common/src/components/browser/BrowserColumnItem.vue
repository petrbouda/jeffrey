<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
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

<!--
  One entry of a BrowserColumn. `state` says where it stands on the browser's path: `path` for a
  selected entry whose children are open in the next column, `active` for the entry the path ends
  on. The `badge` slot sits before the chevron; the `trailing` slot replaces the chevron, e.g. with
  the selected entry's actions, and its clicks do not select the entry.
-->

<template>
  <div
    class="browser-item"
    :class="state !== 'none' ? `browser-item-${state}` : undefined"
    role="button"
    tabindex="0"
    :aria-current="state === 'none' ? undefined : 'true'"
    :title="tooltip"
    @click="$emit('select')"
    @keydown.enter.prevent="$emit('select')"
    @keydown.space.prevent="$emit('select')"
  >
    <i :class="['bi', icon, 'browser-item-icon']"></i>
    <div class="browser-item-text">
      <div class="browser-item-title" :class="{ 'browser-item-mono': mono }">
        <slot name="title">{{ title }}</slot>
      </div>
      <div v-if="subtitle" class="browser-item-subtitle">{{ subtitle }}</div>
    </div>
    <div v-if="$slots.badge" class="browser-item-badge">
      <slot name="badge"></slot>
    </div>
    <div v-if="$slots.trailing" class="browser-item-trailing" @click.stop @keydown.stop>
      <slot name="trailing"></slot>
    </div>
    <i v-else class="bi bi-chevron-right browser-item-chevron"></i>
  </div>
</template>

<script setup lang="ts">
import type { BrowserItemState } from '@shared/types/ui';

withDefaults(
  defineProps<{
    /** A Bootstrap icon class, e.g. `bi-folder`. */
    icon: string;
    /** The entry's name; the `title` slot overrides it. */
    title?: string;
    subtitle?: string;
    /** Sets the title in the monospace face, e.g. for pod names and timestamps. */
    mono?: boolean;
    state?: BrowserItemState;
    tooltip?: string;
  }>(),
  {
    title: '',
    subtitle: undefined,
    mono: false,
    state: 'none',
    tooltip: undefined
  }
);

defineEmits<{
  (e: 'select'): void;
}>();
</script>

<style scoped>
.browser-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: 7px var(--spacing-2);
  border: 1px solid transparent;
  border-radius: var(--radius-base);
  color: var(--color-heading-dark);
  cursor: pointer;
  user-select: none;
  transition: background var(--transition-base);
}

.browser-item:hover {
  background: var(--color-light);
}

.browser-item:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.browser-item-path {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.browser-item-path:hover {
  background: var(--color-primary-light);
}

.browser-item-active {
  background: var(--color-primary);
  color: var(--color-white);
}

.browser-item-active:hover {
  background: var(--color-primary-hover);
}

.browser-item-icon {
  flex: none;
  font-size: 0.85rem;
}

.browser-item-text {
  flex: 1;
  min-width: 0;
}

.browser-item-title {
  font-size: var(--font-size-base);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.browser-item-mono {
  font-family: var(--font-family-monospace);
  font-size: 0.75rem;
}

.browser-item-subtitle {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.browser-item-path .browser-item-subtitle {
  color: var(--color-primary);
  opacity: 0.8;
}

.browser-item-active .browser-item-subtitle {
  color: var(--color-white);
  opacity: 0.85;
}

.browser-item-chevron {
  flex: none;
  font-size: 0.7rem;
  color: var(--color-text-light);
}

.browser-item-path .browser-item-chevron,
.browser-item-active .browser-item-chevron {
  color: inherit;
}

.browser-item-badge {
  flex: none;
  display: flex;
  align-items: center;
}

.browser-item-trailing {
  flex: none;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: default;
}
</style>
