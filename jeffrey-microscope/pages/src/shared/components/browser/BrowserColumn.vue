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
  One column of a column browser (Finder-style): a titled, independently scrolling list of
  BrowserColumnItems, with an optional filter field and a footer. A fixed `width`, or `grow` to
  take the space the other columns leave.

  Usage:
    <BrowserColumn title="Projects" :count="projects.length" width="16rem"
                   v-model:filter="query" filter-placeholder="Filter projects…">
      <BrowserColumnItem v-for="p in shown" … />
    </BrowserColumn>
-->

<template>
  <section
    class="browser-column"
    :class="{ 'browser-column-grow': grow }"
    :style="grow ? undefined : { width }"
    :aria-label="title"
  >
    <header class="browser-column-head">
      <span class="browser-column-title">{{ title }}</span>
      <Badge
        v-if="count !== undefined"
        :value="count"
        variant="grey"
        size="xxs"
        :uppercase="false"
        class="browser-column-count"
      />
      <slot name="actions"></slot>
    </header>

    <div v-if="filterPlaceholder" class="browser-column-filter">
      <i class="bi bi-search"></i>
      <input
        v-model="filter"
        type="text"
        :placeholder="filterPlaceholder"
        :aria-label="filterPlaceholder"
      />
    </div>

    <div class="browser-column-body">
      <slot></slot>
    </div>

    <footer v-if="$slots.footer" class="browser-column-foot">
      <slot name="footer"></slot>
    </footer>
  </section>
</template>

<script setup lang="ts">
import Badge from '@shared/components/Badge.vue';

withDefaults(
  defineProps<{
    title: string;
    /** Shown beside the title; omitted while there is nothing to count. */
    count?: number;
    /** A CSS length; ignored when the column grows. */
    width?: string;
    /** Takes the width the other columns leave. */
    grow?: boolean;
    /** Shows the filter field with this placeholder. */
    filterPlaceholder?: string;
  }>(),
  {
    count: undefined,
    width: '16rem',
    grow: false,
    filterPlaceholder: undefined
  }
);

const filter = defineModel<string>('filter', { default: '' });
</script>

<style scoped>
.browser-column {
  display: flex;
  flex-direction: column;
  flex: none;
  min-height: 0;
  border-right: 1px solid var(--color-border);
}

.browser-column-grow {
  flex: 1;
  min-width: 0;
  border-right: none;
}

.browser-column-head {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  min-height: 38px;
  padding: var(--spacing-2) var(--spacing-3);
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

.browser-column-title {
  font-size: 10px;
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.browser-column-count {
  margin-left: auto;
}

.browser-column-filter {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: var(--spacing-2) var(--spacing-2) 0;
  padding: 4px var(--spacing-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
}

.browser-column-filter input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.browser-column-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--spacing-2);
}

.browser-column-foot {
  border-top: 1px solid var(--color-border);
}
</style>
