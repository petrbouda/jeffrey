<template>
  <div class="exceptions-container">
    <LoadingState v-if="loading" message="Loading exceptions data..." />
    <ErrorState v-else-if="error" message="Failed to load exceptions data" />

    <div v-else>
      <PageHeader
        title="Exceptions"
        description="Exception creation rate over time and sampled throw sites — spot exception storms and exceptions used as control flow"
        icon="bi-exclamation-octagon"
      />

      <div class="mb-4">
        <StatsTable :metrics="metricsData">
          <template #title-action-0>
            <i
              class="bi bi-info-circle text-muted throwables-info-icon"
              title="Click for explanation of Total Throwables vs Sampled Throws"
              @click="showThrowablesModal = true"
            ></i>
          </template>
        </StatsTable>
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Rate Timeline -->
      <div v-show="activeTab === 'timeline'">
        <ChartDescription
          shows="Exceptions created per second across the recording."
          use-case="Sustained plateaus reveal exception-driven control flow; spikes usually correlate with failures or retries."
        />
        <div class="chart-container">
          <TimeSeriesChart
            :primaryData="rateSeries"
            primaryTitle="Exceptions / sec"
            :visibleMinutes="60"
          />
        </div>
      </div>

      <!-- Top Exception Types -->
      <div v-show="activeTab === 'top-types'">
        <ExceptionSamplingDisabledAlert
          v-if="overview && !overview.hasExceptionThrowEvents"
          :total-throwables="overview.totalThrowables"
          :error-count="overview.errorCount"
        />
        <DisabledEventsNotice
          v-else-if="exceptionTypes.length === 0"
          title="No sampled exception throws"
          icon="bi-funnel"
          action-label="Enable jdk.JavaExceptionThrow, then re-record and re-import"
          :command="enableCommand"
        >
          <p>
            The per-type breakdown is built from <code>jdk.JavaExceptionThrow</code> — one event per
            thrown <code>Exception</code> (not <code>Error</code>), with class, message and thread.
            In the JDK's bundled <code>default</code> config this event is <strong>disabled</strong>;
            it is only <strong>enabled in the <code>profile</code> config</strong> (with a stack
            trace, and throttled). So an empty Exceptions tab on a <code>default</code> recording
            usually just means the event was never turned on.
          </p>
          <p>
            <code>Error</code> throws, if any, appear separately under the <strong>Errors</strong>
            tab. The Rate Timeline keeps working regardless, because it is driven by the
            always-enabled <code>jdk.ExceptionStatistics</code> counter.
          </p>

          <template #action>
            <p>
              <strong>A — inline, no extra file.</strong> Record with the bundled
              <code>profile</code> config (which already enables exception throws) or merge the
              event explicitly using the copyable command above.
            </p>
            <p>
              <strong>B — a reusable <code>.jfc</code> overlay.</strong> Save this as
              <code>exceptions.jfc</code> and record with
              <code>settings=profile,settings=exceptions.jfc</code>:
            </p>
            <pre class="jfc-block">{{ jfcSnippet }}</pre>
            <p>
              Re-import the <code>.jfr</code> afterwards. Throwing exceptions for control flow is
              costly because each construction walks the stack via <code>fillInStackTrace()</code> —
              this tab surfaces the hot types once the event is captured.
            </p>
          </template>
        </DisabledEventsNotice>
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="exceptionTypesView.query" search-placeholder="Filter exceptions...">
              <span class="toolbar-info">Exception types</span>
              <template #filters>
                <Badge key-label="Total" :value="exceptionTypesView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Exception Class</th>
              <th class="text-end">Throws</th>
              <th class="text-end">Threads</th>
              <th class="chevron-col"></th>
            </tr>
          </thead>
          <tbody>
            <template v-for="type in exceptionTypesView.visible" :key="type.thrownClass">
              <tr
                class="exception-row"
                :class="{ expandable: hasMessages(type), 'row-expanded': isExpanded(type.thrownClass) }"
                @click="toggleRow(type)"
              >
                <td>
                  <div class="class-with-badge">
                    <ClassNameDisplay :class-name="type.thrownClass" />
                    <Badge
                      v-if="type.error"
                      value="Error"
                      variant="danger"
                      size="xs"
                      borderless
                      class="type-badge"
                    />
                  </div>
                </td>
                <td class="text-end">{{ FormattingService.formatNumber(type.count) }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(type.threadCount) }}</td>
                <td class="text-end">
                  <i
                    v-if="hasMessages(type)"
                    class="bi expand-icon"
                    :class="isExpanded(type.thrownClass) ? 'bi-chevron-up' : 'bi-chevron-down'"
                  ></i>
                </td>
              </tr>
              <tr v-if="isExpanded(type.thrownClass)" class="details-row">
                <td colspan="4" class="p-0">
                  <div class="row-details">
                    <span class="detail-label">
                      Messages
                      <span class="detail-count">{{ type.messages.length }}</span>
                    </span>
                    <SearchableMessageList :messages="type.messages" />
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="exceptionTypesView.visible.length"
              :match-count="exceptionTypesView.matchCount"
              :total="exceptionTypesView.total"
              :expanded="exceptionTypesView.expanded"
              :page-size="exceptionTypesView.pageSize"
              @toggle="exceptionTypesView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Errors -->
      <div v-show="activeTab === 'errors'">
        <DisabledEventsNotice
          v-if="errorTypes.length === 0"
          title="No Error throws recorded"
          icon="bi-shield-check"
          action-label="Re-enable jdk.JavaErrorThrow only if a custom config disabled it"
          :command="enableCommand"
        >
          <p>
            This tab counts <code>jdk.JavaErrorThrow</code> — one event per thrown
            <code>Error</code> (e.g. <code>StackOverflowError</code>,
            <code>NoSuchMethodError</code>). Unlike exception throws, this event is
            <strong>enabled in both the <code>default</code> and <code>profile</code> configs</strong>,
            so an empty tab almost always means <em>no <code>Error</code> was actually thrown</em> —
            which is the healthy case.
          </p>
          <p>
            The command above only helps in the rare case where a custom or minimal config explicitly
            turned <code>jdk.JavaErrorThrow</code> off; on a stock recording there is nothing to
            enable.
          </p>
        </DisabledEventsNotice>
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="errorTypesView.query" search-placeholder="Filter errors...">
              <span class="toolbar-info">Error types</span>
              <template #filters>
                <Badge key-label="Total" :value="errorTypesView.matchCount" variant="danger" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Error Class</th>
              <th class="text-end">Throws</th>
              <th class="text-end">Threads</th>
              <th class="chevron-col"></th>
            </tr>
          </thead>
          <tbody>
            <template v-for="type in errorTypesView.visible" :key="type.thrownClass">
              <tr
                class="exception-row"
                :class="{ expandable: hasMessages(type), 'row-expanded': isExpanded(type.thrownClass) }"
                @click="toggleRow(type)"
              >
                <td>
                  <ClassNameDisplay :class-name="type.thrownClass" />
                </td>
                <td class="text-end">{{ FormattingService.formatNumber(type.count) }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(type.threadCount) }}</td>
                <td class="text-end">
                  <i
                    v-if="hasMessages(type)"
                    class="bi expand-icon"
                    :class="isExpanded(type.thrownClass) ? 'bi-chevron-up' : 'bi-chevron-down'"
                  ></i>
                </td>
              </tr>
              <tr v-if="isExpanded(type.thrownClass)" class="details-row">
                <td colspan="4" class="p-0">
                  <div class="row-details">
                    <span class="detail-label">
                      Messages
                      <span class="detail-count">{{ type.messages.length }}</span>
                    </span>
                    <SearchableMessageList :messages="type.messages" />
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="errorTypesView.visible.length"
              :match-count="errorTypesView.matchCount"
              :total="errorTypesView.total"
              :expanded="errorTypesView.expanded"
              :page-size="errorTypesView.pageSize"
              @toggle="errorTypesView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- How It Works Tab -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Exceptions"
          subtitle="Why exceptions cost more than you think — and what these counts really mean"
        >
          <AboutCallout variant="intro">
            <p>
              Exceptions are normal error handling, but they become a performance problem when used as
              control flow. The expensive part isn't the <code>throw</code> — it's constructing the
              exception, because the constructor walks and captures the current call stack. At high
              rates that stack-walking dominates CPU. This page surfaces both the volume and the hot
              types.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-cpu" title="Where the Cost Is">
            <FeatureGrid>
              <FeatureCard icon="bi-layers" variant="danger" title="Stack-trace capture">
                <code>new Throwable()</code> calls <code>fillInStackTrace()</code>, walking every frame
                on the stack. Deep stacks make each exception expensive — this is what makes
                exception-as-control-flow slow.
              </FeatureCard>
              <FeatureCard icon="bi-lightning" variant="warning" title="Control-flow anti-pattern">
                Throwing to signal a normal outcome (parse failures, "not found", loop breaks) in a hot
                path turns a cheap branch into a stack walk. A high throwable rate with few unique
                messages is the tell.
              </FeatureCard>
              <FeatureCard icon="bi-shield-x" variant="purple" title="Errors vs Exceptions">
                <code>Error</code>s (e.g. <code>NoSuchMethodError</code>, <code>StackOverflowError</code>)
                signal serious problems and are tracked separately on the Errors tab — even a handful is
                worth investigating.
              </FeatureCard>
              <FeatureCard icon="bi-dash-circle" variant="info" title="Pre-allocated exceptions">
                The JIT may optimize frequently-thrown implicit exceptions (e.g. NPEs) into
                stackless, pre-allocated singletons — so a real storm can show <em>more</em> throwables
                than sampled throws.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutCallout variant="tip" title="Total Throwables vs Sampled Throws" icon="bi-lightbulb-fill">
            <p>
              <strong>Total Throwables</strong> is a cumulative counter of every throwable
              <em>constructed</em> (even caught-and-ignored ones). <strong>Sampled Throws</strong> /
              <strong>Errors</strong> count individual throw events, which JFR records selectively and
              which may be disabled entirely. A high total with few sampled throws still means heavy
              exception construction — a real, hidden cost.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.ExceptionStatistics</code> — a periodic snapshot of the cumulative throwable
                count; drives the Rate Timeline. Enabled by default, so the timeline always works.
              </li>
              <li>
                <code>jdk.JavaExceptionThrow</code> / <code>jdk.JavaErrorThrow</code> — one event per
                thrown exception/error with class, message and thread. These power the per-type tables
                but are <strong>often disabled</strong> (especially <code>JavaExceptionThrow</code>),
                which is why the breakdown may show only Errors or an empty state.
              </li>
            </ul>
          </AboutSection>
        </AboutPanel>
      </div>
    </div>

    <GenericModal
      modal-id="throwablesInfoModal"
      size="lg"
      :show="showThrowablesModal"
      title="Total Throwables vs Sampled Throws"
      icon="bi-info-circle"
      @update:show="showThrowablesModal = $event"
    >
      <div class="throwables-info-content">
        <p>
          <strong>Total Throwables</strong> comes from the periodic
          <code>jdk.ExceptionStatistics</code> event and counts <em>every throwable constructed</em>
          since JVM start — including exceptions that are caught immediately or never thrown.
        </p>
        <p>
          <strong>Sampled Throws</strong> and <strong>Errors</strong> count the individual
          <code>jdk.JavaExceptionThrow</code> / <code>jdk.JavaErrorThrow</code> events, which JFR
          records selectively (and which may be disabled entirely in the recording configuration).
        </p>
        <div class="alert alert-info mb-0">
          <i class="bi bi-lightbulb text-info me-2"></i>
          A high <em>Total Throwables</em> rate with few sampled throws still indicates heavy
          exception construction — a common hidden cost when exceptions are used for control flow.
        </div>
      </div>
    </GenericModal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@shared/components/layout/PageHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import TabBar from '@shared/components/TabBar.vue';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import SearchableMessageList from '@/components/SearchableMessageList.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import ChartDescription from '@shared/components/ChartDescription.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import Badge from '@shared/components/Badge.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import GenericModal from '@shared/components/GenericModal.vue';
