<template>
  <div class="region-heatmap">
    <div v-if="snapshots.length === 0">
      <EmptyState
        icon="bi-grid-3x3"
        title="No region snapshots"
        description="Enable jdk.G1HeapRegionInformation in the recording to see the per-region heap layout."
      />
    </div>
    <div v-else>
      <div class="heatmap-toolbar">
        <div class="snapshot-selector" v-if="snapshots.length > 1">
          <label class="selector-label">Snapshot</label>
          <input
            type="range"
            min="0"
            :max="snapshots.length - 1"
            v-model.number="selectedIndex"
            class="snapshot-range"
          />
          <span class="selector-value">
            {{
              FormattingService.formatDuration2Units(selectedSnapshot.timeOffsetMillis * 1_000_000)
            }}
          </span>
        </div>
        <div class="legend">
          <span v-for="bucket in legendBuckets" :key="bucket.label" class="legend-item">
            <span class="legend-swatch" :style="{ background: bucket.color }"></span>
            {{ bucket.label }}
          </span>
        </div>
      </div>

      <div class="region-grid">
        <div
          v-for="cell in selectedSnapshot.regions"
          :key="cell.index"
          class="region-cell"
          :style="{ background: colorForType(cell.type) }"
          :title="`#${cell.index} · ${cell.type} · ${FormattingService.formatBytes(cell.usedBytes)} used`"
        ></div>
      </div>

      <div class="region-summary">
        <Badge
          v-for="bucket in legendBuckets"
          :key="bucket.label"
          :key-label="bucket.label"
          :value="countForBucket(bucket.label)"
          variant="secondary"
          size="s"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import FormattingService from '@/services/FormattingService';
import type { RegionSnapshot } from '@/services/api/model/G1AnalysisData';

const props = defineProps<{
  snapshots: RegionSnapshot[];
}>();

const selectedIndex = ref(0);

watch(
  () => props.snapshots,
  () => {
    selectedIndex.value = props.snapshots.length > 0 ? props.snapshots.length - 1 : 0;
  },
  { immediate: true }
);

const selectedSnapshot = computed<RegionSnapshot>(
  () => props.snapshots[selectedIndex.value] ?? { timeOffsetMillis: 0, regions: [] }
);

interface LegendBucket {
  label: string;
  color: string;
  matches: (type: string) => boolean;
}

const legendBuckets: LegendBucket[] = [
  { label: 'Eden', color: 'var(--color-primary)', matches: t => t.includes('Eden') },
  { label: 'Survivor', color: 'var(--color-success)', matches: t => t.includes('Survivor') },
  { label: 'Old', color: 'var(--color-purple)', matches: t => t.includes('Old') },
  { label: 'Humongous', color: 'var(--color-danger)', matches: t => t.includes('Humongous') },
  { label: 'Archive', color: 'var(--color-warning)', matches: t => t.includes('Archive') },
  { label: 'Free', color: 'var(--color-border)', matches: t => t.includes('Free') }
];

const colorForType = (type: string): string => {
  const bucket = legendBuckets.find(b => b.matches(type ?? ''));
  return bucket ? bucket.color : 'var(--color-text-light)';
};

const countForBucket = (label: string): number => {
  const bucket = legendBuckets.find(b => b.label === label);
  if (!bucket) {
    return 0;
  }
  return selectedSnapshot.value.regions.filter(cell => bucket.matches(cell.type ?? '')).length;
};
</script>

<style scoped>
.heatmap-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
}

.snapshot-selector {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.selector-label {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--color-text-muted);
}

.snapshot-range {
  width: 200px;
}

.selector-value {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-dark);
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.72rem;
  color: var(--color-text-muted);
}

.legend-swatch {
  width: 12px;
  height: 12px;
  border-radius: var(--radius-sm);
  display: inline-block;
}

.region-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(10px, 1fr));
  gap: 2px;
  padding: 0.75rem;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.region-cell {
  aspect-ratio: 1 / 1;
  border-radius: var(--radius-sm);
  cursor: help;
}

.region-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.75rem;
}
</style>
