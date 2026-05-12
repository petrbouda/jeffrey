<template>
  <LoadingState v-if="loading" message="Loading deoptimization data..." />

  <ErrorState v-else-if="error" message="Failed to load deoptimization data" />

  <div v-else>
    <PageHeader
      title="JIT Deoptimizations"
      description="Structured analysis of jdk.Deoptimization events — when speculation fails and the JVM falls back."
      icon="bi-arrow-counterclockwise"
    />

    <!-- Stats row -->
    <div class="mb-4">
      <StatsTable :metrics="metricsData" />
    </div>

    <!-- Tabbed analysis section -->
    <TabBar v-model="activeTab" :tabs="deoptTabs" class="mb-3" />

    <!-- Activity timeseries -->
    <div v-show="activeTab === 'activity'">
        <p class="tab-description">
          Events per second across the recording window. Spikes after warmup may indicate a regression.
        </p>
        <div class="chart-container">
          <TimeSeriesChart
            :primaryData="timeseriesData?.data"
            :primaryTitle="timeseriesData?.name ?? 'Deoptimizations'"
            :visibleMinutes="60"
          />
        </div>
    </div>

    <!-- Events table -->
    <div v-show="activeTab === 'events'">
        <p class="tab-description">
          Per-event detail. Click a row to see full payload. Most recent {{ events.length }} events shown.
        </p>
        <EmptyState
          v-if="events.length === 0"
          icon="bi-arrow-counterclockwise"
          title="No deoptimization events recorded"
        />
        <DataTable v-else>
          <thead>
            <tr>
              <th>Time</th>
              <th>Method</th>
              <th>Line</th>
              <th>BCI</th>
              <th>Reason</th>
              <th>Compiler</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="event in events"
              :key="`${event.timestamp}-${event.compileId}-${event.bci}`"
              @click="showEventDetails(event)"
              style="cursor: pointer"
            >
              <td>
                <div class="time-cell">
                  <span class="time-cell-time">{{ formatEventTime(event.timestamp) }}</span>
                  <span class="time-cell-date">{{ formatEventDate(event.timestamp) }}</span>
                </div>
              </td>
              <td>
                <div class="method-cell">
                  <span class="method-name">{{ getClassMethodName(event.method) }}</span>
                  <span class="method-path">{{ getPackage(event.method) }}</span>
                </div>
              </td>
              <td>{{ event.lineNumber || '—' }}</td>
              <td>{{ event.bci }}</td>
              <td>
                <div class="reason-cell">
                  <span
                    v-if="event.reason"
                    class="compound-pill"
                    :class="`compound-reason-${reasonVariant(event.reason)}`"
                    :title="reasonTooltip(event)"
                  >
                    <span
                      class="compound-pill-action"
                      :class="`compound-action-${actionClass(event.action)}`"
                    >
                      {{ actionAbbr(event.action) }}
                    </span>
                    <span class="compound-pill-reason">
                      {{ displayReason(event.reason) }}
                    </span>
                  </span>
                  <span v-else>—</span>
                  <span v-if="event.instruction" class="reason-cell-instruction code-text">
                    {{ event.instruction }}
                  </span>
                </div>
              </td>
              <td>
                <Badge
                  v-if="event.compiler"
                  :value="event.compiler.toLowerCase()"
                  variant="primary"
                  size="xs"
                />
                <span v-else>—</span>
              </td>
            </tr>
          </tbody>
        </DataTable>
    </div>

    <!-- Top methods -->
    <div v-show="activeTab === 'methods'">
        <p class="tab-description">
          Methods ranked by deopt count. The bar visualizes share relative to the top method.
        </p>
        <EmptyState
          v-if="topMethods.length === 0"
          icon="bi-fire"
          title="No deoptimizations recorded"
        />
        <DataTable v-else>
          <thead>
            <tr>
              <th>#</th>
              <th>Method</th>
              <th>Deopts</th>
              <th>% of Total</th>
              <th>Dominant Reason</th>
              <th>Compilers</th>
              <th class="share-col">Share</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(aggregate, index) in topMethods"
              :key="aggregate.method"
            >
              <td>{{ index + 1 }}</td>
              <td>
                <div class="method-cell">
                  <span class="method-name">{{ getClassMethodName(aggregate.method) }}</span>
                  <span class="method-path">{{ getPackage(aggregate.method) }}</span>
                </div>
              </td>
              <td><strong>{{ FormattingService.formatNumber(aggregate.count) }}</strong></td>
              <td>{{ percentageOfTotal(aggregate.count) }}</td>
              <td>
                <Badge
                  v-if="aggregate.dominantReason"
                  :value="aggregate.dominantReason"
                  :variant="reasonVariant(aggregate.dominantReason)"
                  size="s"
                />
                <span v-else>—</span>
              </td>
              <td>
                <div class="compilers-cell">
                  <Badge
                    v-for="compiler in aggregate.compilers"
                    :key="compiler"
                    :value="compiler.toLowerCase()"
                    variant="primary"
                    size="xs"
                  />
                </div>
              </td>
              <td>
                <div class="share-bar">
                  <div
                    class="share-bar-fill"
                    :style="{
                      width: shareBarWidth(aggregate.count) + '%',
                      background: reasonColor(aggregate.dominantReason)
                    }"
                  ></div>
                </div>
              </td>
            </tr>
          </tbody>
        </DataTable>
    </div>

    <!-- Reason distribution -->
    <div v-show="activeTab === 'distribution'">
        <EmptyState
          v-if="reasonDistribution.length === 0"
          icon="bi-pie-chart"
          title="No deoptimizations recorded"
        />
        <div v-else class="distribution-grid">
          <div class="chart-container">
            <div id="deopt-reason-distribution-chart"></div>
          </div>
          <div class="reason-card-grid">
            <div
              v-for="row in reasonDistribution"
              :key="row.reason"
              class="reason-card"
              :title="reasonExplanation(row.reason)"
            >
              <Badge :value="row.reason" :variant="reasonVariant(row.reason)" size="s" />
              <div class="reason-card-meta">
                <span class="reason-card-count">{{ FormattingService.formatNumber(row.count) }}</span>
                <span class="reason-card-pct">{{ percentageOfTotal(row.count) }}</span>
              </div>
            </div>
          </div>
        </div>
    </div>

    <!-- How It Works (educational, profile-agnostic) -->
    <div v-show="activeTab === 'howit'">
        <div class="howit-section">
          <h6 class="howit-section-title">
            <i class="bi bi-question-circle"></i>
            What is deoptimization?
          </h6>
          <div class="howit-prose">
            <p>
              HotSpot's JIT compilers — <strong>C1</strong> (the client compiler, fast) and
              <strong>C2</strong> (the server compiler, aggressive) — don't translate your bytecode
              literally. They translate it under <strong>optimistic assumptions</strong>: this call
              site is monomorphic, this branch is rarely taken, this field is never
              <code class="code-text">null</code>, this class hierarchy will not gain new subclasses.
              With those assumptions in hand, the compiler produces dramatically faster machine code
              than a literal translation could.
            </p>
            <p>
              But assumptions can be invalidated by runtime: a new subclass loads, a "rarely taken"
              branch is taken, a field <em>does</em> turn out to be null. When that happens, the
              optimized code is no longer correct, so the JVM <strong>deoptimizes</strong>: it
              abandons the optimized version, falls back to the interpreter at this exact bytecode
              index, and may schedule a recompilation that omits the failed assumption. This
              fallback is also called an <em>uncommon trap</em>.
            </p>
          </div>
          <div class="howit-callout howit-callout-tip">
            <strong><i class="bi bi-lightbulb-fill"></i> The takeaway.</strong>
            Deoptimization is not a bug — it's how speculation pays for itself. The JVM speculates
            aggressively because <em>occasionally</em> being wrong (and paying a deopt) is cheaper
            than always being correct (and missing huge optimization wins).
          </div>
        </div>

        <div class="howit-section">
          <h6 class="howit-section-title">
            <i class="bi bi-diagram-3"></i>
            The compilation → deopt lifecycle
          </h6>
          <p class="tab-description">
            Hot methods walk up the tier ladder. When speculation is wrong, they fall back down.
          </p>
          <div class="lifecycle-diagram">
            <svg viewBox="0 0 880 320" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="JIT lifecycle">
              <defs>
                <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto">
                  <path d="M0,0 L10,5 L0,10 z" fill="var(--color-text-muted)" />
                </marker>
                <marker id="arrowDanger" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto">
                  <path d="M0,0 L10,5 L0,10 z" fill="var(--color-danger)" />
                </marker>
              </defs>
              <g font-size="13" font-weight="600" text-anchor="middle">
                <rect x="20" y="60" width="140" height="60" rx="8" fill="var(--color-lighter)" stroke="var(--color-border)" />
                <text x="90" y="86" fill="var(--color-text)">Interpreter</text>
                <text x="90" y="104" fill="var(--color-text-muted)" font-weight="400" font-size="10">runs all bytecode</text>

                <rect x="200" y="60" width="160" height="60" rx="8" fill="var(--color-info-light)" stroke="var(--color-info-border)" />
                <text x="280" y="84" fill="var(--color-info-text)">C1 — Tier 3</text>
                <text x="280" y="102" fill="var(--color-info-text)" font-weight="400" font-size="10">fast compile + profiling</text>

                <rect x="400" y="60" width="160" height="60" rx="8" fill="var(--color-primary-light)" stroke="var(--color-primary-border)" />
                <text x="480" y="84" fill="var(--color-primary)">C2 — Tier 4</text>
                <text x="480" y="102" fill="var(--color-primary)" font-weight="400" font-size="10">aggressive speculation</text>

                <rect x="600" y="60" width="170" height="60" rx="8" fill="var(--color-warning-light)" stroke="var(--color-warning-border)" />
                <text x="685" y="84" fill="var(--color-warning)">Uncommon trap</text>
                <text x="685" y="102" fill="var(--color-warning)" font-weight="400" font-size="10">speculation wrong</text>

                <rect x="400" y="220" width="160" height="60" rx="8" fill="var(--color-info-light)" stroke="var(--color-info-border)" />
                <text x="480" y="244" fill="var(--color-info-text)">Reinterpret</text>
                <text x="480" y="262" fill="var(--color-info-text)" font-weight="400" font-size="10">action: reinterpret</text>

                <rect x="600" y="220" width="170" height="60" rx="8" fill="var(--color-success-light)" stroke="var(--color-success-bg)" />
                <text x="685" y="244" fill="var(--color-success)">Recompile</text>
                <text x="685" y="262" fill="var(--color-success)" font-weight="400" font-size="10">action: recompile</text>
              </g>
              <g stroke="var(--color-text-muted)" stroke-width="1.6" fill="none" marker-end="url(#arrow)">
                <line x1="160" y1="90" x2="200" y2="90" />
                <line x1="360" y1="90" x2="400" y2="90" />
                <line x1="560" y1="90" x2="600" y2="90" />
              </g>
              <g stroke="var(--color-danger)" stroke-width="1.6" fill="none" marker-end="url(#arrowDanger)">
                <path d="M650 120 Q 530 170, 480 220" />
                <path d="M720 120 Q 720 170, 685 220" />
              </g>
              <g stroke="var(--color-success)" stroke-width="1.4" fill="none" stroke-dasharray="5,3" marker-end="url(#arrow)">
                <path d="M600 250 Q 380 250, 380 175 Q 380 130, 480 120" />
              </g>
              <text x="200" y="248" fill="var(--color-success)" font-size="10">re-enter C2 with refined profile</text>
              <g font-size="10" fill="var(--color-text-muted)">
                <text x="180" y="80" text-anchor="middle">≈10k inv</text>
                <text x="380" y="80" text-anchor="middle">≈10k+ inv</text>
                <text x="580" y="80" text-anchor="middle">trap fires</text>
              </g>
            </svg>
          </div>
        </div>

        <div class="howit-section">
          <h6 class="howit-section-title">
            <i class="bi bi-rocket"></i>
            Why does the JVM speculate?
          </h6>
          <div class="howit-prose">
            <p>Speculation is what makes Java fast. Three classics:</p>
            <ul>
              <li>
                <strong>Inlining a virtual call</strong> — after the profiler sees one receiver
                type a million times, C2 inlines the callee directly. Inlining unlocks every other
                optimization downstream.
              </li>
              <li>
                <strong>Removing a null check</strong> — if a field has never been null in
                profiling, C2 removes the check. If it ever <em>does</em> turn out to be null at
                runtime, you get a <code class="code-text">null_check</code> deopt — but the saved
                checks paid off thousands of times before.
              </li>
              <li>
                <strong>Eliminating range checks</strong> — loops with stable bounds let C2 prove
                array indices are in range and skip the per-iteration check. If the predicate
                fails, you get a <code class="code-text">predicate</code> deopt.
              </li>
            </ul>
            <p>
              The trade is asymmetric in your favor: speculation makes 99.9% of executions fast;
              the 0.1% that hit a deopt pay a fraction of a millisecond.
            </p>
          </div>
        </div>

        <div class="howit-section">
          <h6 class="howit-section-title">
            <i class="bi bi-list-ul"></i>
            Reasons reference
          </h6>
          <p class="tab-description">
            The values you'll see in the <code class="code-text">reason</code> column on the Events
            tab and the Reason Distribution tab.
          </p>
          <div class="table-responsive">
            <table class="table table-sm table-hover mb-0">
              <thead>
                <tr>
                  <th>Reason</th>
                  <th>What it means</th>
                  <th>Typical cause</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in reasonReference" :key="row.reason">
                  <td><Badge :value="row.reason" :variant="reasonVariant(row.reason)" size="s" /></td>
                  <td>{{ row.meaning }}</td>
                  <td class="reason-meaning">{{ row.cause }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="howit-section">
          <h6 class="howit-section-title">
            <i class="bi bi-gear"></i>
            Actions reference
          </h6>
          <p class="tab-description">What the JVM does after the trap fires.</p>
          <div class="action-glossary">
            <div v-for="action in actionReference" :key="action.value" class="action-card">
              <h6 class="action-card-heading">
                <Badge :value="action.value" :variant="action.variant" size="s" />
              </h6>
              <p>{{ action.description }}</p>
            </div>
          </div>
        </div>

        <div class="howit-section">
          <div class="howit-grid-2">
            <div>
              <h6 class="howit-section-title">
                <i class="bi bi-check2-circle howit-success-icon"></i>
                When deopt is normal
              </h6>
              <ul class="howit-list">
                <li v-for="point in normalScenarios" :key="point.title">
                  <i class="bi bi-check-circle-fill"></i>
                  <span><strong>{{ point.title }}</strong> {{ point.description }}</span>
                </li>
              </ul>
            </div>
            <div>
              <h6 class="howit-section-title">
                <i class="bi bi-exclamation-triangle howit-warning-icon"></i>
                When deopt is a problem
              </h6>
              <ul class="howit-list howit-list-warn">
                <li v-for="point in problemScenarios" :key="point.title">
                  <i class="bi bi-exclamation-triangle-fill"></i>
                  <span>
                    <strong>{{ point.title }}</strong> {{ point.description }}
                    <button
                      v-if="point.tabLink"
                      class="tab-link"
                      type="button"
                      @click="setActiveTab(point.tabLink)"
                    >
                      Open {{ point.tabLinkLabel }} tab
                    </button>
                  </span>
                </li>
              </ul>
            </div>
          </div>
        </div>

        <div class="howit-section">
          <h6 class="howit-section-title">
            <i class="bi bi-tools"></i>
            Tools &amp; references
          </h6>
          <ul class="refs-list">
            <li>
              <strong>Async-profiler:</strong>
              <code>-e Deoptimization::uncommon_trap_inner</code>
              produces a stack-traced flame graph of deopts (works in production with no JFR
              overhead).
            </li>
            <li>
              <strong>JVM flags for offline inspection:</strong>
              <code>-XX:+PrintCompilation -XX:+TraceDeoptimization -XX:+LogCompilation</code>
            </li>
            <li>
              <strong>JDK-8216041</strong> — JDK 14+ introduced rich
              <code>jdk.Deoptimization</code> JFR events with the reason/action/BCI fields shown
              on the Events tab.
            </li>
            <li>
              <strong>Talks &amp; reading:</strong> Vladimir Ivanov on JVM compilation, Cliff Click
              on speculation, Aleksey Shipilev on JVM internals.
            </li>
          </ul>
        </div>
    </div>

    <!-- Per-event drill-in modal -->
    <GenericModal
      modal-id="deoptEventDetailsModal"
      :show="showEventModal"
      :title="modalTitle"
      icon="bi-arrow-counterclockwise"
      @update:show="showEventModal = $event"
    >
      <div v-if="selectedEvent" class="event-modal-content">
        <div class="event-modal-grid">
          <div>
            <div class="event-modal-section-title">EVENT METADATA</div>
            <div class="event-modal-info">
              <div>
                <strong>Time:</strong>
                {{ FormattingService.formatTimestamp(selectedEvent.timestamp) }}
              </div>
              <div>
                <strong>Thread:</strong>
                <span class="code-text">{{ selectedEvent.thread ?? '—' }}</span>
              </div>
              <div><strong>Compile ID:</strong> {{ selectedEvent.compileId }}</div>
            </div>
          </div>
          <div>
            <div class="event-modal-section-title">CLASSIFICATION</div>
            <div class="event-modal-badges">
              <Badge
                v-if="selectedEvent.reason"
                :value="selectedEvent.reason"
                :variant="reasonVariant(selectedEvent.reason)"
                size="s"
              />
              <Badge
                v-if="selectedEvent.action"
                :value="selectedEvent.action"
                :variant="actionVariant(selectedEvent.action)"
                size="s"
              />
              <Badge
                v-if="selectedEvent.compiler"
                :value="selectedEvent.compiler.toLowerCase()"
                variant="primary"
                size="s"
              />
            </div>
          </div>
        </div>
        <div class="event-modal-block">
          <div class="event-modal-section-title">CODE LOCATION</div>
          <div class="event-modal-code">
            {{ selectedEvent.method ?? '—' }}<span v-if="selectedEvent.lineNumber">:{{ selectedEvent.lineNumber }}</span>
            <br />
            <span class="event-modal-code-secondary">
              → bytecode index {{ selectedEvent.bci }}
              <span v-if="selectedEvent.instruction">
                (<span class="code-text">{{ selectedEvent.instruction }}</span>)
              </span>
            </span>
          </div>
        </div>
        <div v-if="selectedEvent.reason" class="event-modal-block">
          <div class="event-modal-section-title">WHY THIS HAPPENED</div>
          <div class="event-modal-explanation">
            {{ reasonExplanation(selectedEvent.reason) }}
            <button class="tab-link" type="button" @click="goToHowItWorksFromModal">
              Learn more in How It Works →
            </button>
          </div>
        </div>
      </div>
    </GenericModal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import ApexCharts from 'apexcharts';
import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import TabBar from '@/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import DataTable from '@/components/table/DataTable.vue';
import Badge from '@/components/Badge.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import GenericModal from '@/components/GenericModal.vue';
import FormattingService from '@/services/FormattingService';
import ProfileDeoptimizationClient from '@/services/api/ProfileDeoptimizationClient';
import type JITDeoptimizationStats from '@/services/api/model/JITDeoptimizationStats';
import type JITDeoptimizationEvent from '@/services/api/model/JITDeoptimizationEvent';
import type JITDeoptimizationMethodAggregate from '@/services/api/model/JITDeoptimizationMethodAggregate';
import type JITDeoptimizationReasonCount from '@/services/api/model/JITDeoptimizationReasonCount';
import type Serie from '@/services/timeseries/model/Serie';
import type { Variant } from '@/types/ui';

const route = useRoute();

const loading = ref(true);
const error = ref(false);
const stats = ref<JITDeoptimizationStats | null>(null);
const timeseriesData = ref<Serie | null>(null);
const events = ref<JITDeoptimizationEvent[]>([]);
const topMethods = ref<JITDeoptimizationMethodAggregate[]>([]);
const reasonDistribution = ref<JITDeoptimizationReasonCount[]>([]);

const showEventModal = ref(false);
const selectedEvent = ref<JITDeoptimizationEvent | null>(null);

const deoptTabs = [
  { id: 'activity', label: 'Activity', icon: 'graph-up' },
  { id: 'events', label: 'Events', icon: 'table' },
  { id: 'methods', label: 'Top Methods', icon: 'fire' },
  { id: 'distribution', label: 'Reason Distribution', icon: 'pie-chart' },
  { id: 'howit', label: 'How It Works', icon: 'book' }
];
const activeTab = ref(deoptTabs[0].id);

let reasonChart: ApexCharts | null = null;

const metricsData = computed(() => {
  if (!stats.value || stats.value.totalCount === 0) {
    return [
      {
        icon: 'arrow-counterclockwise',
        title: 'Total Deopts',
        value: 0,
        variant: 'highlight' as const,
        breakdown: [{ label: 'No events recorded', value: '—' }]
      },
      {
        icon: 'flag',
        title: 'Top Reason',
        value: '—',
        variant: 'warning' as const
      },
      {
        icon: 'fire',
        title: 'Top Hot Method',
        value: '—',
        variant: 'danger' as const
      },
      {
        icon: 'cpu',
        title: 'Compiler Mix',
        value: '—',
        variant: 'info' as const
      }
    ];
  }

  const total = stats.value.totalCount;
  const durationSeconds = stats.value.recordingDurationMillis / 1000;
  const rate = durationSeconds > 0 ? (total / durationSeconds).toFixed(1) : '—';

  const topReasonShare =
    stats.value.topReason && total > 0
      ? FormattingService.formatPercentage(stats.value.topReasonCount / total)
      : '—';

  const topMethodShare =
    stats.value.topMethod && total > 0
      ? FormattingService.formatPercentage(stats.value.topMethodCount / total)
      : '—';

  return [
    {
      icon: 'arrow-counterclockwise',
      title: 'Total Deopts',
      value: FormattingService.formatNumber(total),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Rate', value: `${rate}/s` },
        { label: 'Distinct methods', value: FormattingService.formatNumber(stats.value.distinctMethods) }
      ]
    },
    {
      icon: 'flag',
      title: 'Top Reason',
      value: stats.value.topReason ?? '—',
      variant: 'warning' as const,
      breakdown: [
        { label: 'Share', value: topReasonShare },
        { label: 'Count', value: FormattingService.formatNumber(stats.value.topReasonCount) }
      ]
    },
    {
      icon: 'fire',
      title: 'Top Hot Method',
      value: stats.value.topMethod ? getClassMethodName(stats.value.topMethod) : '—',
      variant: 'danger' as const,
      breakdown: [
        { label: 'Deopts', value: FormattingService.formatNumber(stats.value.topMethodCount) },
        { label: 'Share', value: topMethodShare }
      ]
    },
    {
      icon: 'cpu',
      title: 'Compiler Mix',
      value:
        stats.value.c2Count > stats.value.c1Count
          ? `C2: ${FormattingService.formatPercentage(
              total > 0 ? stats.value.c2Count / total : 0
            )}`
          : `C1: ${FormattingService.formatPercentage(
              total > 0 ? stats.value.c1Count / total : 0
            )}`,
      variant: 'info' as const,
      breakdown: [
        { label: 'C1', value: FormattingService.formatNumber(stats.value.c1Count) },
        { label: 'C2', value: FormattingService.formatNumber(stats.value.c2Count) }
      ]
    }
  ];
});

