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

<template>
  <div class="slowest-count-header">
    <div class="count-stats">
      <template v-if="shown < effectiveTotal">
        <Badge key-label="Showing" :value="shown" variant="primary" size="m" icon="bi bi-eye-fill" />
        <Badge
          key-label="Total"
          :value="effectiveTotal"
          variant="light"
          size="m"
          icon="bi bi-collection"
          class="total-badge"
        />
      </template>
      <Badge
        v-else
        key-label="Showing all"
        :value="effectiveTotal"
        variant="primary"
        size="m"
        icon="bi bi-check2-circle"
      />
    </div>
    <span class="count-note"><i class="bi bi-sort-down"></i>{{ note }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Badge from '@shared/components/Badge.vue';

const props = withDefaults(
  defineProps<{
    shown: number;
    total: number;
    note?: string;
  }>(),
  {
    note: 'sorted by duration'
  }
);

// Guard against a total that is somehow smaller than what is displayed, so the
// header never reads "Showing 20 of 18".
const effectiveTotal = computed(() => Math.max(props.total, props.shown));
</script>

<style scoped>
.slowest-count-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.625rem 1rem;
  border-bottom: 1px solid var(--color-border-light);
}

.count-stats {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* The neutral "Total" badge ships with an almost-invisible border; give it a clearly visible one. */
:deep(.total-badge) {
  border-color: var(--color-grey-border) !important;
}

.count-note {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.count-note i {
  font-size: 0.9em;
  color: var(--color-primary);
}
</style>
