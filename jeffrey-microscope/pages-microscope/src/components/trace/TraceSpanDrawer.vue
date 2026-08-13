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
  <aside class="span-drawer">
    <div class="drawer-header">
      <div class="drawer-title" :title="span.name">{{ span.name }}</div>
      <div class="drawer-badges">
        <Badge :variant="kindVariant" size="xs" :value="span.kind" />
        <Badge
          v-if="span.status === 'ERROR'"
          variant="danger"
          size="xs"
          :value="span.errorType ?? 'error'"
        />
      </div>
    </div>

    <TabBar v-model="activeTab" :tabs="tabs" />

    <div v-if="activeTab === 'attributes'" class="drawer-body">
      <DrawerSection label="Timing" icon="bi-clock">
        <InfoRow label="Duration" mono>
          {{ FormattingService.formatDuration2Units(span.durationNanos) }}
        </InfoRow>
        <InfoRow label="Self time" mono>
          {{ FormattingService.formatDuration2Units(span.selfDurationNanos) }}
        </InfoRow>
        <InfoRow label="Start" mono>
          {{ FormattingService.formatDuration2Units(span.startMillisFromBeginning * 1_000_000) }}
          into the recording
        </InfoRow>
      </DrawerSection>

      <DrawerSection label="Identity" icon="bi-fingerprint">
        <InfoRow label="Span id" mono>{{ span.spanId }}</InfoRow>
        <InfoRow label="Parent" mono>{{ span.parentSpanId ?? '—' }}</InfoRow>
        <InfoRow label="Thread" mono>{{ span.threadName ?? span.threadHash }}</InfoRow>
        <InfoRow label="Source event" mono>{{ span.eventType }}</InfoRow>
      </DrawerSection>

      <DrawerSection v-if="attributes.length > 0" label="From the source event" icon="bi-braces">
        <InfoRow v-for="entry in attributes" :key="entry.key" :label="entry.key" mono>
          {{ entry.value }}
        </InfoRow>
      </DrawerSection>
    </div>

    <div v-else-if="activeTab === 'flamegraph'" class="drawer-body">
      <TraceSpanFlamegraphs
        :profile-id="profileId"
        :trace-id="traceId"
        :span-id="span.spanId"
        @view="$emit('viewFlamegraph', $event)"
      />
    </div>

    <div v-else class="drawer-body">
      <LoadingState v-if="eventsLoading" message="Loading events..." />
      <ErrorState v-else-if="eventsError" :message="eventsError" @retry="loadEvents" />
      <EmptyState
        v-else-if="events.length === 0"
        title="Nothing recorded"
        message="Nothing else ran on this thread while the span was open."
        icon="bi-inbox"
      />
      <table v-else class="events-table">
        <thead>
          <tr>
            <th>Event</th>
            <th class="numeric">Duration</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(event, index) in events" :key="index">
            <td>{{ event.eventType }}</td>
            <td class="numeric">
              {{ FormattingService.formatDuration2Units(event.durationNanos) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import Badge from '@shared/components/Badge.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import DrawerSection from '@shared/components/drawer/DrawerSection.vue';
import TabBar from '@shared/components/TabBar.vue';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import TraceSpanFlamegraphs from '@/components/trace/TraceSpanFlamegraphs.vue';
import type { TraceSpanFlamegraphRequest } from '@/components/trace/TraceSpanFlamegraphs.vue';
import InfoRow from '@shared/components/drawer/InfoRow.vue';
import FormattingService from '@shared/services/FormattingService';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import type { TraceEventRow, TraceSpanRow } from '@/services/api/model/trace/TraceModels';

const props = defineProps<{
  profileId: string;
  traceId: string;
  span: TraceSpanRow;
}>();

defineEmits<{ (event: 'viewFlamegraph', request: TraceSpanFlamegraphRequest): void }>();

const tabs: TabBarItem[] = [
  { id: 'attributes', label: 'Attributes', icon: 'braces' },
  { id: 'events', label: 'Events in span', icon: 'list-ul' },
  { id: 'flamegraph', label: 'Flamegraph', icon: 'fire' }
];

const activeTab = ref('attributes');
const events = ref<TraceEventRow[]>([]);
const eventsLoading = ref(false);
const eventsError = ref<string | null>(null);

const kindVariant = computed(() => {
  if (props.span.kind === 'SERVER') {
    return 'primary';
  }
  return props.span.kind === 'CLIENT' ? 'info' : 'secondary';
});

/**
 * The originating event's own fields, flattened for display. Keys the span already shows in its
 * own right are dropped so the section adds information instead of repeating the header.
 */
const HIDDEN_ATTRIBUTES = new Set([
  'traceId',
  'spanId',
  'parentSpanId',
  'name',
  'kind',
  'status',
  'errorType'
]);

const attributes = computed<{ key: string; value: string }[]>(() => {
  if (!props.span.attributes) {
    return [];
  }
  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(props.span.attributes);
  } catch {
    // A malformed payload should not take the drawer down with it.
    return [];
  }
  return Object.entries(parsed)
    .filter(([key, value]) => !HIDDEN_ATTRIBUTES.has(key) && value !== null && value !== '')
    .map(([key, value]) => ({ key, value: String(value) }));
});

async function loadEvents(): Promise<void> {
  eventsLoading.value = true;
  eventsError.value = null;
  try {
    const client = new ProfileTracesClient(props.profileId);
    events.value = await client.getSpanEvents(props.traceId, props.span.spanId);
  } catch {
    eventsError.value = 'Failed to load the events recorded inside this span.';
  } finally {
    eventsLoading.value = false;
  }
}

// Events are fetched only when the tab is opened, and re-fetched when the selection moves to a
// different span while the tab stays open.
watch(
  () => [activeTab.value, props.span.spanId] as const,
  ([tab]) => {
    if (tab === 'events') {
      loadEvents();
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.span-drawer {
  border-left: 1px solid var(--color-border);
  background: var(--color-bg-hover);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.drawer-header {
  padding: 0.8rem 0.9rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-card);
}

.drawer-title {
  font-weight: 500;
  color: var(--color-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-badges {
  display: flex;
  gap: 0.3rem;
  margin-top: 0.35rem;
  flex-wrap: wrap;
}

.drawer-body {
  padding: 0.7rem 0.9rem;
  overflow-y: auto;
}

.events-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
}

.events-table th {
  text-align: left;
  font-size: var(--font-size-xs);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  padding: 0.3rem 0.25rem;
  border-bottom: 1px solid var(--color-border);
}

.events-table td {
  padding: 0.3rem 0.25rem;
  border-bottom: 1px solid var(--color-border-row);
  color: var(--color-text);
}

.numeric {
  text-align: right;
  font-family: var(--font-family-monospace);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