const reasonReference = [
  {
    reason: 'class_check',
    meaning: "Receiver type didn't match speculation",
    cause: 'New subclass loaded; previously monomorphic call site sees a second type'
  },
  {
    reason: 'bimorphic_or_optimized',
    meaning: 'An inlined call site got a third receiver',
    cause: 'Genuinely polymorphic call in production traffic'
  },
  {
    reason: 'unstable_if',
    meaning: 'Branch frequencies shifted',
    cause: 'Hot path changed direction (warmup transition, mode switch, load shift)'
  },
  {
    reason: 'unstable_fused_if',
    meaning: 'Fused conditional shifted',
    cause: 'Same as unstable_if for fused branches'
  },
  {
    reason: 'predicate',
    meaning: 'Loop predication assumption failed',
    cause: 'Range check elimination fired anyway — bounds shifted'
  },
  {
    reason: 'null_check',
    meaning: 'Null where speculation said never null',
    cause: 'New input class hit a previously unseen path'
  },
  {
    reason: 'array_check',
    meaning: 'Element-type assumption broke',
    cause: 'Heterogeneous writes into a typed array'
  },
  {
    reason: 'intrinsic',
    meaning: 'Intrinsic precondition failed',
    cause: 'Edge cases of Arrays.copyOf, String.indexOf, etc.'
  },
  {
    reason: 'uncommon_trap',
    meaning: 'Catch-all for rare paths',
    cause: 'First-time exception, rarely-taken branch finally taken once'
  },
  {
    reason: 'class_cast',
    meaning: 'checkcast / instanceof mismatch',
    cause: 'Class hierarchy or generics types diverged from compile-time speculation'
  }
];

