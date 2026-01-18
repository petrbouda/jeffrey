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
  to: string
  icon: string
  title: string
  description: string
  variant?: 'primary' | 'secondary'
}>()
</script>

<template>
  <router-link
    :to="to"
    class="docs-link-card"
    :class="{ 'variant-secondary': variant === 'secondary' }"
  >
    <div class="link-icon">
      <i :class="icon"></i>
    </div>
    <div class="link-content">
      <h4>{{ title }}</h4>
      <p>{{ description }}</p>
    </div>
    <i class="bi bi-arrow-right link-arrow"></i>
  </router-link>
</template>

<style scoped>
.docs-link-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem;
  margin: 1.5rem 0;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-left: 4px solid #5e64ff;
  border-radius: 8px;
  text-decoration: none !important;
  color: inherit;
  transition: all 0.2s ease;
}

.docs-link-card:hover {
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.08) 0%, rgba(124, 58, 237, 0.08) 100%);
  border-color: #5e64ff;
  transform: translateX(4px);
  text-decoration: none !important;
}

.docs-link-card.variant-secondary {
  border-left-color: #10b981;
}

.docs-link-card.variant-secondary:hover {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.08) 0%, rgba(5, 150, 105, 0.08) 100%);
  border-color: #10b981;
}

.link-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  border-radius: 10px;
}

.variant-secondary .link-icon {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.link-icon i {
  font-size: 1.25rem;
  color: #fff;
}

.link-content {
  flex: 1;
}

.link-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #343a40;
  text-decoration: none !important;
}

.link-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
  text-decoration: none !important;
}

.link-arrow {
  font-size: 1.25rem;
  color: #5e64ff;
  transition: transform 0.2s ease;
}

.variant-secondary .link-arrow {
  color: #10b981;
}

.docs-link-card:hover .link-arrow {
  transform: translateX(4px);
}
</style>
