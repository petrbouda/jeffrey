<template>
  <LoadingState v-if="loading" message="Loading heap memory data..." />

  <ErrorState v-else-if="error" message="Failed to load heap memory data" />

  <div v-else>
    <!-- Header Section -->
    <PageHeader
      title="Heap Memory Timeseries"
      description="Time-series analysis of heap memory usage, allocation patterns, and GC impact"
      icon="bi-graph-up-arrow"
    />

    <!-- Heap Memory Timeseries Section -->
    <TabBar v-model="activeTab" :tabs="heapMemoryTabs" class="mb-3" />

    <!-- Before/After GC Tab -->
    <div v-show="activeTab === 'before-after-gc'">
      <TimeSeriesChart
        :primary-data="heapMemoryData"
        primary-title="Before/After GC"
        :primary-axis-type="AxisFormatType.BYTES"
        :visible-minutes="60"
        primary-color="#007bff"
        time-unit="seconds"
      />
    </div>

    <!-- Allocation Tab -->
    <div v-show="activeTab === 'allocation'">
      <TimeSeriesChart
        :primary-data="allocationData"
        primary-title="Allocation Rate"
        :primary-axis-type="AxisFormatType.BYTES"
        :visible-minutes="60"
        primary-color="#00d27a"
        time-unit="seconds"
      />
    </div>

    <!-- How It Works Tab -->
    <div v-show="activeTab === 'about'">
      <AboutPanel
        icon="bi-question-circle"
        title="Understanding Heap Memory"
        subtitle="What the before/after-GC sawtooth and allocation rate tell you"
      >
        <AboutCallout variant="intro">
          <p>
            The Java heap fills as your application allocates and drops each time the collector runs —
            the classic sawtooth. The shape of that sawtooth, sampled at every GC, reveals how hard the
            collector is working and whether your live set is stable or growing.
          </p>
        </AboutCallout>

        <AboutSection icon="bi-graph-up" title="Reading the Sawtooth">
          <FeatureGrid>
            <FeatureCard icon="bi-arrow-down-up" variant="primary" title="Before vs After GC">
              The peak is heap used just before a collection; the trough is what survived just after.
              The drop is garbage reclaimed in that cycle.
            </FeatureCard>
            <FeatureCard icon="bi-water" variant="warning" title="Rising troughs = growing live set">
              If the after-GC floor creeps up over time, your <em>live</em> data is growing — a genuine
              leak or a cache without bounds, not just churn.
            </FeatureCard>
            <FeatureCard icon="bi-activity" variant="info" title="Sawtooth frequency">
              Tall, frequent teeth mean high allocation pressure — Eden fills fast. Cross-reference the
              Allocations and GC pages.
            </FeatureCard>
            <FeatureCard icon="bi-plus-circle" variant="success" title="Allocation Rate">
              Bytes allocated per second, independent of the heap occupancy. This is the input that
              drives collection frequency.
            </FeatureCard>
          </FeatureGrid>
        </AboutSection>

        <AboutCallout variant="tip" title="Floor, not peaks" icon="bi-lightbulb-fill">
          For leak hunting, watch the post-GC <em>floor</em>, not the peaks. Peaks reflect allocation
          churn (harmless); a steadily rising floor reflects memory that's never reclaimed.
        </AboutCallout>

        <AboutSection icon="bi-broadcast" title="How JFR Emits This">
          <ul>
            <li>
              <code>jdk.GCHeapSummary</code> — heap used/committed captured <em>before and after</em>
              each collection (the <code>when</code> field). Enabled by default; this is the sawtooth.
            </li>
            <li>
              The allocation-rate series is derived from the allocation events (see the Allocations
              page's "How It Works").
            </li>
          </ul>
        </AboutSection>
      </AboutPanel>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import TabBar from '@/components/TabBar.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import ProfileHeapMemoryClient from '@/services/api/ProfileHeapMemoryClient';
import HeapMemoryTimeseriesType from '@/services/api/model/HeapMemoryTimeseriesType';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';

const route = useRoute();

const loading = ref(true);
const error = ref<string | null>(null);

// Tabs configuration for Heap Memory
const heapMemoryTabs = [
  {
    id: 'before-after-gc',
    label: 'Before/After GC',
    icon: 'memory',
    type: HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC
  },
  {
    id: 'allocation',
    label: 'Allocation Rate',
    icon: 'plus-circle',
    type: HeapMemoryTimeseriesType.ALLOCATION
  },
  { id: 'about', label: 'How It Works', icon: 'book' }
];
const activeTab = ref(heapMemoryTabs[0].id);

// Heap timeseries data
const heapMemoryData = ref<number[][]>([]);
const allocationData = ref<number[][]>([]);
const currentTimeseriesType = ref<HeapMemoryTimeseriesType>(
  HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC
);

// Client initialization - will be set after workspace/project IDs are available
let client: ProfileHeapMemoryClient;

// Reload timeseries data when the user switches tab.
watch(activeTab, async newId => {
  const tab = heapMemoryTabs.find(t => t.id === newId);
  if (!tab?.type) return;

  heapMemoryData.value = [];
  allocationData.value = [];
  currentTimeseriesType.value = tab.type;
  try {
    if (!client) {
      client = new ProfileHeapMemoryClient(route.params.profileId as string);
    }
    if (tab.type === HeapMemoryTimeseriesType.ALLOCATION) {
      const timeseriesData = await client.getTimeseries(HeapMemoryTimeseriesType.ALLOCATION);
      allocationData.value = timeseriesData.data;
    } else if (tab.type === HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC) {
      const timeseriesData = await client.getTimeseries(
        HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC
      );
      heapMemoryData.value = timeseriesData.data;
    }
  } catch (err) {
    console.error('Error loading timeseries data:', err);
    error.value = 'Failed to load timeseries data';
  }
});

// Load heap memory data
const loadHeapMemoryData = async () => {
  try {
    loading.value = true;
    error.value = null;

    // Initialize client if needed
    if (!client) {
      client = new ProfileHeapMemoryClient(route.params.profileId as string);
    }

    // Load heap memory data with default HEAP_BEFORE_AFTER_GC type
    const heapResult = await client.getTimeseries(HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC);
    heapMemoryData.value = heapResult.data;
    currentTimeseriesType.value = HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading heap memory data:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadHeapMemoryData();
});
</script>

<style scoped>
@media (max-width: 768px) {
  .chart-container {
    height: 300px;
  }
}
</style>
