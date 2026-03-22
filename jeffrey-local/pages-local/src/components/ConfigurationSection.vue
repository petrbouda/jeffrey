<!--
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 -->

<template>
  <div class="config-section">
    <h5 class="section-title">
      <i class="bi me-2" :class="icon"></i>
      {{ title }}
    </h5>
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
  background: #fafbfc;
  border-radius: 6px;
  padding: 1.25rem;
  border: 1px solid #e9ecef;
  transition: background-color 0.2s ease;
}

.config-section:hover {
  background: #f8f9fa;
}

.section-title {
  color: #6c757d;
  font-weight: 500;
  font-size: 0.95rem;
  margin-bottom: 1rem;
  display: flex;
  align-items: center;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.config-items {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.config-items :deep(.config-item) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
  border-bottom: 1px solid #eaecef;
}

.config-items :deep(.config-item:last-child) {
  border-bottom: none;
}

.config-items :deep(.config-label) {
  font-weight: 500;
  color: #6c757d;
  font-size: 0.875rem;
  flex: 1;
}

.config-items :deep(.config-value) {
  font-weight: 600;
  color: #212529;
  font-size: 0.875rem;
  text-align: right;
  flex: 1;
}

.config-items :deep(.config-value i.bi) {
  vertical-align: baseline;
  position: relative;
  top: -1px;
  margin-right: 0.25rem;
}
</style>
