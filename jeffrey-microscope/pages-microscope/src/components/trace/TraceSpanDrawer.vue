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

    <!--
      A launcher rather than a list: the timeline needs the whole dialog to be readable, and a
      second, weaker rendering of the same events here would only disagree with it.
    -->
    <div v-else class="drawer-body">
      <p class="events-hint">
        Everything the JVM did on <strong>{{ span.threadName ?? 'this thread' }}</strong> while the
        span was open — samples, allocations, monitor waits — on one timeline.
      </p>
      <button type="button" class="events-open" @click="$emit('viewEvents')">
        <i class="bi bi-list-ul"></i> Open event timeline
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import Badge from '@shared/components/Badge.vue';
import DrawerSection from '@shared/components/drawer/DrawerSection.vue';
import TabBar from '@shared/components/TabBar.vue';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import TraceSpanFlamegraphs from '@/components/trace/TraceSpanFlamegraphs.vue';
import type { TraceSpanFlamegraphRequest } from '@/components/trace/TraceSpanFlamegraphs.vue';
import InfoRow from '@shared/components/drawer/InfoRow.vue';
import FormattingService from '@shared/services/FormattingService';
import type { TraceSpanRow } from '@/services/api/model/trace/TraceModels';

const props = defineProps<{
  profileId: string;
  traceId: string;
  span: TraceSpanRow;
}>();

defineEmits<{
  (event: 'viewFlamegraph', request: TraceSpanFlamegraphRequest): void;
  (event: 'viewEvents'): void;
}>();

const tabs: TabBarItem[] = [
  { id: 'attributes', label: 'Attributes', icon: 'braces' },
  { id: 'events', label: 'Events in span', icon: 'list-ul' },
  { id: 'flamegraph', label: 'Flamegraph', icon: 'fire' }
];

const activeTab = ref('attributes');

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

.events-hint {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin: 0 0 0.7rem;
  line-height: 1.5;
}

.events-hint strong {
  color: var(--color-dark);
  font-weight: 500;
}

.events-open {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  width: 100%;
  justify-content: center;
  padding: 0.4rem 0.6rem;
  font: inherit;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-white);
  background: var(--color-primary);
  border: 0;
  border-radius: var(--radius-base);
  cursor: pointer;
}

.events-open:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
</style>
