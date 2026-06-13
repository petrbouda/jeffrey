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
  A card with a colored icon tile, a title and a slotted description. Used inside FeatureGrid to
  explain the facets of a feature. `variant` picks the icon-tile colour from the design tokens.
-->
<template>
  <div class="feature-card">
    <div class="feature-icon" :class="`feature-icon--${variant}`">
      <i class="bi" :class="icon"></i>
    </div>
    <div class="feature-content">
      <h6>{{ title }}</h6>
      <p><slot></slot></p>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    icon: string;
    title: string;
    variant?: 'primary' | 'info' | 'success' | 'warning' | 'danger' | 'neutral' | 'purple' | 'teal';
  }>(),
  {
    variant: 'primary'
  }
);
</script>

<style scoped>
.feature-card {
  display: flex;
  gap: 0.875rem;
  padding: 1rem;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition:
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.feature-card:hover {
  box-shadow: var(--shadow-sm);
}

.feature-icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-white);
  font-size: 1.1rem;
}

.feature-icon--primary {
  background: var(--color-primary);
}

.feature-icon--info {
  background: var(--color-info);
}

.feature-icon--success {
  background: var(--color-success);
}

.feature-icon--warning {
  background: var(--color-warning);
}

.feature-icon--danger {
  background: var(--color-danger);
}

.feature-icon--neutral {
  background: var(--color-grey-muted);
}

.feature-icon--purple {
  background: var(--color-purple);
}

.feature-icon--teal {
  background: var(--color-teal);
}

.feature-content h6 {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--color-dark);
  margin-bottom: 0.25rem;
}

.feature-content p {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  margin-bottom: 0;
  line-height: 1.5;
}
</style>