import ExceptionSamplingDisabledAlert from '@/components/alerts/ExceptionSamplingDisabledAlert.vue';
import FormattingService from '@shared/services/FormattingService';
import ProfileExceptionsClient from '@/services/api/ProfileExceptionsClient';
import type { ExceptionsOverview, ExceptionTypeStat } from '@/services/api/model/ExceptionsModels';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import { useTableView } from '@/composables/useTableView';

const route = useRoute();

const enableCommand =
  'java -XX:StartFlightRecording=settings=profile,jdk.JavaExceptionThrow#enabled=true,jdk.JavaErrorThrow#enabled=true,filename=app.jfr,dumponexit=true -jar app.jar';

const jfcSnippet = `<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <event name="jdk.JavaExceptionThrow">
    <setting name="enabled">true</setting>
    <setting name="stackTrace">true</setting>
  </event>
  <event name="jdk.JavaErrorThrow">
    <setting name="enabled">true</setting>
    <setting name="stackTrace">true</setting>
  </event>
</configuration>`;

const loading = ref(true);
const error = ref(false);

const overview = ref<ExceptionsOverview>();
const timeline = ref<TimeseriesData>();
const topTypes = ref<ExceptionTypeStat[]>([]);

const activeTab = ref('timeline');
const showThrowablesModal = ref(false);

