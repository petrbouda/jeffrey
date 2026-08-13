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
  A quiet secondary fact on a row: an icon plus a short value. Lighter than a Badge, which
  carries a state; a chip just carries a detail (a count, a timestamp, an id).

  This is a component rather than a class in shared-components.css because it is used from
  slots: slot content compiles in the caller's scope, so a shared list shell cannot style
  chips its callers pass in.
-->
<template>
  <span class="detail-chip" :class="{ mono }">
    <i v-if="icon" :class="icon"></i>
    <slot />
  </span>
</template>

<script setup lang="ts">
defineProps<{
  /** Full bootstrap-icon class, e.g. `bi bi-clock` (same convention as Badge.vue). */
  icon?: string;
  /** Monospace treatment for values worth copying out (ids, hashes). */
  mono?: boolean;
}>();
</script>

<style scoped>
.detail-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-family: var(--font-family-base);
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--color-text-muted);
  letter-spacing: 0.01em;
}

.detail-chip i {
  font-size: 0.6rem;
  opacity: 0.7;
}

/*
 * Ids and hashes are here to be copied, not read, so the monospace treatment carries the
 * de-emphasis with it -- keeping the pairing in one rule rather than leaving callers to
 * re-specify a colour at the same specificity as the base chip.
 */
.detail-chip.mono {
  font-family: var(--font-family-monospace);
  color: var(--color-text-light);
}
</style>
