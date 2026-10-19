<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

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
            :primary-data="rateSeries"
            primary-title="Exceptions / sec"
            :visible-minutes="60"
          />
        </div>
      </div>

      <!-- Application throw sites -->
      <div v-show="activeTab === 'application'">
        <ChartDescription
          shows="Throw sites — an exception class and the frame that threw it — with the JVM's own class-loading, reflection and linking throws left out. Library throws stay: a timeout inside an HTTP client is the application's problem too."
          use-case="Expand a site to see its messages and stack; the frame that threw is marked and library frames fold away."
        />
        <ExceptionSamplingDisabledAlert
          v-if="overview && !overview.hasExceptionThrowEvents"
          :total-throwables="overview.totalThrowables"
          :error-count="overview.errorCount"
        />
        <EmptyState
          v-else-if="applicationSiteCount === 0"
          icon="bi-check2-circle"
          title="No application throws"
          description="Every sampled throw came from the JVM's own class loading, reflection or linking; the All Exceptions tab lists them."
        />
        <ApplicationExceptionsTable v-else :profile-id="profileId" :sites="sites" />
      </div>

      <!-- All exception types -->
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
            throwable constructed, with class, message, thread and stack. In the JDK's bundled
            <code>default</code> config this event is <strong>disabled</strong>; it is only
            <strong>enabled in the <code>profile</code> config</strong> (with a stack trace, and
            throttled). So an empty tab on a <code>default</code> recording usually just means the
            event was never turned on.
          </p>
          <p>
            <code>Error</code> throws, if any, appear separately under the
            <strong>Errors</strong> tab. The Rate Timeline keeps working regardless, because it is
            driven by the always-enabled <code>jdk.ExceptionStatistics</code> counter.
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
        <ExceptionTypesTable
          v-else
          :types="exceptionTypes"
          title="Exception types"
          class-header="Exception Class"
          search-placeholder="Filter exceptions..."
          total-variant="secondary"
          origin-filter
        />
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
            This tab counts <code>jdk.JavaErrorThrow</code> — one event per constructed
            <code>Error</code> (e.g. <code>StackOverflowError</code>,
            <code>NoSuchMethodError</code>). Unlike exception throws, this event is
            <strong
              >enabled in both the <code>default</code> and <code>profile</code> configs</strong
            >, so an empty tab almost always means
            <em>no <code>Error</code> was actually thrown</em> — which is the healthy case.
          </p>
          <p>
            The command above only helps in the rare case where a custom or minimal config
            explicitly turned <code>jdk.JavaErrorThrow</code> off; on a stock recording there is
            nothing to enable.
          </p>
        </DisabledEventsNotice>
        <ExceptionTypesTable
          v-else
          :types="errorTypes"
          title="Error types"
          class-header="Error Class"
          search-placeholder="Filter errors..."
          total-variant="danger"
        />
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
              Exceptions are normal error handling, but they become a performance problem when used
              as control flow. The expensive part isn't the <code>throw</code> — it's constructing
              the exception, because the constructor walks and captures the current call stack. At
              high rates that stack-walking dominates CPU. This page surfaces both the volume and
              the hot types.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-cpu" title="Where the Cost Is">
            <FeatureGrid>
              <FeatureCard icon="bi-layers" variant="danger" title="Stack-trace capture">
                <code>new Throwable()</code> calls <code>fillInStackTrace()</code>, walking every
                frame on the stack. Deep stacks make each exception expensive — this is what makes
                exception-as-control-flow slow.
              </FeatureCard>
              <FeatureCard icon="bi-lightning" variant="warning" title="Control-flow anti-pattern">
                Throwing to signal a normal outcome (parse failures, "not found", loop breaks) in a
                hot path turns a cheap branch into a stack walk. A high throwable rate with few
                unique messages is the tell.
              </FeatureCard>
              <FeatureCard icon="bi-shield-x" variant="purple" title="Errors vs Exceptions">
                <code>Error</code>s (e.g. <code>NoSuchMethodError</code>,
                <code>StackOverflowError</code>) signal serious problems and are tracked separately
                on the Errors tab — even a handful is worth investigating.
              </FeatureCard>
              <FeatureCard icon="bi-gear" variant="info" title="JVM-internal throws">
                The JVM throws and catches by the handful while loading classes, reflecting and
                linking call sites. Nobody can act on those, so the <strong>Application</strong> tab
                leaves them out — library throws such as timeouts stay.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutCallout
            variant="tip"
            title="Total Throwables vs Sampled Throws"
            icon="bi-lightbulb-fill"
          >
            <p>
              <strong>Total Throwables</strong> is a cumulative counter of every throwable
              <em>constructed</em> (even caught-and-ignored ones). <strong>Sampled Throws</strong> /
              <strong>Errors</strong> count individual throw events, which JFR records selectively
              and which may be disabled entirely. A high total with few sampled throws still means
              heavy exception construction — a real, hidden cost.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-diagram-3" title="Application vs JVM Internal">
            <p>
              Every sampled throw is classified by who threw it. A throw is
              <strong>JVM internal</strong> when either rule matches; everything else, libraries
              included, is <strong>Application</strong>:
            </p>
            <ul>
              <li>
                <strong>The class</strong> is one of the JVM's resolution failures — the
                <code>LinkageError</code> family (<code>NoClassDefFoundError</code>,
                <code>NoSuchMethodError</code>, …), the <code>java.lang.invoke</code> throwables, or
                the <code>ReflectiveOperationException</code> family
                (<code>ClassNotFoundException</code>, <code>NoSuchMethodException</code>,
                <code>InvocationTargetException</code>, …).
              </li>
              <li>
                <strong>The thrower</strong> — the frame below the exception's own constructors —
                sits in a class loader (<code>jdk.internal.loader</code>,
                <code>java.lang.ClassLoader</code>, <code>URLClassLoader</code>, Spring Boot's
                loader) or in the <code>java.lang.invoke</code> linking layer, e.g. an
                <code>IOException</code> raised while a loader probes a jar.
              </li>
            </ul>
            <p>
              The same rules drop caught JVM-internal throws from the trace drill-down, so both
              views agree on what counts as noise.
            </p>
          </AboutSection>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.ExceptionStatistics</code> — a periodic snapshot of the cumulative
                throwable count; drives the Rate Timeline. Enabled by default, so the timeline
                always works.
              </li>
              <li>
                <code>jdk.JavaExceptionThrow</code> / <code>jdk.JavaErrorThrow</code> — emitted from
                the throwable's constructor, so they count throwables <em>created</em>, with class,
                message, thread and stack. An <code>Error</code> emits both (twice over), and this
                page collapses them back to one throw. They power the throw sites and per-type
                tables but are <strong>often disabled</strong> (especially
                <code>JavaExceptionThrow</code>), which is why the breakdown may show only Errors or
                an empty state.
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
import ChartDescription from '@shared/components/ChartDescription.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import GenericModal from '@shared/components/GenericModal.vue';
import ExceptionSamplingDisabledAlert from '@/components/alerts/ExceptionSamplingDisabledAlert.vue';
import ApplicationExceptionsTable from '@/components/exceptions/ApplicationExceptionsTable.vue';
import ExceptionTypesTable from '@/components/exceptions/ExceptionTypesTable.vue';
import FormattingService from '@shared/services/FormattingService';
import ProfileExceptionsClient from '@/services/api/ProfileExceptionsClient';
import type {
  ExceptionSiteStat,
  ExceptionsOverview,
  ExceptionTypeStat
} from '@/services/api/model/ExceptionsModels';
import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import { applicationSites } from '@/services/exceptions/exceptionViews';

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
const sites = ref<ExceptionSiteStat[]>([]);

