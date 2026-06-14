<template>
  <div class="stw-legend">
    <div v-for="lane in STW_LANES" :key="lane.category" class="stw-legend-item">
      <span class="stw-legend-swatch" :style="{ backgroundColor: lane.color }"></span>
      <span class="stw-legend-label">{{ lane.label }}</span>
      <span class="stw-legend-count">{{ counts[lane.category] ?? 0 }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { STW_LANES } from '@/services/stw/stwLanes';
import type { StwCategory, StwEvent } from '@/services/api/model/stw/StwModels';

const props = defineProps<{
  events: StwEvent[];
}>();

const counts = computed<Record<string, number>>(() => {
  const result: Partial<Record<StwCategory, number>> = {};
  for (const event of props.events) {
    result[event.category] = (result[event.category] ?? 0) + 1;
  }
  return result as Record<string, number>;
});
</script>

<style scoped>
.stw-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.stw-legend-item {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.8rem;
  color: var(--color-text);
}

.stw-legend-swatch {
  width: 12px;
  height: 12px;
  border-radius: var(--radius-sm);
  display: inline-block;
}

.stw-legend-count {
  font-weight: 600;
  color: var(--color-text-muted);
}
</style>
