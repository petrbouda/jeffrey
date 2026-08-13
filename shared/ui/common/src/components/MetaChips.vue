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
  <div class="meta-chips">
    <span
      v-for="(chip, index) in chips"
      :key="index"
      class="meta-chip"
      :class="'meta-chip-' + (chip.tone ?? 'default')"
    >
      <i v-if="chip.icon" class="bi" :class="'bi-' + chip.icon"></i>
      {{ chip.text }}
      <span v-if="chip.marker" class="meta-chip-marker">{{ chip.marker }}</span>
    </span>
  </div>
</template>

<script setup lang="ts">
/**
 * A row of small read-only facts above a detail view — counts, a duration, a thread, an id.
 *
 * Chips, not a table: these are the handful of numbers that orient the reader before they look at
 * anything else, and they read as a sentence rather than as rows to scan. Anything the reader has
 * to compare against a sibling belongs in a table instead.
 */
export interface MetaChip {
  /** Bootstrap icon name without the `bi-` prefix (e.g. `clock`). */
  icon?: string;
  text: string;
  /**
   * `strong` for the fact the view is about, `danger` for one that needs attention. Left at
   * `default`, a chip is quiet on purpose — a row where everything is emphasised emphasises nothing.
   */
  tone?: 'default' | 'strong' | 'danger';
  /**
   * A short qualifier on the thing the chip names, rendered inside it — `virtual` on a thread, say.
   * Separate from `text` because it qualifies rather than identifies: it is styled apart so the eye
   * still reads the name first.
   */
  marker?: string;
}

defineProps<{
  chips: MetaChip[];
}>();
</script>

<style scoped>
.meta-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--color-text-muted);
  background: var(--color-light);
  border-radius: var(--radius-sm);
  padding: 0.25rem 0.55rem;
}

.meta-chip-strong {
  color: var(--color-primary);
  font-weight: 700;
}

.meta-chip-marker {
  margin-left: 0.3rem;
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-purple);
}

.meta-chip-danger {
  color: var(--color-danger);
  background: var(--color-danger-light);
  font-weight: 600;
}
</style>