const actionReference: Array<{ value: string; variant: Variant; description: string }> = [
  {
    value: 'none',
    variant: 'secondary',
    description:
      'Trap recorded for accounting only. No code change. If the trap repeats, the JVM may upgrade the action to maybe_recompile.'
  },
  {
    value: 'maybe_recompile',
    variant: 'secondary',
    description:
      'Trap counter incremented. If it crosses an internal threshold for this BCI, a fresh compile is scheduled.'
  },
  {
    value: 'reinterpret',
    variant: 'info',
    description:
      "Cheap fallback: this method's frame switches to interpreter at the exact bytecode index. The compiled version is left alone for other call sites."
  },
  {
    value: 'recompile',
    variant: 'success',
    description:
      'Costly. The compiled version is invalidated and a recompile is queued — without the assumption that just failed.'
  }
];

const normalScenarios = [
  {
    title: 'During warmup.',
    description: 'The first seconds of any recording will look noisy — that is the JVM finding the right tier 3/4 mix.'
  },
  {
    title: 'After class loading.',
    description: 'A new subclass invalidates a previously monomorphic call site. Expected.'
  },
  {
    title: 'After hot-path direction changes.',
    description: 'Mode switches, weekend traffic patterns, batch-vs-online — all cause frequencies to shift.'
  },
  {
    title: 'First time a rare branch fires.',
    description: 'One uncommon_trap per rarely-taken path is by design.'
  }
];

