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
  The two-step picker: which spans, then which of the things those spans carried.
  Each answered step collapses to a single row stating what was chosen, so the selector shrinks to
  about eighty pixels once the breakdown below it is what the reader is actually looking at. Clicking
  a collapsed row expands that step again in place — the step is a row before it is answered and a
  row after, which is what keeps the whole flow reading as one table rather than three screens.

  Reaching a key through its event type is also what makes the second step honest: `tenant` belongs
  to no event type, so its counts are only meaningful once you say which type's spans you are
  reading it on.
-->
<template>
  <MainCard>
    <template #header>
      <MainCardHeader icon="bi-diagram-3" title="Selection">
        <template #actions>
          <span class="step-progress">{{ stepBadge }}</span>
        </template>
      </MainCardHeader>
    </template>

    <div class="table-responsive">
      <table class="table table-sm mb-0 selector-table">
        <tbody>
          <!-- Step 1, answered and collapsed -->
          <tr v-if="showCollapsedType" class="collapsed-row" @click="expand(1)">
            <td>
              <span class="collapsed-cell">
                <span class="step-badge">1</span>
                <span class="step-label">Event type</span>
                <span class="step-value">{{ selectedType }}</span>
                <span class="step-meta">
                  {{ FormattingService.formatNumber(selectedTypeRow?.spanCount ?? 0) }} spans ·
                  {{ selectedTypeRow?.breakableCount ?? 0 }} attributes to break down
                </span>
                <span class="step-change">
                  change<i class="bi bi-chevron-down"></i>
                </span>
              </span>
            </td>
          </tr>

          <!-- Step 2, answered and collapsed -->
          <tr v-if="showCollapsedKey" class="collapsed-row" @click="expand(2)">
            <td>
              <span class="collapsed-cell">
                <span class="step-badge">2</span>
                <span class="step-label">Attribute</span>
                <span class="step-value">{{ selectedKey?.key }}</span>
                <span class="step-meta">{{ selectedKeySummary }}</span>
                <span class="step-change">
                  change<i class="bi bi-chevron-down"></i>
                </span>
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <LoadingState v-if="loading" message="Loading event types..." />

    <ErrorState v-else-if="error" :message="error" @retry="reload" />

    <!-- Step 1, open -->
    <template v-else-if="openStep === 1">
      <EmptyState
        v-if="eventTypes.length === 0"
        icon="bi-diagram-3"
        title="No spans"
        description="No event type in this profile produced spans, so there is nothing to break down."
      />

      <div v-else class="table-responsive">
        <table class="table table-sm table-hover mb-0">
          <thead>
            <tr>
              <th>Event type</th>
              <th class="text-end">Spans</th>
              <th class="text-end">Traces</th>
              <th style="width: 150px">Share of spans</th>
              <th class="text-end">Attributes</th>
              <th class="text-end">Errors</th>
              <th style="width: 24px"></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="type in eventTypes"
              :key="type.eventType"
              class="pick-row"
              :class="{ 'table-active': type.eventType === selectedType }"
              @click="pickType(type)"
            >
              <td class="mono">{{ type.eventType }}</td>
              <td class="text-end mono">{{ FormattingService.formatNumber(type.spanCount) }}</td>
              <td class="text-end mono">{{ FormattingService.formatNumber(type.traceCount) }}</td>
              <td>
                <div class="share">
                  <span class="share-track"><i :style="{ width: shareOf(type) + '%' }"></i></span>
                  <span class="share-value">{{ Math.round(shareOf(type)) }}%</span>
                </div>
              </td>
              <td class="text-end mono">{{ type.breakableCount }}</td>
              <td class="text-end">
                <Badge
                  v-if="type.errorSpans > 0"
                  :value="FormattingService.formatNumber(type.errorSpans)"
                  variant="danger"
                  size="xs"
                  borderless
                />
                <span v-else class="text-muted">—</span>
              </td>
              <td><i class="bi bi-chevron-right chev"></i></td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- Step 2, open -->
    <template v-else-if="openStep === 2">
      <LoadingState v-if="keysLoading" message="Loading attributes..." />

      <ErrorState v-else-if="keysError" :message="keysError" @retry="loadKeys" />

      <EmptyState
        v-else-if="breakableKeys.length === 0"
        icon="bi-tag"
        title="Nothing to break down"
        :description="emptyKeysDescription"
      />

      <div v-else class="table-responsive">
        <table class="table table-sm table-hover mb-0">
          <thead>
            <tr>
              <th>Attribute</th>
              <th>Source</th>
              <th class="text-end">Values</th>
              <th style="width: 150px">Coverage</th>
              <th style="width: 24px"></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="key in breakableKeys"
              :key="keyToken(key)"
              class="pick-row"
              :class="{ 'table-active': isSelected(key) }"
              @click="pickKey(key)"
            >
              <td class="mono">{{ key.key }}</td>
              <td>
                <Badge
                  :value="SOURCE_LABELS[key.source]"
                  :variant="SOURCE_VARIANTS[key.source]"
                  size="xs"
                  borderless
                  :uppercase="false"
                />
              </td>
              <td class="text-end mono">{{ FormattingService.formatNumber(key.distinctValues) }}</td>
              <td>
                <div class="share">
                  <span class="share-track"><i :style="{ width: coverageOf(key) + '%' }"></i></span>
                  <span class="share-value">{{ Math.round(coverageOf(key)) }}%</span>
                </div>
              </td>
              <td><i class="bi bi-chevron-right chev"></i></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!--
        Said rather than left out: a key kept out of the list because it is too wide is a fact about
        the instrumentation, and silently showing four of a type's eleven keys reads as the type only
        having four.
      -->
      <p v-if="searchOnlyCount > 0" class="search-only-note">
        {{ searchOnlyCount }} more
        {{ searchOnlyCount === 1 ? 'attribute is' : 'attributes are' }}
        too wide to break down — search for
        {{ searchOnlyCount === 1 ? 'it' : 'them' }} instead.
      </p>
    </template>
  </MainCard>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter, type LocationQuery } from 'vue-router';

