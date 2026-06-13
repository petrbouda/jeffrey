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
    variant?: 'intro' | 'note' | 'tip';
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

.about-callout--tip .about-callout-title i {
  color: var(--color-info);
}
</style>
