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
    <i class="bi" :class="icon"></i>
    <h5>{{ title }}</h5>
    <p v-if="description">{{ description }}</p>
    <slot name="action"></slot>
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
  text-align: center;
  padding: 3rem 1rem;
}

.empty-state i {
  font-size: 3rem;
  color: var(--color-text-muted, #9ca3af);
  margin-bottom: 1rem;
  display: block;
}

.empty-state h5 {
  color: var(--color-text, #374151);
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.empty-state p {
  color: var(--color-text-muted, #6b7280);
  margin-bottom: 0;
}
</style>
