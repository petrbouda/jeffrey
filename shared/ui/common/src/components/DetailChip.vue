<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
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