const problemScenarios: Array<{
  title: string;
  description: string;
  tabLink?: string;
  tabLinkLabel?: string;
}> = [
  {
    title: 'Same method deopts hundreds of times',
    description: 'long after warmup ends. Use the Top Methods tab to find these.',
    tabLink: 'methods',
    tabLinkLabel: 'Top Methods'
  },
  {
    title: 'Repeated class_check or bimorphic_or_optimized',
    description: 'on the same call site → genuinely megamorphic in production. Refactor candidate.'
  },
  {
    title: 'Steady-state null_check deopts',
    description: 'cleanup nullable code paths or use Optional at the boundary.'
  },
  {
    title: 'Recompile loops',
    description: '(deopt → recompile → deopt again) appear as alternating spikes on the Activity tab. The JVM is failing to find a stable shape.',
    tabLink: 'activity',
    tabLinkLabel: 'Activity'
  }
];

const actionAbbr = (action: string | null | undefined): string => {
  if (!action) return '—';
  switch (action) {
    case 'reinterpret': return 'R';
    case 'recompile': return 'RC';
    case 'maybe_recompile': return 'MR';
    case 'none': return 'N';
    default: return action.substring(0, 2).toUpperCase();
  }
};

const actionClass = (action: string | null | undefined): string => {
  if (!action) return 'none';
  switch (action) {
    case 'reinterpret': return 'reinterpret';
    case 'recompile': return 'recompile';
    case 'maybe_recompile': return 'maybe';
    case 'none': return 'none';
    default: return 'none';
  }
};