// Expanded exception-type rows (keyed by thrown class), shared across the two tables.
const expandedRows = ref<Set<string>>(new Set());

const rateSeries = computed<number[][]>(() => timeline.value?.series?.[0]?.data ?? []);
// jdk.JavaExceptionThrow and jdk.JavaErrorThrow share one stream; split it so the Exceptions tab
// shows only non-error throwables and the Errors tab only errors. Without this, a recording that
// emits errors but not exceptions would list errors under "Exceptions".
const exceptionTypes = computed<ExceptionTypeStat[]>(() => topTypes.value.filter(type => !type.error));
const errorTypes = computed<ExceptionTypeStat[]>(() => topTypes.value.filter(type => type.error));

const exceptionTypesView = useTableView<ExceptionTypeStat>(exceptionTypes, {
  searchableText: type => type.thrownClass
});
const errorTypesView = useTableView<ExceptionTypeStat>(errorTypes, {
  searchableText: type => type.thrownClass
});

const hasMessages = (type: ExceptionTypeStat): boolean => type.messages.length > 0;

const isExpanded = (thrownClass: string): boolean => expandedRows.value.has(thrownClass);

const toggleRow = (type: ExceptionTypeStat): void => {
  if (!hasMessages(type)) {
    return;
  }
  const next = new Set(expandedRows.value);
  if (next.has(type.thrownClass)) {
    next.delete(type.thrownClass);
  } else {
    next.add(type.thrownClass);
  }
  expandedRows.value = next;
};

