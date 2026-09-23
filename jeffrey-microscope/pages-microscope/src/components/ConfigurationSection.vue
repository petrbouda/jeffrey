<!--
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 -->

<template>
  <div class="config-section">
    <div class="section-header">
      <i class="bi" :class="icon"></i>
      <span class="section-title">{{ title }}</span>
    </div>
    <div class="config-items">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title: string;
  icon: string;
}

defineProps<Props>();
</script>

<style scoped>
.config-section {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
  overflow: hidden;
  transition:
    box-shadow var(--transition-base),
    transform var(--transition-base);
}

.config-section:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.section-header {
  padding: 0.85rem 1rem 0.6rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border-bottom: 2px solid var(--color-primary-light);
}

.section-header i {
  color: var(--color-primary);
  font-size: 0.95rem;
}

.section-title {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-dark);
}

.config-items {
  padding: 0.5rem 1rem 0.65rem;
  display: flex;
  flex-direction: column;
}

.config-items :deep(.config-item) {
  display: flex;
  align-items: baseline;
  padding: 0.4rem 0;
  gap: 0.5rem;
}

.config-items :deep(.config-item)::after {
  content: '';
  flex: 1;
  border-bottom: 1.5px dotted var(--color-border);
  min-width: 20px;
  margin-bottom: 3px;
  order: 1;
}

.config-items :deep(.config-label) {
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-text-muted);
  white-space: nowrap;
  order: 0;
}

.config-items :deep(.config-value) {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-dark);
  white-space: nowrap;
  text-align: right;
  order: 2;
}

.config-items :deep(.config-value i.bi) {
  vertical-align: baseline;
  position: relative;
  top: -1px;
  margin-right: 0.25rem;
}
</style>
