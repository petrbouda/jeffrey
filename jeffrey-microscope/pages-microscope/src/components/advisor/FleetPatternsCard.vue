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
  <MainCard no-padding>
    <template #header>
      <MainCardHeader
        icon="bi-diagram-3"
        title="Patterns across your fleet"
        :badge="patterns.length"
      />
    </template>

    <EmptyState
      v-if="patterns.length === 0"
      icon="bi-diagram-3"
      title="No shared patterns yet"
      description="When the same hotspot is confirmed in two or more projects, it will surface here — those are usually fixed once, upstream, rather than in every caller."
    />

    <div v-else class="fleet">
      <div v-for="pattern in patterns" :key="pattern.citedFrame" class="group">
        <button class="group-head" @click="toggle(pattern.citedFrame)">
          <i
            class="bi"
            :class="isOpen(pattern.citedFrame) ? 'bi-chevron-down' : 'bi-chevron-right'"
          ></i>
          <code class="frame">{{ pattern.citedFrame }}</code>
          <span class="swatches">
            <span
              v-for="index in analyzedProjectCount"
              :key="index"
              class="swatch"
              :class="{ off: index > pattern.projectCount }"
            ></span>
          </span>
          <span class="count"
            >{{ pattern.projectCount }} / {{ analyzedProjectCount }} projects</span
          >
          <Badge
            :value="`${pattern.peakSelfPct.toFixed(1)}% peak`"
            variant="warning"
            size="xs"
            :uppercase="false"
          />
        </button>

        <div v-if="isOpen(pattern.citedFrame)" class="occurrences">
          <button
            v-for="occurrence in pattern.occurrences"
            :key="`${occurrence.projectId}-${occurrence.profileId}-${occurrence.eventType}`"
            class="occurrence"
            @click="$emit('open', occurrence)"
          >
            <span class="project">{{ occurrence.profileName ?? occurrence.projectId }}</span>
            <span v-if="occurrence.sourcePath" class="source">{{ occurrence.sourcePath }}</span>
            <span class="share">{{ occurrence.selfPct.toFixed(1) }}%</span>
          </button>
        </div>
      </div>
    </div>
  </MainCard>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import Badge from '@shared/components/Badge.vue';
import type { FleetOccurrence, FleetPattern } from '@/services/api/model/Advisor';

defineProps<{ patterns: FleetPattern[]; analyzedProjectCount: number }>();
defineEmits<{ open: [occurrence: FleetOccurrence] }>();

const openFrames = ref<Set<string>>(new Set());

const isOpen = (frame: string): boolean => openFrames.value.has(frame);

const toggle = (frame: string): void => {
  const next = new Set(openFrames.value);
  if (next.has(frame)) {
    next.delete(frame);
  } else {
    next.add(frame);
  }
  openFrames.value = next;
};
</script>

<style scoped>
.fleet {
  display: flex;
  flex-direction: column;
}

.group {
  border-bottom: 1px solid var(--color-border-light);
}

.group:last-child {
  border-bottom: 0;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 16px;
  background: transparent;
  border: 0;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: background 0.15s ease;
}

.group-head:hover {
  background: var(--color-light);
}

.frame {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.swatches {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.swatch {
  width: 11px;
  height: 11px;
  border-radius: var(--radius-sm);
  background: var(--color-warning);
}

.swatch.off {
  background: var(--color-border);
}

.count {
  font-size: 0.74rem;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.occurrences {
  display: flex;
  flex-direction: column;
  background: var(--color-light);
}

.occurrence {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 16px 9px 44px;
  background: transparent;
  border: 0;
  border-top: 1px solid var(--color-border-light);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.occurrence:hover {
  background: var(--color-white);
}

.project {
  flex: 0 0 auto;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-dark);
}

.source {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 0.73rem;
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share {
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--color-warning);
  white-space: nowrap;
}
</style>
