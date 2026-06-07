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
  <div>
    <LoadingState v-if="loading" message="Loading span heatmap..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <EmptyState
      v-else-if="!heatmap || heatmap.rows.length === 0"
      title="No Spans"
      message="No async-profiler spans were recorded in this profile."
      icon="bi-grid-3x3"
    />

    <div v-else class="dashboard-container">
      <ChartSection title="Spans Heatmap — tag by time" icon="grid-3x3" :full-width="true">
        <template #header-actions>
          <div class="btn-group btn-group-sm metric-toggle" role="group">
            <button
              type="button"
              class="btn"
              :class="metric === 'p95' ? 'btn-primary' : 'btn-outline-secondary'"
              @click="metric = 'p95'"
            >
              p95 latency
            </button>
            <button
              type="button"
              class="btn"
              :class="metric === 'count' ? 'btn-primary' : 'btn-outline-secondary'"
              @click="metric = 'count'"
            >
              Span count
            </button>
          </div>
        </template>
        <SpanHeatmapChart :data="heatmap" :metric="metric" />
      </ChartSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';

import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import ChartSection from '@/components/ChartSection.vue';
import SpanHeatmapChart from '@/components/span/SpanHeatmapChart.vue';
import ProfileAsyncProfilerClient from '@/services/api/ProfileAsyncProfilerClient';
import type { SpanHeatmapMetric } from '@/services/span/spanHeatmapSeries';
import type { SpanHeatmap } from '@/services/api/model/span/SpanModels';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heatmap = ref<SpanHeatmap | null>(null);
const metric = ref<SpanHeatmapMetric>('p95');

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    const client = new ProfileAsyncProfilerClient(profileId);
    heatmap.value = await client.getHeatmap();
  } catch (e: unknown) {
    console.error('Failed to load span heatmap:', e);
    error.value = 'Failed to load span heatmap. Please try again.';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}

.metric-toggle {
  white-space: nowrap;
}
</style>
