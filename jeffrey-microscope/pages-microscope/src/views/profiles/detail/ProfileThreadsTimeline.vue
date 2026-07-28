<template>
  <div class="threads-timeline-container">
    <PageHeader
      title="Threads Timeline"
      description="View and analyze thread activities over time"
      icon="bi-clock-history"
    />

    <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

    <div v-show="activeTab === 'timeline'">
      <div class="d-flex align-items-center mb-3">
        <div class="input-group search-container me-3" style="max-width: 60%">
          <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
          <input
            v-model="fulltextFilter"
            type="text"
            class="form-control search-input"
            placeholder="Filter threads..."
            @input="onFilterChange()"
          />
          <button
            v-if="fulltextFilter"
            class="btn btn-outline-secondary clear-btn"
            type="button"
            @click="clearFilter"
          >
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <div class="btn-group btn-group-sm mini-sort-buttons">
          <button
            v-for="(option, index) in sortingTypes"
            :key="index"
            type="button"
            class="btn compact-btn"
            :class="[selectedSorting === option ? 'btn-primary active' : 'btn-outline-primary']"
            @click="sortingChanged({ value: option })"
            :title="`Sort by ${option}`"
          >
            {{ option }}
          </button>
        </div>
        <button
          type="button"
          class="btn icon-info-btn ms-2"
          @click="infoDialogVisible = true"
          title="Thread Information"
        >
          <i class="bi bi-info-circle"></i>
        </button>
      </div>

      <LoadingState v-if="loading" message="Reading thread activity from the recording…" />
      <ErrorState v-else-if="error" :message="error" />

      <div v-else :key="forceRenderThreads" class="thread-components-container">
        <div v-for="(threadRow, index) in threadRows" :key="index" class="thread-row-wrapper">
          <ThreadComponent
            :index="index"
            :project-id="projectId"
            :primary-profile-id="profileId"
            :thread-common="threadCommon as ThreadCommon"
            :thread-row="threadRow"
          />
        </div>

        <EmptyState
          v-if="loadedCount === 0 && isFiltered"
          icon="bi-search"
          title="No threads match the filter"
          :description="`Nothing in this recording's ${totalCount.toLocaleString()} threads contains &quot;${fulltextFilter}&quot;.`"
        />
        <EmptyState
          v-else-if="loadedCount === 0"
          icon="bi-clock-history"
          title="No thread activity"
          description="This recording contains no thread events to lay out on a timeline."
        />

        <div v-else class="page-summary">
          <span class="page-summary-text">{{ pageSummary }}</span>
          <button
            v-if="hasMore"
            class="btn btn-sm btn-primary"
            :disabled="loadingMore"
            @click="loadMore"
          >
            <span
              v-if="loadingMore"
              class="spinner-border spinner-border-sm me-2"
              role="status"
            ></span>
            {{ loadingMore ? 'Loading…' : `Show ${nextPageSize} more` }}
          </button>
          <span v-else class="page-summary-done">All loaded</span>
        </div>
      </div>
    </div>

    <!-- How It Works Tab -->
    <div v-show="activeTab === 'about'">
      <AboutPanel
        icon="bi-question-circle"
        title="Understanding the Threads Timeline"
        subtitle="How per-thread activity bands are reconstructed from JFR samples"
      >
        <AboutCallout variant="intro">
          <p>
            Each row is one thread; the coloured bands along it show what that thread was doing at
            each moment — running on CPU, blocked on a lock, parked, or doing socket/file I/O — laid
            out on a shared wall-clock axis. It's reconstructed by bucketing JFR's per-thread events
            into time slots, so you can see contention and idle periods across all threads at once.
          </p>
        </AboutCallout>

        <AboutSection icon="bi-bar-chart-steps" title="Reading the Timeline">
          <FeatureGrid>
            <FeatureCard icon="bi-list" variant="primary" title="Rows are threads">
              One lane per thread, labelled by name. Filter and sort to bring the threads you care
              about to the top.
            </FeatureCard>
            <FeatureCard icon="bi-palette" variant="info" title="Colours are activity">
              Each colour is an event category (CPU sample, monitor block, park, socket/file I/O) —
              see the legend in the info dialog. A solid band of one colour shows where time went.
            </FeatureCard>
            <FeatureCard icon="bi-dash" variant="neutral" title="Gaps are idle (or unsampled)">
              Empty stretches mean the thread produced no events — genuinely idle, or active in a
              way the recording didn't sample.
            </FeatureCard>
            <FeatureCard icon="bi-zoom-in" variant="success" title="Patterns to spot">
              Many threads blocked at the same instant = contention; one thread saturating CPU = a
              hot path to profile; staircase start times = a warming thread pool.
            </FeatureCard>
          </FeatureGrid>
        </AboutSection>

        <AboutSection icon="bi-layers" title="Working with Large Recordings">
          <FeatureGrid>
            <FeatureCard icon="bi-sort-down" variant="primary" title="Busiest threads first">
              A recording can hold thousands of threads, so the page loads 50 at a time in the order
              you picked and appends the next 50 on request. The footer says how many of the
              recording's threads you are looking at.
            </FeatureCard>
            <FeatureCard icon="bi-funnel" variant="info" title="Sort and filter search everything">
              Both run against the whole recording, not the threads already on screen — a thread
              that would have sorted onto the last page is still one search away.
            </FeatureCard>
            <FeatureCard icon="bi-bounding-box" variant="neutral" title="Bands, not events">
              Events closer together than the timeline can draw apart are merged into one band. The
              band remembers how many events it covers, so the counts stay honest.
            </FeatureCard>
            <FeatureCard icon="bi-cursor" variant="success" title="Details on hover">
              A band carries no field values until you point at it — then the events behind that one
              band are fetched, which is why the first tooltip on a lane takes a moment.
            </FeatureCard>
          </FeatureGrid>
        </AboutSection>

        <AboutSection icon="bi-broadcast" title="How JFR Emits This">
          <p>
            The lanes are assembled from several per-thread event streams, bucketed into time slots
            by their <code>eventThread</code> and timestamp:
          </p>
          <ul>
            <li>
              <code>jdk.ThreadStart</code> / <code>jdk.ThreadEnd</code> — when each lane begins and
              ends.
            </li>
            <li>
              <code>jdk.ExecutionSample</code> — CPU activity (the thread was on-CPU when sampled).
            </li>
            <li>
              <code>jdk.JavaMonitorEnter</code> / <code>jdk.ThreadPark</code> /
              <code>jdk.ThreadSleep</code>
              — blocking bands.
            </li>
            <li>
              <code>jdk.SocketRead</code>/<code>Write</code> and <code>jdk.FileRead</code>/<code
                >Write</code
              >
              — I/O bands.
            </li>
          </ul>
        </AboutSection>
      </AboutPanel>
    </div>
  </div>

  <GenericModal
    modal-id="threadInfoModal"
    size="lg"
    :show="infoDialogVisible"
    title="Thread View Information"
    icon="bi-info-circle"
    modal-dialog-class="modal-dialog-scrollable"
    @update:show="infoDialogVisible = $event"
  >
    <template #default>
      <div class="info-section">
        <h5 class="section-title">Timeline</h5>
        <ul class="info-list">
          <li>Timeline contains green parts representing the lifespan of the threads.</li>
          <li>Other events fits into the thread's lifespan.</li>
          <li>
            Entire timeline is divided into pixels. For longer timelines, multiple events can be
            represented by a single pixel.
          </li>
          <li>
            Events too close together to be drawn apart are merged into one band, which keeps count
            of how many it covers. A single pixel can also hold bands of different types.
          </li>
          <li>
            Pointing at a band fetches the events behind it and shows their fields in the tooltip.
          </li>
        </ul>
      </div>

      <div class="info-section mt-4">
        <h5 class="section-title">Event Types</h5>
        <DataTable table-class="event-type-table">
          <tbody>
            <tr>
              <td class="color-cell">
                <div
                  class="event-color-box"
                  :style="{ 'background-color': ThreadRow.lifespanColor }"
                ></div>
              </td>
              <td>Lifespan of the thread, time between Thread Start and End</td>
            </tr>
            <tr>
              <td class="color-cell">
                <div
                  class="event-color-box"
                  :style="{ 'background-color': ThreadRow.parkedColor }"
                ></div>
              </td>
              <td>
                Parking of the thread: <b>LockSupport#park()</b> (e.g. parking threads in Thread
                Pools)
              </td>
            </tr>
            <tr>
              <td class="color-cell">
                <div
                  class="event-color-box"
                  :style="{ 'background-color': ThreadRow.sleepColor }"
                ></div>
              </td>
              <td>Threads Sleep, emitted by <b>Thread#sleep()</b></td>
            </tr>
            <tr>
              <td class="color-cell">
                <div
                  class="event-color-box"
                  :style="{ 'background-color': ThreadRow.waitingColor }"
                ></div>
              </td>
              <td>Thread Wait, emitted by <b>Thread#wait()</b></td>
            </tr>
            <tr>
              <td class="color-cell">
                <div
                  class="event-color-box"
                  :style="{ 'background-color': ThreadRow.blockedColor }"
                ></div>
              </td>
              <td>Blocked thread, caused by <b>MonitorEnter</b> (e.g. synchronized)</td>
            </tr>
            <tr>
              <td class="color-cell">
                <div
                  class="event-color-box"
                  :style="{ 'background-color': ThreadRow.socketReadColor }"
                ></div>
              </td>
              <td>Blocking reads from a Socket (e.g. <b>SocketInputStream#read</b>)</td>
            </tr>
            <tr>
              <td class="color-cell">
                <div
                  class="event-color-box"
                  :style="{ 'background-color': ThreadRow.socketWriteColor }"
                ></div>
              </td>
              <td>Blocking writes to a Socket (e.g. <b>SocketOutputStream#write</b>)</td>
            </tr>
            <tr>
              <td class="color-cell">
                <div
                  class="event-color-box"
                  :style="{ 'background-color': ThreadRow.fileReadColor }"
                ></div>
              </td>
              <td>Blocking reads from a File (e.g. <b>FileInputStream#read</b>)</td>
            </tr>
            <tr>
              <td class="color-cell">
                <div
                  class="event-color-box"
                  :style="{ 'background-color': ThreadRow.fileWriteColor }"
                ></div>
              </td>
              <td>Blocking writes to a File (e.g. <b>FileOutputStream#write</b>)</td>
            </tr>
          </tbody>
        </DataTable>
      </div>
    </template>
  </GenericModal>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import { onBeforeMount, ref, computed } from 'vue';
import ProfileThreadClient from '@/services/api/ProfileThreadClient.ts';
import ThreadComponent from '@/components/ThreadComponent.vue';
import ThreadCommon from '@/services/api/model/ThreadCommon';
import ThreadRowData from '@/services/api/model/ThreadRowData';
import type { ThreadSort } from '@/services/api/model/ThreadResponse';
import Konva from 'konva';
import ThreadRow from '@/services/thread/ThreadRow';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import GenericModal from '@shared/components/GenericModal.vue';
import TabBar from '@shared/components/TabBar.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import type { PropType } from 'vue';
import '@shared/styles/shared-components.css';

// Props definition
const props = defineProps({
  profile: {
    type: Object,
    default: null
  },
  secondaryProfile: {
    type: Object,
    default: null
  },
  disabledFeatures: {
    type: Array as PropType<string[]>,
    default: () => []
  }
});

const route = useRoute();
const { projectId } = useNavigation();

/**
 * How many threads a request asks for. Each row is a lane's worth of bands, so this is the knob
 * that keeps the first paint quick on a recording with thousands of threads.
 */
const PAGE_SIZE = 50;

const FILTER_DEBOUNCE_MS = 300;

/**
 * The sort buttons, paired with the order the server knows them by.
 */
const SORTINGS: { label: string; sort: ThreadSort }[] = [
  { label: 'Event Count', sort: 'EVENT_COUNT' },
  { label: 'Lifespan', sort: 'LIFESPAN' },
  { label: 'Alphabetically', sort: 'NAME' }
];

const profileId = route.params.profileId as string;

const threadRows = ref<ThreadRowData[]>();
const threadCommon = ref<ThreadCommon>();
const loading = ref<boolean>(true);
const loadingMore = ref<boolean>(false);
const error = ref<string | null>(null);

/** Threads matching the current filter, and threads in the recording — both counted server-side. */
const matchedCount = ref<number>(0);
const totalCount = ref<number>(0);

const activeTab = ref('timeline');
const tabs = [
  { id: 'timeline', label: 'Timeline', icon: 'clock-history' },
  { id: 'about', label: 'How It Works', icon: 'book' }
];
const fulltextFilter = ref<string>('');
const selectedSorting = ref<string>(SORTINGS[0].label);
const sortingTypes = ref<string[]>(SORTINGS.map(s => s.label));

const forceRenderThreads = ref<number>(0);

const infoDialogVisible = ref<boolean>(false);

const loadedCount = computed(() => threadRows.value?.length ?? 0);
const hasMore = computed(() => loadedCount.value < matchedCount.value);
const isFiltered = computed(() => fulltextFilter.value.trim().length > 0);

/** The button promises exactly what is left, so the last page never says "show 50 more" for 7. */
const nextPageSize = computed(() => Math.min(PAGE_SIZE, matchedCount.value - loadedCount.value));

/** Reads as "Showing 50 of 1,204 threads", and says so about the filter when one is applied. */
const pageSummary = computed(() => {
  const shown = loadedCount.value.toLocaleString();
  const matched = matchedCount.value.toLocaleString();
  if (isFiltered.value) {
    return `Showing ${shown} of ${matched} matching threads · ${totalCount.value.toLocaleString()} in the recording`;
  }
  return `Showing ${shown} of ${matched} threads`;
});

let filterTimeout: ReturnType<typeof setTimeout>;

let threadService: ProfileThreadClient;

onBeforeMount(() => {
  // Too many layers, it spams the console
  Konva.showWarnings = false;

  threadService = new ProfileThreadClient(profileId);
  loadFirstPage();
});

function currentSort(): ThreadSort {
  return SORTINGS.find(s => s.label === selectedSorting.value)?.sort ?? 'EVENT_COUNT';
}

/**
 * Replaces what is on screen — used on first paint and whenever the sort or filter changes, since
 * both reorder the whole recording and not just the threads already loaded.
 */
function loadFirstPage() {
  loading.value = true;
  error.value = null;

  threadService
    .list({ sort: currentSort(), nameFilter: fulltextFilter.value, offset: 0, limit: PAGE_SIZE })
    .then(response => {
      threadRows.value = response.rows;
      threadCommon.value = response.common;
      matchedCount.value = response.matchedCount;
      totalCount.value = response.totalCount;
      forceRenderThreads.value++;
    })
    .catch(e => {
      error.value = e instanceof Error ? e.message : 'Failed to load the thread timeline';
    })
    .finally(() => {
      loading.value = false;
    });
}

function loadMore() {
  if (loadingMore.value || !hasMore.value) {
    return;
  }
  loadingMore.value = true;

  threadService
    .list({
      sort: currentSort(),
      nameFilter: fulltextFilter.value,
      offset: loadedCount.value,
      limit: PAGE_SIZE
    })
    .then(response => {
      threadRows.value = [...(threadRows.value ?? []), ...response.rows];
      matchedCount.value = response.matchedCount;
      totalCount.value = response.totalCount;
    })
    .catch(e => {
      error.value = e instanceof Error ? e.message : 'Failed to load more threads';
    })
    .finally(() => {
      loadingMore.value = false;
    });
}

function sortingChanged(newSorting: { value: string }) {
  if (selectedSorting.value === newSorting.value) {
    return;
  }
  selectedSorting.value = newSorting.value;
  loadFirstPage();
}

function onFilterChange() {
  clearTimeout(filterTimeout);
  filterTimeout = setTimeout(loadFirstPage, FILTER_DEBOUNCE_MS);
}

function clearFilter() {
  clearTimeout(filterTimeout);
  fulltextFilter.value = '';
  loadFirstPage();
}
</script>

<style scoped>
.threads-timeline-container {
  width: 100%;
}

.thread-components-container {
  margin-top: 1.5rem;
}

.thread-row-wrapper {
  margin-bottom: 0.5rem;
}

/* Footer under the lanes: how much of the recording is on screen, and how to get the rest. */
.page-summary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  flex-wrap: wrap;
  margin-top: 0.5rem;
  padding: 1rem 1.5rem;
  background-color: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.page-summary-text {
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

.page-summary-done {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  opacity: 0.75;
}

/* Modal styles */
.modal-content {
  border: 1px solid var(--color-border);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.modal-header {
  border-bottom: 1px solid var(--color-border);
  background-color: var(--color-light);
  padding: 10px 16px;
}

.modal-footer {
  border-top: 1px solid var(--color-border);
  background-color: var(--color-light);
  padding: 8px 16px;
}

.info-section {
  margin-bottom: 1.2rem;
}

.section-title {
  font-size: 0.95rem;
  font-weight: 600;
  margin-bottom: 0.8rem;
  color: var(--color-text);
}

.info-list {
  padding-left: 1.25rem;
  font-size: 0.85rem;
}

.info-list li {
  margin-bottom: 0.4rem;
  line-height: 1.4;
}

.color-cell {
  width: 50px;
  vertical-align: middle;
  padding: 0.4rem;
}

.event-color-box {
  width: 10px;
  height: 10px;
  margin: 0 auto;
}

.event-type-table {
  font-size: 0.85rem;
}

.event-type-table td {
  vertical-align: middle;
  padding: 0.4rem 0.6rem;
}
</style>