// Long reason names overflow narrow cells. Show a compact form; the full value is in the title attr.
const reasonAbbreviations: Record<string, string> = {
  bimorphic_or_optimized_type_check: 'BIMORPHIC',
  intrinsic_or_type_checked_inlining: 'INTRINSIC',
  speculate_class_check: 'SPECULATE_CLASS',
  unstable_fused_if: 'UNSTABLE_FUSED'
};

const displayReason = (reason: string | null | undefined): string => {
  if (!reason) return '';
  const lower = reason.toLowerCase();
  if (reasonAbbreviations[lower]) return reasonAbbreviations[lower];
  return reason.length > 22 ? reason.substring(0, 20) + '…' : reason;
};

const reasonTooltip = (event: { reason: string | null; action: string | null }): string => {
  const parts: string[] = [];
  if (event.reason) parts.push(event.reason);
  if (event.action) parts.push(`action: ${event.action}`);
  return parts.join(' · ');
};

const reasonVariant = (reason: string | null | undefined): Variant => {
  if (!reason) return 'secondary';
  const r = reason.toLowerCase();
  if (r.includes('class_check') || r.includes('class_cast')) return 'danger';
  if (r.includes('bimorphic')) return 'violet';
  if (r.includes('unstable_if') || r.includes('unstable_fused')) return 'warning';
  if (r.includes('null_check')) return 'info';
  if (r.includes('array')) return 'warning';
  if (r.includes('range_check')) return 'primary';
  if (r.includes('loop_limit')) return 'success';
  if (r.includes('speculate')) return 'primary';
  if (r.includes('unloaded')) return 'secondary';
  if (r.includes('profile_predicate') || r.includes('predicate')) return 'secondary';
  if (r.includes('intrinsic') || r.includes('type_checked_inlining')) return 'secondary';
  if (r.includes('uncommon')) return 'secondary';
  return 'secondary';
};

