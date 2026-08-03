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
  <span class="docket-row">
    <span class="docket-key">{{ label }}</span>
    <span class="docket-value" :class="{ mono }" :title="title">
      <slot></slot>
    </span>
  </span>
</template>

<script setup lang="ts">
defineProps<{
  label: string;
  /** Frame names and other code identifiers read better — and truncate more honestly — in monospace. */
  mono?: boolean;
  /** The unabbreviated value, for the values a card has to shorten. */
  title?: string;
}>();
</script>

<style scoped>
/* Everything here is inline-level: these rows live inside the docket's <button>. */
.docket-row {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  padding: 0.22rem 0;
  font-size: 0.71rem;
}

.docket-row + .docket-row {
  border-top: 1px solid var(--color-border-row);
}

.docket-key {
  flex: none;
  font-size: 0.6rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.docket-value {
  margin-left: auto;
  min-width: 0;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--color-heading-dark);
}

.docket-value.mono {
  font-family: var(--font-family-monospace);
  font-size: 0.67rem;
  font-weight: 500;
}
</style>
