<template>
  <LoadingState v-if="loading" message="Loading string/symbol table statistics..." />

  <ErrorState v-else-if="error" message="Failed to load string/symbol table statistics" />

  <div v-else>
    <PageHeader
      title="String & Symbol Tables"
      description="JVM intern-table footprint and growth from jdk.StringTableStatistics / jdk.SymbolTableStatistics"
      icon="bi-fonts"
    />

    <DisabledEventsNotice
      v-if="!hasData"
      title="No string/symbol table statistics in this recording"
      icon="bi-fonts"
      action-label="Enable the table-statistics events, then re-record and re-import"
      :command="enableCommand"
    >
      <p>
        These views are built from <code>jdk.StringTableStatistics</code> and
        <code>jdk.SymbolTableStatistics</code> — periodic snapshots of the JVM's two global intern
        tables (interned <code>String</code> instances and the UTF-8 symbols behind class, method,
        and field names). They report native memory that never shows up on the heap chart.
      </p>
      <p>
        In the JDK's bundled configs these events are <strong>disabled in the lean
        <code>default</code> config</strong> and <strong>enabled in <code>profile</code></strong>
        (sampled every 10&nbsp;s). An empty page therefore almost always means the recording used the
        <code>default</code> config — re-record with <code>settings=profile</code>, or enable the two
        events explicitly, and the data will appear.
      </p>

      <template #action>
        <p>
          <strong>A — inline, no extra file.</strong> Use the copyable command above: it keeps the
          bundled <code>profile</code> config and turns both table-statistics events on explicitly.
        </p>
        <p>
          <strong>B — a reusable <code>.jfc</code> overlay.</strong> Save this as
          <code>string-symbol-tables.jfc</code> and record with
          <code>settings=profile,settings=string-symbol-tables.jfc</code>:
        </p>
        <pre class="jfc-block">{{ jfcSnippet }}</pre>
        <p>
          Re-import the <code>.jfr</code> into Jeffrey afterwards. Both events are low-volume periodic
          samples, so leaving them on for the whole run is cheap.
        </p>
      </template>
    </DisabledEventsNotice>

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

      <!-- Deduplication -->
      <div v-show="activeTab === 'deduplication'">
        <ChartDescription
          shows="String-deduplication activity per second: strings deduplicated and heap bytes reclaimed"
          use-case="String deduplication (G1/Shenandoah, -XX:+UseStringDeduplication) collapses duplicate char[] backing arrays; the bytes-saved line quantifies the heap it reclaims"
        />
        <TimeSeriesChart
          v-if="hasDeduplication"
          :primary-data="dedupCount"
          :secondary-data="dedupBytes"
          primary-title="Deduplicated"
          secondary-title="Bytes Saved"
          :primary-axis-type="AxisFormatType.NUMBER"
          :secondary-axis-type="AxisFormatType.BYTES"
          :independent-secondary-axis="true"
          :visible-minutes="60"
          primary-color="#34A853"
          secondary-color="#4285F4"
        />
        <EmptyState
          v-else
          icon="bi-recycle"
          title="String deduplication is disabled"
          description="No jdk.StringDeduplication events were recorded. Enable it with -XX:+UseStringDeduplication (G1 or Shenandoah)."
        />
      </div>

      <!-- How It Works -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding String & Symbol Tables"
          subtitle="The JVM's two global intern tables and the native memory they cost"
        >
          <AboutCallout variant="intro">
            <p>
              The JVM keeps two global hash tables: the <strong>String table</strong> (interned
              <code>String</code> instances, populated by <code>String.intern()</code> and string
              constants) and the <strong>Symbol table</strong> (UTF-8 symbols for class, method, and
              field names). Both consume native memory outside the Java heap. Unbounded string-table
              growth usually means interned-string misuse; symbol-table growth tracks dynamic class
              generation.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-hdd" title="What the Views Show">
            <FeatureGrid>
              <FeatureCard icon="bi-list-ol" variant="primary" title="Entries">
                Entry counts of the String and Symbol tables over time. A steadily climbing
                string-table count points to interned-string growth (often <code>String.intern()</code>
                misuse); symbol growth tracks class/method-name churn.
              </FeatureCard>
              <FeatureCard icon="bi-hdd" variant="info" title="Footprint">
                The native memory footprint of each table over time. This is JVM memory that never
                appears on the heap chart — useful when RSS exceeds the heap and you need to find
                where the rest went.
              </FeatureCard>
              <FeatureCard icon="bi-recycle" variant="success" title="Deduplication">
                String-deduplication activity per second — strings deduplicated and heap bytes
                reclaimed. G1/Shenandoah collapse duplicate <code>char[]</code> backing arrays under
                <code>-XX:+UseStringDeduplication</code>; the bytes-saved line quantifies the heap it
                reclaims.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutCallout variant="tip" title="Native, not heap" icon="bi-lightbulb-fill">
            Both tables live in native memory. If RSS keeps growing while the heap chart stays flat,
            a runaway string or symbol table is one of the usual suspects.
          </AboutCallout>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.StringTableStatistics</code> — periodic stats for the interned-String table
                (entry count, bucket count, total size). Sampled every 10&nbsp;s.
              </li>
              <li>
                <code>jdk.SymbolTableStatistics</code> — periodic stats for the Symbol table (the
                UTF-8 names behind classes, methods, and fields). Sampled every 10&nbsp;s.
              </li>
            </ul>
            <p>
              Both events are <strong>disabled in the bundled <code>default</code> config</strong>
              and <strong>enabled in <code>profile</code></strong>. An empty page almost always means
              the recording used <code>default</code> — re-record with
              <code>settings=profile</code>, or enable the two events explicitly.
            </p>
          </AboutSection>
        </AboutPanel>
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
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import FormattingService from '@shared/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type { StringSymbolTablesData } from '@/services/api/model/GCTablesModels';

const route = useRoute();

const enableCommand =
  'java -XX:StartFlightRecording=settings=profile,jdk.StringTableStatistics#enabled=true,jdk.SymbolTableStatistics#enabled=true,filename=app.jfr,dumponexit=true -jar app.jar';

const jfcSnippet = `<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <event name="jdk.StringTableStatistics">
    <setting name="enabled">true</setting>
    <setting name="period">10 s</setting>
  </event>
  <event name="jdk.SymbolTableStatistics">
    <setting name="enabled">true</setting>
    <setting name="period">10 s</setting>
  </event>
</configuration>`;

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<StringSymbolTablesData>();

const tabs = [
  { id: 'entries', label: 'Entries', icon: 'list-ol' },
  { id: 'footprint', label: 'Footprint', icon: 'hdd' },
  { id: 'deduplication', label: 'Deduplication', icon: 'recycle' },
  { id: 'about', label: 'How It Works', icon: 'book' }
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

const hasDeduplication = computed(() => (data.value?.deduplication?.cycles ?? 0) > 0);
const dedupCount = computed(() => data.value?.deduplication?.timeline.series?.[0]?.data ?? []);
const dedupBytes = computed(() => data.value?.deduplication?.timeline.series?.[1]?.data ?? []);

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
.jfc-block {
  margin: 8px 0 12px;
  padding: 12px 14px;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.78rem;
  line-height: 1.5;
  color: var(--color-text);
  overflow-x: auto;
  white-space: pre;
}
</style>