const profileId = route.params.profileId as string;

const activeTab = ref('timeline');
const showThrowablesModal = ref(false);

const rateSeries = computed<number[][]>(() => timeline.value?.series?.[0]?.data ?? []);
// jdk.JavaExceptionThrow and jdk.JavaErrorThrow share one stream; split it so the All Exceptions
// tab shows only non-error throwables and the Errors tab only errors. Without this, a recording that
// emits errors but not exceptions would list errors under "All Exceptions".
const exceptionTypes = computed<ExceptionTypeStat[]>(() =>
  topTypes.value.filter(type => !type.error)
);
const errorTypes = computed<ExceptionTypeStat[]>(() => topTypes.value.filter(type => type.error));
const applicationSiteCount = computed<number>(() => applicationSites(sites.value).length);

const tabs = computed<TabBarItem[]>(() => [
  { id: 'timeline', label: 'Rate Timeline', icon: 'graph-up' },
  {
    id: 'application',
    label: 'Application',
    icon: 'code-slash',
    badge: applicationSiteCount.value || undefined
  },
  {
    id: 'top-types',
    label: 'All Exceptions',
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
      icon: 'code-slash',
      title: 'Application Throws',
      value: FormattingService.formatNumber(o.applicationThrowCount),
      variant: 'success' as const
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
    const client = new ProfileExceptionsClient(profileId);

    const [overviewResult, timelineResult, topTypesResult, sitesResult] = await Promise.all([
      client.getOverview(),
      client.getTimeline(),
      client.getTopTypes(),
      client.getSites()
    ]);

    overview.value = overviewResult;
    timeline.value = timelineResult;
    topTypes.value = topTypesResult;
    sites.value = sitesResult;
    // The throw sites are what a reader comes for; the timeline stays the landing tab only when the
    // recording cannot name the throws.
    if (overviewResult.hasExceptionThrowEvents) {
      activeTab.value = 'application';
    }

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
