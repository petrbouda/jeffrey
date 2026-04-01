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
  <div class="empty-state">
    <div class="empty-state-content">
      <i class="bi" :class="icon"></i>
      <span class="empty-state-title">{{ title }}</span>
      <span v-if="description" class="empty-state-description">{{ description }}</span>
      <slot name="action"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  icon: string;
  title: string;
  description?: string;
}

defineProps<Props>();
</script>

<style scoped>
.empty-state {
  padding: 12px;
}

.empty-state-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 20px;
  gap: 6px;
}

.empty-state-content i {
  font-size: 2rem;
  color: var(--color-primary);
  opacity: 0.4;
  margin-bottom: 4px;
}

.empty-state-title {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--color-text-muted);
}

.empty-state-description {
  font-size: 0.78rem;
  color: var(--color-text-light);
}
</style>