const actionVariant = (action: string | null | undefined): Variant => {
  if (!action) return 'secondary';
  switch (action) {
    case 'reinterpret':
      return 'info';
    case 'recompile':
      return 'success';
    case 'maybe_recompile':
    case 'none':
      return 'secondary';
    default:
      return 'secondary';
  }
};

const reasonColor = (reason: string | null): string => {
  const tokens = getComputedStyle(document.documentElement);
  switch (reasonVariant(reason)) {
    case 'danger':
      return tokens.getPropertyValue('--color-danger').trim();
    case 'violet':
      return tokens.getPropertyValue('--color-violet').trim();
    case 'warning':
      return tokens.getPropertyValue('--color-warning').trim();
    case 'info':
      return tokens.getPropertyValue('--color-info').trim();
    case 'success':
      return tokens.getPropertyValue('--color-success').trim();
    default:
      return tokens.getPropertyValue('--color-secondary').trim();
  }
};

const reasonExplanation = (reason: string): string => {
  const ref = reasonReference.find(r => r.reason === reason);
  return ref ? ref.meaning : 'Compiler-specific deoptimization reason.';
};

const percentageOfTotal = (count: number): string => {
  if (!stats.value || stats.value.totalCount === 0) return '—';
  return FormattingService.formatPercentage(count / stats.value.totalCount);
};

const shareBarWidth = (count: number): number => {
  if (topMethods.value.length === 0) return 0;
  const max = topMethods.value[0].count;
  if (max === 0) return 0;
  return Math.max(2, Math.round((count / max) * 100));
};

const formatEventTime = (millis: number): string => {
  if (!millis) return '—';
  // ISO output is `YYYY-MM-DDTHH:mm:ss.sssZ` → extract `HH:mm:ss.sss`.
  const iso = new Date(millis).toISOString();
  return iso.substring(11, 23);
};

const formatEventDate = (millis: number): string => {
  if (!millis) return '';
  return `${new Date(millis).toISOString().substring(0, 10)} UTC`;
};

