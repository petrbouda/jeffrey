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
  Rounded callout box used inside an AboutPanel. `intro` is a neutral lead-in paragraph, `note` a
  bordered aside, `tip` an accented takeaway. Optional title (with optional icon) above the slot.
-->
<template>
  <div class="about-callout" :class="`about-callout--${variant}`">
    <div v-if="title" class="about-callout-title">
      <i v-if="icon" class="bi" :class="icon"></i>
      <span>{{ title }}</span>
    </div>
    <div class="about-callout-body">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'intro' | 'note' | 'tip' | 'warning';
    title?: string;
    icon?: string;
  }>(),
  {
    variant: 'intro',
    title: undefined,
    icon: undefined
  }
);
</script>

<style scoped>
.about-callout {
  border-radius: var(--radius-md);
  padding: 1rem 1.25rem;
  margin-bottom: 1.5rem;
  font-size: 0.9rem;
  line-height: 1.6;
  color: var(--color-text);
}

.about-callout-body :deep(p) {
  margin: 0;
}

.about-callout-body :deep(p + p) {
  margin-top: 0.75rem;
}

.about-callout-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  color: var(--color-dark);
  margin-bottom: 0.5rem;
}

.about-callout--intro {
  background: var(--color-light);
}

.about-callout--note {
  background: var(--color-light);
  border: 1px solid var(--color-border);
}

.about-callout--tip {
  background: var(--color-info-light);
  border-left: 4px solid var(--color-info);
}

.about-callout--warning {
  background: var(--color-warning-light);
  border-left: 4px solid var(--color-warning);
}

.about-callout--warning .about-callout-title i {
  color: var(--color-warning);
}

.about-callout--tip .about-callout-title i {
  color: var(--color-info);
}
</style>
