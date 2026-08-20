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
  The Selection card of Attribute Values and Latency by Attributes: the same two stepper chips the
  Search Conditions builder wears, in a slim card of their own. The card is two lines tall whatever
  the state — the dropdowns float over the breakdown rather than pushing it around.

  Both halves of the selection live in the URL, so a breakdown is a link and the browser's Back
  button steps through the investigation. Reaching a key through its event type is what makes the
  second step honest: `tenant` belongs to no event type, so its counts are only meaningful once you
  say which type's spans you are reading it on.
-->
<template>
  <MainCard :no-padding="true" class="selection-card">
    <div class="selection-head">
      <span class="selection-title">Selection</span>
      <span class="step-pill">{{ stepBadge }}</span>
    </div>

    <LoadingState v-if="loading" message="Loading event types..." />

    <ErrorState v-else-if="error" :message="error" @retry="reload" />

    <EmptyState
      v-else-if="eventTypes.length === 0"
      icon="bi-diagram-3"
      title="No spans"
      description="No event type in this profile produced spans, so there is nothing to break down."
    />

    <div v-else class="selection-chips">
      <TraceAttributeStepPicker
        :event-types="eventTypes"
        :selected-type="selectedType"
        :selected-key-name="selectedKey?.key ?? null"
        key-word="Attribute"
        @pick-type="pickType"
        @pick-key="pickKey"
      />
    </div>
  </MainCard>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter, type LocationQuery } from 'vue-router';

import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import MainCard from '@shared/components/MainCard.vue';
import TraceAttributeStepPicker from '@/components/trace/TraceAttributeStepPicker.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import {
  eventTypeFromQuery,
  keyFromQuery,
  type TraceAttributeKeyRow,
  type TraceSpanTypeRow
} from '@/services/api/model/trace/TraceAttributeModels';

const route = useRoute();
const router = useRouter();
const client = new ProfileTracesClient(route.params.profileId as string);

const loading = ref(true);
const error = ref<string | null>(null);
const eventTypes = ref<TraceSpanTypeRow[]>([]);

const selectedType = computed(() => eventTypeFromQuery(route.query));
const selectedKey = computed(() => keyFromQuery(route.query));

const stepBadge = computed(() => {
  if (selectedType.value === null) {
    return 'Step 1 of 2';
  }
  return selectedKey.value === null ? 'Step 2 of 2' : 'Ready';
});

/*
 * A different event type may not carry the chosen key at all, and the ones that do carry it under
 * different counts — so the key is dropped rather than kept pointing at a breakdown that belongs to
 * the type just left behind. The picker only emits on an actual change.
 */
function pickType(type: TraceSpanTypeRow): void {
  const query: LocationQuery = { ...route.query, eventType: type.eventType };
  delete query.key;
  delete query.owner;
  delete query.source;
  router.push({ query });
}

function pickKey(key: TraceAttributeKeyRow): void {
  router.push({
    query: { ...route.query, source: key.source, owner: key.owner ?? undefined, key: key.key }
  });
}

async function reload(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    eventTypes.value = await client.getSpanEventTypes();
  } catch (e: unknown) {
    console.error('Failed to load the span event types:', e);
    error.value = 'Failed to load event types. Please try again.';
  } finally {
    loading.value = false;
  }
}

onMounted(reload);
</script>

<style scoped>
/*
 * The card must not clip the picker's dropdowns, so the shared overflow:hidden is lifted and the
 * header strip clips its own top corners instead — the same trick the Search Conditions card uses.
 */
.selection-card {
  overflow: visible;
}

.selection-head {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-3) var(--spacing-5);
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
  border-radius: var(--radius-md) var(--radius-md) 0 0;
}

.selection-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.step-pill {
  margin-left: auto;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-primary-light);
  border-radius: var(--radius-pill);
  padding: 1px var(--spacing-2);
}

.selection-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  padding: var(--spacing-3) var(--spacing-5);
}
</style>