const tabs = computed<TabBarItem[]>(() => [
  { id: 'timeline', label: 'Rate Timeline', icon: 'graph-up' },
  {
    id: 'top-types',
    label: 'Exceptions',
    icon: 'funnel',
    badge: exceptionTypes.value.length || undefined
  },
  {
    id: 'errors',
    label: 'Errors',
    icon: 'exclamation-triangle',
    badge: errorTypes.value.length || undefined,
    badgeVariant: 'danger'
  },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

const metricsData = computed(() => {
  if (!overview.value) {
    return [];
  }
  const o = overview.value;
  return [
    {
      icon: 'exclamation-octagon',
      title: 'Total Throwables',
      value: FormattingService.formatNumber(o.totalThrowables),
      variant: 'highlight' as const
    },
    {
      icon: 'funnel',
      title: 'Sampled Throws',
      value: FormattingService.formatNumber(o.sampledThrowCount),
      variant: 'info' as const
    },
    {
      icon: 'exclamation-triangle-fill',
      title: 'Errors',
      value: FormattingService.formatNumber(o.errorCount),
      variant: 'danger' as const
    },
    {
      icon: 'diagram-2',
      title: 'Distinct Types',
      value: FormattingService.formatNumber(o.distinctTypes),
      variant: 'warning' as const
    }
  ];
});

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    const client = new ProfileExceptionsClient(profileId);

    const [overviewResult, timelineResult, topTypesResult] = await Promise.all([
      client.getOverview(),
      client.getTimeline(),
      client.getTopTypes()
    ]);

    overview.value = overviewResult;
    timeline.value = timelineResult;
    topTypes.value = topTypesResult;

    loading.value = false;
  } catch (e) {
    console.error('Failed to load exceptions data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.exceptions-container {
  width: 100%;
  color: var(--color-text);
}

.chart-container {
  width: 100%;
}

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

/* Keep the Error badge aligned with the class name (top line of the cell). */
.class-with-badge {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.type-badge {
  margin-top: 1px;
}

.chevron-col {
  width: 40px;
}

/* Expandable exception rows (Guardian-style) */
.exception-row.expandable {
  cursor: pointer;
}

.exception-row.row-expanded > td {
  background-color: var(--color-bg-hover-alt);
}

.expand-icon {
  font-size: 0.8rem;
  color: var(--color-text-light);
}

.details-row:hover > td {
  background: transparent;
}

.row-details {
  background: var(--color-light);
  padding: 0.85rem 1.25rem 1rem;
  border-bottom: 1px solid var(--color-border);
}

.detail-label {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-text-muted);
}

.detail-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.1rem;
  padding: 0 0.3rem;
  border-radius: var(--radius-sm);
  background: var(--color-secondary-bg);
  color: var(--color-text-muted);
  font-size: 0.65rem;
}

.throwables-info-icon {
  margin-left: 0.5rem;
  font-size: 0.875rem;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.2s ease;
}

.throwables-info-icon:hover {
  opacity: 1;
}

.throwables-info-content {
  font-size: 0.95rem;
  line-height: 1.5;
}

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
