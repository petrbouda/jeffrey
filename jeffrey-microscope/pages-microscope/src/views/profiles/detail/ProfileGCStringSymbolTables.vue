<template>
  <LoadingState v-if="loading" message="Loading string/symbol table statistics..." />

  <ErrorState v-else-if="error" message="Failed to load string/symbol table statistics" />

  <div v-else>
    <PageHeader
      title="String & Symbol Tables"
      description="JVM intern-table footprint and growth from jdk.StringTableStatistics / jdk.SymbolTableStatistics"
      icon="bi-fonts"
    />

    <EmptyState
      v-if="!hasData"
      icon="bi-fonts"
      title="No string/symbol table statistics"
      description="This recording contains no jdk.StringTableStatistics or jdk.SymbolTableStatistics events."
    />

    <div v-else>
      <div class="mb-4">
        <StatsTable :metrics="metrics" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Entries -->
      <div v-show="activeTab === 'entries'">
        <ChartDescription
          shows="Number of entries in the String and Symbol intern tables over time"
          use-case="A steadily climbing string-table count points to interned-string growth (often String.intern() misuse); symbol growth tracks class/method-name churn"
        />
        <TimeSeriesChart
          :primary-data="stringEntries"
          :secondary-data="symbolEntries"
          primary-title="String Table"
          secondary-title="Symbol Table"
          :primary-axis-type="AxisFormatType.NUMBER"
          :secondary-axis-type="AxisFormatType.NUMBER"
          :independent-secondary-axis="true"
          :visible-minutes="60"
          primary-color="#4285F4"
          secondary-color="#FBBC04"
        />
      </div>

      <!-- Footprint -->
      <div v-show="activeTab === 'footprint'">
        <ChartDescription
          shows="Memory footprint of the String and Symbol tables over time"
          use-case="This is native JVM memory that never appears on the heap chart — useful when RSS exceeds heap"
        />
        <TimeSeriesChart
          :primary-data="stringFootprint"
          :secondary-data="symbolFootprint"
          primary-title="String Table"
          secondary-title="Symbol Table"
          :primary-axis-type="AxisFormatType.BYTES"
          :secondary-axis-type="AxisFormatType.BYTES"
          :independent-secondary-axis="true"
          :visible-minutes="60"
          primary-color="#4285F4"
          secondary-color="#FBBC04"
        />
      </div>

      <!-- About -->
      <div v-show="activeTab === 'about'">
        <ConfigurationSection title="How String & Symbol Tables Work" icon="bi-info-circle">
          <p class="about-text">
            The JVM keeps two global hash tables: the <strong>String table</strong> (interned
            <code>String</code> instances, populated by <code>String.intern()</code> and string
            constants) and the <strong>Symbol table</strong> (UTF-8 symbols for class, method, and
            field names). Both consume native memory outside the Java heap. Unbounded growth of the
            string table usually means interned-string misuse; symbol-table growth tracks dynamic
            class generation. JFR reports both periodically via
            <code>jdk.StringTableStatistics</code> and <code>jdk.SymbolTableStatistics</code>.
          </p>
        </ConfigurationSection>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import TabBar from '@/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import ConfigurationSection from '@/components/ConfigurationSection.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type { StringSymbolTablesData } from '@/services/api/model/GCTablesModels';

const route = useRoute();

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<StringSymbolTablesData>();

const tabs = [
  { id: 'entries', label: 'Entries', icon: 'list-ol' },
  { id: 'footprint', label: 'Footprint', icon: 'hdd' },
  { id: 'about', label: 'About', icon: 'info-circle' }
];
const activeTab = ref(tabs[0].id);

const hasData = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return false;
  }
  return h.peakStringEntries > 0 || h.peakSymbolEntries > 0;
});

const stringEntries = computed(() => data.value?.entries.series?.[0]?.data ?? []);
const symbolEntries = computed(() => data.value?.entries.series?.[1]?.data ?? []);
const stringFootprint = computed(() => data.value?.footprint.series?.[0]?.data ?? []);
const symbolFootprint = computed(() => data.value?.footprint.series?.[1]?.data ?? []);

const metrics = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return [];
  }
  return [
    {
      icon: 'fonts',
      title: 'String Table',
      value: FormattingService.formatNumber(h.peakStringEntries),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Peak Footprint', value: FormattingService.formatBytes(h.peakStringFootprint) }
      ]
    },
    {
      icon: 'tags',
      title: 'Symbol Table',
      value: FormattingService.formatNumber(h.peakSymbolEntries),
      variant: 'info' as const,
      breakdown: [
        { label: 'Peak Footprint', value: FormattingService.formatBytes(h.peakSymbolFootprint) }
      ]
    }
  ];
});

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;
    const client = new ProfileGCClient(route.params.profileId as string);
    data.value = await client.getStringSymbolTables();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading string/symbol tables:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(loadData);
</script>

<style scoped>
.about-text {
  font-size: 0.9rem;
  line-height: 1.6;
  color: var(--color-dark);
  margin: 0;
}
</style>