const getClassMethodName = (method: string | null | undefined): string => {
  if (!method) return '';
  const lastSep = method.lastIndexOf('#');
  if (lastSep === -1) return method;
  const methodNameWithParams = method.substring(lastSep + 1);
  const packagePath = method.substring(0, lastSep);
  const lastDot = packagePath.lastIndexOf('.');
  const className = lastDot !== -1 ? packagePath.substring(lastDot + 1) : packagePath;
  return `${className}.${methodNameWithParams}`;
};

const getPackage = (method: string | null | undefined): string => {
  if (!method) return '';
  const segments = method.split('.');
  if (segments.length <= 1) return method;
  return segments.slice(0, segments.length - 1).join('.');
};

const showEventDetails = (event: JITDeoptimizationEvent) => {
  selectedEvent.value = event;
  showEventModal.value = true;
};

const modalTitle = computed(() => {
  if (!selectedEvent.value || !selectedEvent.value.method) {
    return 'Deoptimization Event';
  }
  return `Deoptimization · ${getClassMethodName(selectedEvent.value.method)}`;
});

const goToHowItWorksFromModal = () => {
  showEventModal.value = false;
  setActiveTab('howit');
};

const setActiveTab = (tabId: string) => {
  activeTab.value = tabId;
};

// Re-render the reason-distribution chart when its tab becomes visible.
watch(activeTab, newId => {
  if (newId === 'distribution') {
    nextTick(() => createReasonChart());
  }
});

const createReasonChart = () => {
  const chartElement = document.getElementById('deopt-reason-distribution-chart');
  if (!chartElement || reasonDistribution.value.length === 0) {
    return;
  }

  const tokens = getComputedStyle(document.documentElement);
  const border = tokens.getPropertyValue('--color-border-row').trim();

  const data = reasonDistribution.value.map(row => ({
    x: row.reason,
    y: row.count
  }));
  const colors = reasonDistribution.value.map(row => reasonColor(row.reason));

  const options = {
    chart: {
      type: 'bar' as const,
      height: 380,
      fontFamily: 'inherit',
      toolbar: { show: false },
      animations: { enabled: false }
    },
    series: [{ name: 'Events', data }],
    colors,
    plotOptions: {
      bar: {
        horizontal: true,
        borderRadius: 0,
        distributed: true,
        barHeight: '92%'
      }
    },
    legend: { show: false },
    dataLabels: {
      enabled: true,
      formatter: (val: number) => FormattingService.formatNumber(val),
      style: { fontSize: '11px', colors: ['#fff'] },
      offsetX: 30
    },
    xaxis: { labels: { style: { fontSize: '11px' } } },
    yaxis: { labels: { style: { fontSize: '12px', fontWeight: 500 } } },
    grid: { borderColor: border }
  };

  if (reasonChart) {
    reasonChart.destroy();
  }
  reasonChart = new ApexCharts(chartElement, options);
  reasonChart.render();
};

const loadAll = async () => {
  loading.value = true;
  error.value = false;
  try {
    const profileId = route.params.profileId as string;
    const client = new ProfileDeoptimizationClient(profileId);
    const [statsResult, timeseriesResult, eventsResult, topMethodsResult, distributionResult] =
      await Promise.all([
        client.getStatistics(),
        client.getTimeseries(),
        client.getEvents(),
        client.getTopMethods(),
        client.getReasonDistribution()
      ]);
    stats.value = statsResult;
    timeseriesData.value = timeseriesResult;
    events.value = eventsResult;
    topMethods.value = topMethodsResult;
    reasonDistribution.value = distributionResult;
  } catch (e) {
    console.error('Failed to load deoptimization data:', e);
    error.value = true;
  } finally {
    loading.value = false;
  }
};

watch(reasonDistribution, () => {
  // Re-render chart if it's already created and data changes
  if (reasonChart) {
    nextTick(() => createReasonChart());
  }
});

onMounted(() => {
  loadAll();
});

onUnmounted(() => {
  if (reasonChart) {
    reasonChart.destroy();
    reasonChart = null;
  }
});
</script>

<style scoped>
.tab-description {
  margin: 0 0 var(--spacing-3);
  color: var(--color-text-muted);
  font-size: 0.88rem;
}

.chart-container {
  width: 100%;
}

.chart-container > div[id] {
  height: 380px;
}

.method-cell {
  display: flex;
  flex-direction: column;
}

.method-name {
  font-weight: var(--font-weight-medium);
  color: var(--color-dark);
}

