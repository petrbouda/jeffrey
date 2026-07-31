<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
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

<template>
  <div class="delta-table">
    <div class="delta-title" :class="tone">
      <i :class="tone === 'bad' ? 'bi bi-arrow-up-right' : 'bi bi-arrow-down-right'"></i>
      <span>{{ title }}</span>
      <span class="count">{{ deltas.length }}</span>
    </div>

    <EmptyState v-if="!deltas.length" icon="bi-dash" title="None" description="" />

    <table v-else class="table table-sm table-hover mb-0">
      <thead>
        <tr>
          <th>Frame</th>
          <th class="text-end">Baseline</th>
          <th class="text-end">Current</th>
          <th class="text-end">Change</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="delta in deltas" :key="delta.frame">
          <td class="frame-cell">
            <code>{{ delta.frame }}</code>
            <Badge v-if="delta.isNew" value="new" variant="warning" size="xxs" :uppercase="false" />
          </td>
          <td class="text-end">{{ delta.baselineSelfPct.toFixed(1) }}%</td>
          <td class="text-end">{{ delta.currentSelfPct.toFixed(1) }}%</td>
          <td class="text-end" :class="tone">
            {{ delta.deltaPp > 0 ? '+' : '' }}{{ delta.deltaPp.toFixed(1) }} pp
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import EmptyState from '@shared/components/EmptyState.vue';
import Badge from '@shared/components/Badge.vue';
import type { FrameDelta } from '@/services/api/model/Advisor';

defineProps<{
  title: string;
  deltas: FrameDelta[];
  tone: 'bad' | 'good';
}>();
</script>

<style scoped>
.delta-table {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.delta-title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 0.75rem;
  font-size: 0.8rem;
  font-weight: 600;
  border-bottom: 1px solid var(--color-border-light);
}

.delta-title.bad {
  color: var(--color-danger);
  background: color-mix(in srgb, var(--color-danger) 6%, var(--color-white));
}

.delta-title.good {
  color: var(--color-success);
  background: color-mix(in srgb, var(--color-success) 6%, var(--color-white));
}

.delta-title .count {
  margin-left: auto;
  color: var(--color-text-muted);
  font-weight: 400;
}

.frame-cell {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  max-width: 0;
}

.frame-cell code {
  font-family: var(--font-family-monospace);
  font-size: 0.75rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

td.bad {
  color: var(--color-danger);
}

td.good {
  color: var(--color-success);
}
</style>
