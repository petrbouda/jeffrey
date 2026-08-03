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

<!--
  The docket the three Advisor artifact pages open with: every event type the Advisor knows about, in
  one fixed order, each card carrying that type's own figures. What those figures are is the page's
  business — Prompts has no diff and Patches has no prompt size — so the rows come in through a slot
  and this component owns only the card, its states and the selection.
-->
<template>
  <div class="docket">
    <div class="docket-list">
      <button
        v-for="item in items"
        :key="item.eventType"
        type="button"
        class="docket-item"
        :class="{ selected: item.eventType === selected, unavailable: !item.available }"
        :style="eventTypeVars(item.eventType)"
        :title="item.available ? undefined : `This recording carries no ${item.label} samples`"
        @click="emit('update:selected', item.eventType)"
      >
        <span class="docket-head">
          <span class="docket-name">
            <i class="bi" :class="eventTypeStyle(item.eventType).icon"></i>
            {{ item.label }}
          </span>
          <Badge
            v-if="item.badge"
            :value="item.badge.value"
            :variant="item.badge.variant"
            size="xxs"
          />
          <span v-else-if="!item.available" class="docket-missing">Not recorded</span>
        </span>

        <span v-if="item.available" class="docket-rows">
          <slot name="rows" :item="item"></slot>
        </span>

        <!-- A missing type still says something: the flag that would have captured it. -->
        <span v-else class="docket-note">
          No samples here. Captured by <code>{{ eventTypeCaptureFlag(item.eventType) }}</code
          >.
        </span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import Badge from '@shared/components/Badge.vue';
import {
  eventTypeCaptureFlag,
  eventTypeStyle,
  eventTypeVars
} from '@/views/profiles/detail/advisor/eventTypeStyle';
import type { DocketItem } from '@/views/profiles/detail/advisor/advisorDocket';

defineProps<{
  items: DocketItem[];
  selected: string | null;
}>();

const emit = defineEmits<{
  'update:selected': [eventType: string];
}>();
</script>

<style scoped>
.docket {
  margin-bottom: 1.1rem;
}

.docket-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(212px, 1fr));
  gap: 0.5rem;
}

/* The left spine is the type's own accent — the same device the Overview's manifest uses, so the
   Advisor's pages read as one family. Selection tints the card rather than flooding it, which keeps a
   red type (Blocking) from reading as an error the moment you pick it. */
.docket-item {
  appearance: none;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--et);
  border-radius: var(--radius-base);
  background: var(--color-bg-card);
  padding: 0.6rem 0.72rem 0.65rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  transition:
    background 0.16s,
    border-color 0.16s;
}

.docket-item:hover {
  background: var(--color-light);
}

.docket-item.selected {
  background: var(--et-light);
  border-color: var(--et);
}

.docket-head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.docket-name {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-heading-dark);
}

.docket-name i {
  color: var(--et);
}

.docket-head :deep(.badge) {
  margin-left: auto;
  flex: none;
}

.docket-rows {
  display: flex;
  flex-direction: column;
}

/* A type the recording has no samples for: ruled out with a hatch, still selectable — its page
   explains what is missing and how to record it. Selection reads as a neutral ring rather than the
   type's colour, which would imply there is data behind the card. */
.docket-item.unavailable {
  border-left-color: var(--color-border-input);
  background-color: var(--color-bg-card);
  background-image: repeating-linear-gradient(-45deg, var(--hatch-line) 0 1px, transparent 1px 7px);
}

.docket-item.unavailable .docket-name,
.docket-item.unavailable .docket-name i {
  color: var(--color-text-muted);
}

.docket-item.unavailable:hover {
  background-color: var(--color-light);
}

.docket-item.unavailable.selected {
  background-color: var(--color-lighter);
  border-color: var(--color-border-input);
  border-left-color: var(--color-border-input);
}

.docket-item.unavailable.selected .docket-name {
  color: var(--color-heading-dark);
}

.docket-missing {
  margin-left: auto;
  flex: none;
  font-size: 0.6rem;
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  padding: 0.06rem 0.4rem;
}

.docket-note {
  font-size: 0.68rem;
  line-height: 1.5;
  color: var(--color-text-muted);
}

.docket-note code {
  font-family: var(--font-family-monospace);
  font-size: 0.68rem;
  color: var(--color-heading-dark);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.02rem 0.24rem;
}
</style>