import Badge from '@shared/components/Badge.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import FormattingService from '@shared/services/FormattingService';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import {
  eventTypeFromQuery,
  keyFromQuery,
  keyToken,
  sameKey,
  type TraceAttributeKeyRow,
  type TraceAttributeSource,
  type TraceSpanTypeRow
} from '@/services/api/model/trace/TraceAttributeModels';

/** Which step is expanded. Null once both are answered — the collapsed rows are the whole selector. */
type OpenStep = 1 | 2 | null;

const SOURCE_LABELS: Record<TraceAttributeSource, string> = {
  ATTRIBUTE: 'Span attribute',
  EVENT_FIELD: 'Declared field',
  SPAN_SHAPE: 'Span shape'
};

const SOURCE_VARIANTS: Record<TraceAttributeSource, 'primary' | 'info' | 'secondary'> = {
  ATTRIBUTE: 'primary',
  EVENT_FIELD: 'info',
  SPAN_SHAPE: 'secondary'
};

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;
const client = new ProfileTracesClient(profileId);

const loading = ref(true);
const error = ref<string | null>(null);
const eventTypes = ref<TraceSpanTypeRow[]>([]);

const keysLoading = ref(false);
const keysError = ref<string | null>(null);
const keys = ref<TraceAttributeKeyRow[]>([]);

/*
 * Both halves of the selection live in the URL, so a breakdown is a link and the browser's Back
 * button steps through the investigation. `reopen` does not: which step is expanded is view state,
 * and putting it in history would make Back walk the picker open and shut.
 */
const reopen = ref<OpenStep>(null);

const selectedType = computed(() => eventTypeFromQuery(route.query));
const selectedKey = computed(() => keyFromQuery(route.query));

const selectedTypeRow = computed(() =>
  eventTypes.value.find(type => type.eventType === selectedType.value)
);

const openStep = computed<OpenStep>(() => {
  if (reopen.value !== null) {
    return reopen.value;
  }
  if (selectedType.value === null) {
    return 1;
  }
  return selectedKey.value === null ? 2 : null;
});

const showCollapsedType = computed(() => selectedType.value !== null && openStep.value !== 1);

/*
 * Only while nothing is expanded. Re-choosing the event type makes the attribute beneath it
 * provisional — it may not survive the new type at all — so a step-2 summary sitting above an open
 * step 1 would be stating a choice that is currently being unmade.
 */
const showCollapsedKey = computed(
  () => selectedType.value !== null && selectedKey.value !== null && openStep.value === null
);

const stepBadge = computed(() => {
  if (selectedType.value === null) {
    return 'Step 1 of 2';
  }
  return selectedKey.value === null ? 'Step 2 of 2' : 'Ready';
});

/** A key past the cap has no breakdown to open, so the second step lists only what it can draw. */
const breakableKeys = computed(() => keys.value.filter(key => !key.searchOnly));
const searchOnlyCount = computed(() => keys.value.length - breakableKeys.value.length);