.method-path {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.time-cell-time {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 0.85rem;
  font-weight: var(--font-weight-medium);
  color: var(--color-dark);
}

.time-cell-date {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
}

.reason-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.reason-cell-instruction {
  font-size: 0.75rem;
}

/* Compound reason+action pill (Option C — two-tone candy bar) */
.compound-pill {
  display: inline-flex;
  align-items: stretch;
  border-radius: var(--radius-base);
  overflow: hidden;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  line-height: 1;
  cursor: help;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border: 1px solid transparent;
}

.compound-pill-action {
  padding: 0.22rem 0.5rem;
  color: var(--color-white);
  display: inline-flex;
  align-items: center;
  font-size: 0.7rem;
  letter-spacing: 0.05em;
}

.compound-action-reinterpret { background: var(--color-info); }
.compound-action-recompile { background: var(--color-success); }
.compound-action-maybe { background: var(--color-text-muted); }
.compound-action-none { background: var(--color-text-light); }

.compound-pill-reason {
  padding: 0.22rem 0.55rem;
  display: inline-flex;
  align-items: center;
}

.compound-reason-warning .compound-pill-reason {
  background: var(--color-warning-light);
  color: var(--color-warning);
}
.compound-reason-danger .compound-pill-reason {
  background: var(--color-danger-light);
  color: var(--color-danger);
}
.compound-reason-info .compound-pill-reason {
  background: var(--color-info-light);
  color: var(--color-info);
}
.compound-reason-violet .compound-pill-reason {
  background: var(--color-violet-lightest-bg);
  color: var(--color-violet-text);
}
.compound-reason-secondary .compound-pill-reason {
  background: var(--color-secondary-bg);
  color: var(--color-secondary);
}
.compound-reason-success .compound-pill-reason {
  background: var(--color-success-light);
  color: var(--color-success);
}
.compound-reason-primary .compound-pill-reason {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.code-text {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 0.82rem;
  background: var(--color-code-bg);
  color: var(--color-dark);
  padding: 0.1rem 0.35rem;
  border-radius: var(--radius-sm);
}

.compilers-cell {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.share-col {
  width: 220px;
}

.share-bar {
  width: 100%;
  height: 8px;
  background: var(--color-lighter);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.share-bar-fill {
  height: 100%;
  border-radius: var(--radius-sm);
}

.distribution-grid {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

/* Reason Distribution — compact card grid below the bar chart.
   Uses auto-fill so cards reflow to fill the available width (chart + cards stack vertically). */
.reason-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--spacing-2);
  align-content: start;
}

.reason-card {
  padding: 0.45rem 0.6rem;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: var(--spacing-2);
  cursor: help;
}

.reason-card-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.1;
}

.reason-card-count {
  font-weight: var(--font-weight-bold);
  color: var(--color-dark);
  font-size: 0.85rem;
}

.reason-card-pct {
  color: var(--color-text-muted);
  font-size: 0.7rem;
}


.tab-link {
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  color: var(--color-primary);
  text-decoration: underline;
  cursor: pointer;
}

.tab-link:hover {
  color: var(--color-primary-hover);
}

/* How It Works section styles */
.howit-section {
  margin-bottom: 1.5rem;
}

.howit-section-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.1rem;
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
  margin: 0 0 0.75rem;
}

.howit-section-title i {
  color: var(--color-primary);
}

.howit-section-title .howit-success-icon {
  color: var(--color-success);
}

.howit-section-title .howit-warning-icon {
  color: var(--color-warning);
}

.howit-prose {
  max-width: 880px;
  line-height: var(--line-height-relaxed);
  color: var(--color-text);
  font-size: 0.95rem;
}

.howit-prose p {
  margin: 0 0 0.75rem;
}

.howit-prose strong {
  color: var(--color-dark);
}

.howit-prose ul {
  margin: 0 0 0.75rem;
  padding-left: 1.3rem;
}

.howit-prose ul li {
  margin-bottom: 0.25rem;
}

.howit-callout {
  padding: 1rem 1.25rem;
  border-radius: var(--radius-base);
  margin: 0 0 1rem;
}

.howit-callout-tip {
  background: var(--color-success-bg);
  border-left: 4px solid var(--color-success);
  color: var(--color-success-dark);
}

.lifecycle-diagram {
  background: var(--color-light);
  border-radius: var(--radius-lg);
  padding: 1.25rem;
  margin: 0.75rem 0 1.25rem;
}

.lifecycle-diagram svg {
  width: 100%;
  height: auto;
  max-width: 880px;
  display: block;
  margin: 0 auto;
}

.action-glossary {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

@media (max-width: 992px) {
  .action-glossary {
    grid-template-columns: 1fr;
  }
}

.action-card {
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 1rem;
}

.action-card-heading {
  margin: 0 0 0.5rem;
}

.action-card p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.howit-grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
}

@media (max-width: 992px) {
  .howit-grid-2 {
    grid-template-columns: 1fr;
  }
}

.howit-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.howit-list li {
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
  padding: 0.75rem 1rem;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
}

.howit-list li i {
  color: var(--color-success);
  margin-top: 2px;
  flex-shrink: 0;
}

.howit-list-warn li {
  background: var(--color-warning-bg);
  border-color: var(--color-warning-border);
}

.howit-list-warn li i {
  color: var(--color-warning);
}

.refs-list {
  padding-left: 1.2rem;
  margin: 0;
}

.refs-list li {
  margin-bottom: 0.5rem;
  color: var(--color-text);
}

.refs-list code {
  background: var(--color-code-bg);
  color: var(--color-code-text);
  padding: 0.1rem 0.35rem;
  border-radius: var(--radius-sm);
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 0.82rem;
}

.reason-meaning {
  color: var(--color-text-muted);
}

/* Modal content styles */
.event-modal-content {
  font-size: var(--font-size-base);
}

.event-modal-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin-bottom: 1rem;
}

@media (max-width: 768px) {
  .event-modal-grid {
    grid-template-columns: 1fr;
  }
}

.event-modal-section-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 0.5rem;
}

.event-modal-info div {
  font-size: var(--font-size-sm);
  margin-bottom: 0.25rem;
}

.event-modal-badges {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.event-modal-block {
  margin-bottom: 1rem;
}

.event-modal-code {
  background: var(--color-light);
  padding: 0.75rem;
  border-radius: var(--radius-base);
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 0.85rem;
}

.event-modal-code-secondary {
  color: var(--color-text-muted);
}

.event-modal-explanation {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
  color: var(--color-text);
}
</style>