const emptyKeysDescription = computed(() =>
  searchOnlyCount.value > 0
    ? `Every attribute on these spans has too many values to break down. Search for them instead.`
    : 'Spans of this event type carried no attribute, declared field or shape worth breaking down.'
);

const selectedKeySummary = computed(() => {
  const key = selectedKey.value;
  if (key === null) {
    return '';
  }
  const row = keys.value.find(candidate => sameKey(candidate, key));
  if (row === undefined) {
    return '';
  }
  return `${FormattingService.formatNumber(row.distinctValues)} values · on ${Math.round(coverageOf(row))}% of these spans`;
});

/** Against the busiest type rather than the profile: spans of different types are not a partition. */
function shareOf(type: TraceSpanTypeRow): number {
  const peak = Math.max(...eventTypes.value.map(candidate => candidate.spanCount), 1);
  return Math.min(100, (type.spanCount / peak) * 100);
}

function coverageOf(key: TraceAttributeKeyRow): number {
  const spans = selectedTypeRow.value?.spanCount ?? 0;
  if (spans === 0) {
    return 0;
  }
  return Math.min(100, (key.spanCount / spans) * 100);
}

function isSelected(key: TraceAttributeKeyRow): boolean {
  return selectedKey.value !== null && sameKey(selectedKey.value, key);
}

function expand(step: OpenStep): void {
  reopen.value = step;
}

/*
 * A different event type may not carry the chosen key at all, and the ones that do carry it under
 * different counts — so the key is dropped rather than kept pointing at a breakdown that belongs to
 * the type just left behind.
 */
function pickType(type: TraceSpanTypeRow): void {
  reopen.value = null;
  if (type.eventType === selectedType.value) {
    return;
  }
  const query: LocationQuery = { ...route.query, eventType: type.eventType };
  delete query.key;
  delete query.owner;
  delete query.source;
  router.push({ query });
}

function pickKey(key: TraceAttributeKeyRow): void {
  reopen.value = null;
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

async function loadKeys(): Promise<void> {
  const type = selectedType.value;
  if (type === null) {
    keys.value = [];
    return;
  }
  keysLoading.value = true;
  keysError.value = null;
  try {
    keys.value = await client.getAttributeKeys(type);
  } catch (e: unknown) {
    console.error('Failed to load the attributes of an event type:', e);
    keysError.value = 'Failed to load the attributes of this event type. Please try again.';
    keys.value = [];
  } finally {
    keysLoading.value = false;
  }
}

// Loaded whenever the type changes, not only while step two is open: the collapsed second row reads
// the chosen key's counts out of this list, and a deep link arrives with both steps already answered.
watch(selectedType, loadKeys, { immediate: true });

onMounted(reload);
</script>

<style scoped>
/*
 * The collapsed rows carry card-header weight rather than table-row weight. Two tables stacked is
 * the risk this layout runs, and giving the summary the lighter ground is what stops the page
 * reading as though it holds two datasets.
 */
.selector-table {
  margin-bottom: 0;
}

.selector-table td {
  padding: 0;
  border-bottom: 1px solid var(--color-border);
}

.collapsed-row {
  cursor: pointer;
}

.collapsed-cell {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  background: var(--color-primary-lighter);
}

.collapsed-row:hover .collapsed-cell {
  background: var(--color-primary-light);
}

.step-badge {
  width: 18px;
  height: 18px;
  border-radius: var(--radius-circle);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  background: var(--color-primary);
  color: var(--color-white);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.step-label {
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.step-value {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-heading);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-meta {
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
}

.step-change {
  margin-left: auto;
  flex: none;
  display: flex;
  align-items: center;
  gap: var(--spacing-1);
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
}

.step-change i {
  font-size: var(--font-size-xs);
}

.pick-row {
  cursor: pointer;
}

.mono {
  font-family: var(--font-family-monospace);
}

.chev {
  color: var(--color-text-light);
  font-size: var(--font-size-sm);
}

.share {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.share-track {
  flex: 1;
  height: 5px;
  border-radius: var(--radius-xs);
  background: var(--color-border);
  overflow: hidden;
  min-width: 40px;
}

.share-track i {
  display: block;
  height: 100%;
  background: var(--color-primary);
  opacity: 0.7;
}

.share-value {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  width: 38px;
  text-align: right;
}

.step-progress {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.search-only-note {
  margin: 0;
  padding: var(--spacing-2) var(--spacing-3);
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
  border-top: 1px solid var(--color-border);
}
</style>
