<!--
  - Jeffrey
  - Copyright (C) 2024 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import ThreadRowData from '@/services/api/model/ThreadRowData';

import ThreadCommon from '@/services/api/model/ThreadCommon';
import ThreadPeriod from '@/services/api/model/ThreadPeriod';
import ThreadRow from '@/services/thread/ThreadRow';
import {
  THREAD_CATEGORIES,
  categoryCoverageNanos,
  categoryEventCount,
  presentCategories as categoriesPresentOn
} from '@/services/thread/ThreadCategories';
import type { ThreadCategory } from '@/services/thread/ThreadCategories';
import FormattingService from '@shared/services/FormattingService';
import Badge from '@shared/components/Badge.vue';
import PrimaryFlamegraphClient from '@/services/api/PrimaryFlamegraphClient';
import ProfileThreadClient from '@/services/api/ProfileThreadClient';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import SearchBarComponent from '@/components/SearchBarComponent.vue';
import GenericModal from '@shared/components/GenericModal.vue';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter.ts';

const props = withDefaults(
  defineProps<{
    index: number;
    projectId: string;
    primaryProfileId: string;
    threadCommon: ThreadCommon;
    threadRow: ThreadRowData;
    /** How many threads this lane stands for; 1 means an ordinary thread. */
    threadCount?: number;
    /** The group a collapsed lane stands for, which is what its requests are scoped by. */
    groupKey?: string | null;
    expanded?: boolean;
  }>(),
  { threadCount: 1, groupKey: null, expanded: false }
);

defineEmits<{ toggle: [] }>();

/**
 * A lane standing for several threads has no thread of its own: its {@link ThreadInfo} carries
 * placeholder ids. Everything that would otherwise be scoped by those ids — the flamegraph, the
 * band tooltips, the thread details — is scoped by the group instead.
 */
const collapsed = computed(() => props.threadCount > 1);

const threadGroup = computed<string | null>(() => (collapsed.value ? props.groupKey : null));

/** How many of a group's threads the details modal lists at a time. */
const MEMBER_PAGE_SIZE = 50;

const threadClient = new ProfileThreadClient(props.primaryProfileId);

const selectedEventCode = ref();
const showFlameMenu = ref(false);
const flameMenuPosition = ref({
  top: '0px',
  left: '0px'
});

const contextMenuItems = createContextMenuItems();

const canvasId = ref(`thread-canvas-${props.index}`);

/**
 * Keyed by the lane's position rather than by a thread id: a collapsed lane has no id of its own, so
 * every one of them would otherwise open the same modal.
 */
const flamegraphModalId = ref(`flamegraphModal-${props.index}`);

const showFlamegraphDialog = ref(false);

/**
 * Brings a reloaded graph back into view. The modal's overlay is what scrolls — the global
 * `.modal-overlay` carries the `overflow-y` — so it is also what the flamegraph tracks.
 */
function scrollToTop() {
  const wrapper = document.getElementById(flamegraphModalId.value);
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}
const showInfoModal = ref(false);

const threadInfo = props.threadRow.threadInfo;

let threadRowRenderer: ThreadRow;

/** One renderer per open sub-lane, so every canvas can be torn down again. */
let categoryRenderers: ThreadRow[] = [];

/** Whether the row is showing its per-event-type breakdown. */
const breakdownOpen = ref(false);

/**
 * The event types this thread actually has, in the order the lane draws them. A thread that only
 * ever read from a socket gets one sub-lane, not eight mostly-empty ones.
 */
const presentCategories = computed<ThreadCategory[]>(() => categoriesPresentOn(props.threadRow));

/** Canvas ids need the category as well as the row: `index` alone collides across sub-lanes. */
function categoryCanvasId(category: ThreadCategory): string {
  return `${canvasId.value}-${category.state.toLowerCase()}`;
}

function categoryLabel(category: ThreadCategory): string {
  return category.metadata(props.threadCommon.metadata).label;
}

let flamegraphTooltip: FlamegraphTooltip;

let graphUpdater: GraphUpdater;

// Store scroll handler reference for proper cleanup
const handleScroll = () => {
  if (threadRowRenderer != null) {
    threadRowRenderer.onWindowScroll();
  }
  categoryRenderers.forEach(renderer => renderer.onWindowScroll());

  // Close menu on scroll
  if (showFlameMenu.value) {
    showFlameMenu.value = false;
  }
};

/**
 * Determines whether to use weight based on the event type.
 * By default, use weight for allocation and blocking events.
 *
 * @param eventCode the event type code
 * @returns true if weight should be used, false otherwise
 */
function resolveWeight(eventCode: string | undefined): boolean {
  if (!eventCode) {
    return false;
  }

  const isAllocationEvent =
    eventCode === 'jdk.ObjectAllocationInNewTLAB' ||
    eventCode === 'jdk.ObjectAllocationOutsideTLAB' ||
    eventCode === 'jdk.ObjectAllocationSample';

  const isBlockingEvent =
    eventCode === 'jdk.JavaMonitorEnter' ||
    eventCode === 'jdk.JavaMonitorWait' ||
    eventCode === 'jdk.ThreadPark';

  return isAllocationEvent || isBlockingEvent;
}

const useWeightValue = computed(() => resolveWeight(selectedEventCode.value));

/**
 * How many events a category stands for across the whole lane. A band can cover several events that
 * the timeline cannot draw apart, so this is not the number of bands.
 *
 * A lane total is the right answer here — this panel describes the lane. It is the wrong answer for
 * a tooltip, which describes one position: merging chains, so on a busy lane a single band holds the
 * whole recording and would report this same number wherever the pointer is. That is what
 * `ThreadWindowDetails` asks the server about instead.
 */
function eventCount(periods: ThreadPeriod[]): number {
  return categoryEventCount(periods);
}

/**
 * How much of the recording a category's bands cover, in nanoseconds.
 *
 * An upper bound on the time spent in that state rather than the figure itself: bands merge events
 * that are too close together to draw apart, and a merged band spans the gaps it bridged. Everything
 * rendering this has to say so — see {@link coverageTitle}.
 */
function coverageNanos(periods: ThreadPeriod[]): number {
  return categoryCoverageNanos(periods);
}

function coverageLabel(periods: ThreadPeriod[]): string {
  const covered = coverageNanos(periods);
  const share =
    props.threadCommon.totalDuration > 0
      ? Math.round((covered / props.threadCommon.totalDuration) * 100)
      : 0;
  return `~${FormattingService.formatDuration2Units(covered)} (${share}%)`;
}

const coverageTitle =
  'Time covered by this type’s drawn bands. Bands merge events too close together to tell apart, ' +
  'and a merged band spans the gaps it bridged, so this is an upper bound on the time spent in ' +
  'this state.';

/**
 * Opens or closes the per-event-type breakdown.
 *
 * The sub-lane canvases are built only once their containers are in the DOM: a Konva stage reads its
 * width from `offsetWidth` exactly once, so one constructed while hidden would stay zero pixels wide
 * forever. They also borrow the lane's hover cache — same thread, same windows.
 */
async function toggleBreakdown(): Promise<void> {
  breakdownOpen.value = !breakdownOpen.value;

  if (!breakdownOpen.value) {
    destroyCategoryRenderers();
    return;
  }

  await nextTick();
  categoryRenderers = presentCategories.value.map(category => {
    const renderer = new ThreadRow(
      props.primaryProfileId,
      props.threadCommon,
      props.threadRow,
      categoryCanvasId(category),
      threadGroup.value,
      { states: [category.state], windowDetails: threadRowRenderer.sharedWindowDetails() }
    );
    renderer.draw();
    return renderer;
  });
}

function destroyCategoryRenderers(): void {
  categoryRenderers.forEach(renderer => renderer.destroy());
  categoryRenderers = [];
}

onMounted(() => {
  // Initialize thread row
  threadRowRenderer = new ThreadRow(
    props.primaryProfileId,
    props.threadCommon,
    props.threadRow,
    canvasId.value,
    threadGroup.value
  );
  threadRowRenderer.draw();

  // Add scroll listener with stored handler reference for proper cleanup
  document.addEventListener('scroll', handleScroll);
});

const toggleFlamegraphMenu = (event: MouseEvent) => {
  const buttonRect = (event.target as Element).getBoundingClientRect();
  flameMenuPosition.value = {
    top: `${buttonRect.bottom + 5}px`,
    left: `${buttonRect.left}px`
  };
  showFlameMenu.value = !showFlameMenu.value;
};

/**
 * The threads behind a collapsed lane, listed a page at a time — a pool can hold hundreds of them,
 * and the lane itself only ever knew how many there were.
 */
const members = ref<ThreadRowData[]>([]);
const matchedMembers = ref(0);
const loadingMembers = ref(false);
const membersError = ref<string | null>(null);

const hasMoreMembers = computed(() => members.value.length < matchedMembers.value);

function loadMembers(): void {
  const group = threadGroup.value;
  if (group === null || loadingMembers.value) {
    return;
  }

  loadingMembers.value = true;
  membersError.value = null;
  threadClient
    .members({
      group: group,
      sort: 'EVENT_COUNT',
      offset: members.value.length,
      limit: MEMBER_PAGE_SIZE
    })
    .then(page => {
      members.value = members.value.concat(page.rows);
      matchedMembers.value = page.matchedCount;
    })
    .catch(() => {
      membersError.value = 'Threads of this group could not be loaded.';
    })
    .finally(() => {
      loadingMembers.value = false;
    });
}

/** What a thread reports for an id it never got. */
const UNKNOWN_THREAD_ID = -1;

function memberKey(member: ThreadRowData): string {
  return `${member.threadInfo.javaId}-${member.threadInfo.osId}`;
}

/**
 * Every thread in a group carries the same name, so a member is told apart by its ids. A thread can
 * be missing either one — a native thread never got a Java id, a virtual thread has no OS id.
 */
function memberIds(member: ThreadRowData): string {
  const ids = [];
  if (Number(member.threadInfo.javaId) !== UNKNOWN_THREAD_ID) {
    ids.push(`Java ID ${member.threadInfo.javaId}`);
  }
  if (Number(member.threadInfo.osId) !== UNKNOWN_THREAD_ID) {
    ids.push(`OS ID ${member.threadInfo.osId}`);
  }
  return ids.length > 0 ? ids.join(' · ') : 'No thread id';
}

const memberSummary = computed(() => {
  if (loadingMembers.value && members.value.length === 0) {
    return 'Loading threads…';
  }
  const shown = members.value.length.toLocaleString();
  return `Showing ${shown} of ${matchedMembers.value.toLocaleString()} threads in this lane`;
});

const openInfoModal = () => {
  showInfoModal.value = true;
  if (collapsed.value && members.value.length === 0) {
    loadMembers();
  }
};

const executeMenuItem = (item: any) => {
  showFlameMenu.value = false;
  item.command();
};

// Clean up event listeners when the component is unmounted
onUnmounted(() => {
  // Remove scroll listener with correct handler reference
  document.removeEventListener('scroll', handleScroll);

  // Clean up thread-row renderer resources (Konva stage, event handlers, etc.)
  destroyCategoryRenderers();
  if (threadRowRenderer) {
    threadRowRenderer.destroy();
  }
});

const showFlamegraph = (eventCode: string) => {
  const flamegraphClient = new PrimaryFlamegraphClient(
    props.primaryProfileId,
    eventCode,
    // Thread mode puts a synthetic root frame under every thread. On a collapsed lane that would
    // split the graph by the very thing the lane collapsed — one sliver per pool worker — so a
    // group merges instead, and only a single thread keeps its own root.
    !collapsed.value,
    null,
    false,
    false,
    false,
    props.threadRow.threadInfo,
    threadGroup.value
  );

  graphUpdater = new FullGraphUpdater(flamegraphClient, false);
  flamegraphTooltip = FlamegraphTooltipFactory.create(eventCode, false, false);

  // Delayed the initialization of the graphUpdater to ensure that the modal is fully rendered
  setTimeout(() => {
    graphUpdater.initialize();
  }, 200);

  // Set the event code first
  selectedEventCode.value = eventCode;

  // Then set the flag to show the dialog
  // The watcher will take care of showing the modal
  showFlamegraphDialog.value = true;
};

function createContextMenuItems() {
  const items = [];

  if (props.threadCommon.containsWallClock) {
    items.push({
      label: 'Wall-Clock',
      command: () => {
        showFlamegraph('profiler.WallClockSample');
      }
    });
  }

  if (props.threadRow.parked.length > 0) {
    items.push({
      label: 'Thread Park',
      command: () => {
        showFlamegraph('jdk.ThreadPark');
      }
    });
  }

  if (props.threadRow.sleep.length > 0) {
    items.push({
      label: 'Thread Sleep',
      command: () => {
        showFlamegraph('jdk.ThreadSleep');
      }
    });
  }

  if (props.threadRow.blocked.length > 0) {
    items.push({
      label: 'Monitor Blocked (Synchronized)',
      command: () => {
        showFlamegraph('jdk.JavaMonitorEnter');
      }
    });
  }

  if (props.threadRow.waiting.length > 0) {
    items.push({
      label: 'Monitor Wait',
      command: () => {
        showFlamegraph('jdk.JavaMonitorWait');
      }
    });
  }

  if (props.threadRow.socketRead.length > 0) {
    items.push({
      label: 'Socket Read',
      command: () => {
        showFlamegraph('jdk.SocketRead');
      }
    });
  }

  if (props.threadRow.socketWrite.length > 0) {
    items.push({
      label: 'Socket Write',
      command: () => {
        showFlamegraph('jdk.SocketWrite');
      }
    });
  }

  if (props.threadRow.fileRead.length > 0) {
    items.push({
      label: 'File Read',
      command: () => {
        showFlamegraph('jdk.FileRead');
      }
    });
  }

  if (props.threadRow.fileWrite.length > 0) {
    items.push({
      label: 'File Write',
      command: () => {
        showFlamegraph('jdk.FileWrite');
      }
    });
  }

  return items;
}
</script>

<template>
  <div class="thread-container">
    <div class="thread-card">
      <div class="thread-header">
        <div class="thread-actions">
          <button
            class="action-btn flame-btn"
            type="button"
            :title="collapsed ? 'Show flamegraph for all threads in the group' : 'Show flamegraph'"
            @click="toggleFlamegraphMenu"
          >
            <i class="bi bi-fire"></i>
          </button>
          <button
            class="action-btn info-btn"
            type="button"
            :title="collapsed ? 'Group information' : 'Thread information'"
            @click="openInfoModal"
          >
            <i class="bi bi-question-circle"></i>
          </button>
          <button
            v-if="presentCategories.length > 0"
            class="action-btn split-btn"
            type="button"
            :aria-pressed="breakdownOpen"
            :title="
              breakdownOpen
                ? 'Hide the breakdown by event type'
                : 'Split into one lane per event type'
            "
            @click="toggleBreakdown"
          >
            <i class="bi bi-distribute-vertical"></i>
          </button>
        </div>
        <button
          v-if="collapsed"
          class="thread-disclosure"
          type="button"
          :aria-expanded="expanded"
          :title="expanded ? 'Collapse threads' : 'Show threads'"
          @click="$emit('toggle')"
        >
          <i class="bi" :class="expanded ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
        </button>
        <h6 class="thread-title" :title="threadInfo.name">{{ threadInfo.name }}</h6>
        <Badge v-if="collapsed" :value="`${threadCount} threads`" variant="primary" size="xs" />
      </div>
      <div class="thread-content">
        <div :id="canvasId" class="thread-canvas"></div>

        <!--
          One sub-lane per event type the thread actually has. Each is the same renderer as the lane
          above, restricted to a single category, so it keeps the same geometry and the same hover
          tooltip — and being the same width, it lines up with the lane in time.
        -->
        <template v-if="breakdownOpen">
          <div class="breakdown-header">
            <span>Breakdown by event type</span>
            <span class="breakdown-summary">
              {{ presentCategories.length }} of {{ THREAD_CATEGORIES.length }} types present
            </span>
          </div>
          <div class="breakdown-list">
            <div v-for="category in presentCategories" :key="category.state" class="breakdown-item">
              <div class="breakdown-info">
                <span
                  class="breakdown-swatch"
                  :style="{ 'background-color': category.color }"
                ></span>
                <span class="breakdown-name">{{ categoryLabel(category) }}</span>
                <span class="breakdown-stats">
                  <span>
                    <b>{{ eventCount(category.periods(threadRow)).toLocaleString() }}</b> events
                  </span>
                  <span :title="coverageTitle">{{
                    coverageLabel(category.periods(threadRow))
                  }}</span>
                </span>
              </div>
              <div :id="categoryCanvasId(category)" class="thread-canvas"></div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>

  <div v-if="showFlameMenu" class="flamegraph-menu shadow-sm" :style="flameMenuPosition">
    <div class="menu-header">
      <button class="menu-close" @click="showFlameMenu = false">
        <i class="bi bi-x"></i>
      </button>
    </div>
    <div v-if="contextMenuItems.length === 0" class="menu-item disabled">
      No flamegraph data available
    </div>
    <div
      v-for="(item, index) in contextMenuItems"
      :key="index"
      class="menu-item"
      @click="executeMenuItem(item)"
    >
      {{ item.label }}
    </div>
  </div>

  <!-- Thread Information Modal -->
  <div v-if="showInfoModal" class="thread-info-overlay" @click="showInfoModal = false">
    <div class="modal-container tooltip-style-modal" @click.stop>
      <div
        class="tooltip-header d-flex justify-content-between align-items-center"
        style="padding: 0.75rem"
      >
        <h5 class="m-0 text-dark fw-bold text-truncate" style="max-width: 85%">
          {{ threadInfo.name }}
        </h5>
        <button class="modal-close" @click="showInfoModal = false">
          <i class="bi bi-x"></i>
        </button>
      </div>

      <div class="section-header px-3 py-2 bg-white border-bottom border-light">
        <span class="section-title fw-semibold">{{
          collapsed ? 'Group Details' : 'Thread Details'
        }}</span>
      </div>

      <!-- A collapsed lane has no ids of its own, so it is described by its group instead. The
           counts below already cover every thread behind it — they are the lane's own bands. -->
      <div class="tooltip-content">
        <div class="tooltip-row d-flex px-3 py-2">
          <span class="field-name text-secondary fw-medium">Name:</span>
          <span class="field-value text-dark">{{ threadInfo.name }}</span>
        </div>
        <template v-if="collapsed">
          <div class="tooltip-row d-flex px-3 py-2">
            <span class="field-name text-secondary fw-medium">Threads:</span>
            <span class="field-value text-dark">{{ threadCount }}</span>
          </div>
        </template>
        <template v-else>
          <div class="tooltip-row d-flex px-3 py-2">
            <span class="field-name text-secondary fw-medium">Java ID:</span>
            <span class="field-value text-dark">{{ threadInfo.javaId }}</span>
          </div>
          <div class="tooltip-row d-flex px-3 py-2">
            <span class="field-name text-secondary fw-medium">OS ID:</span>
            <span class="field-value text-dark">{{ threadInfo.osId }}</span>
          </div>
        </template>
      </div>

      <!-- Only the types this thread actually has, coloured and labelled the way the lane draws
           them. Restating the palette here is how the modal drifted onto colours that matched
           nothing on the canvas. -->
      <div
        v-for="category in presentCategories"
        :key="category.state"
        class="tooltip-category d-flex align-items-center px-3 py-2 bg-light"
      >
        <div
          class="color-indicator me-3"
          :style="{ width: '10px', height: '10px', backgroundColor: category.color }"
        ></div>
        <div class="d-flex justify-content-between w-100">
          <span class="category-name fw-medium">{{ categoryLabel(category) }}</span>
          <span class="event-count text-muted small"
            >{{ eventCount(category.periods(threadRow)) }} event{{
              eventCount(category.periods(threadRow)) !== 1 ? 's' : ''
            }}</span
          >
        </div>
      </div>

      <!-- The threads behind the lane. They all share a name, so each is listed by its ids and by
           how much of the lane it accounts for. -->
      <template v-if="collapsed">
        <div class="section-header px-3 py-2 bg-white border-top border-bottom border-light">
          <span class="section-title fw-semibold">Threads</span>
        </div>

        <div v-if="membersError" class="member-message px-3 py-2 text-danger small">
          {{ membersError }}
        </div>

        <div class="member-list">
          <div
            v-for="member in members"
            :key="memberKey(member)"
            class="member-row d-flex px-3 py-2"
          >
            <span class="member-ids text-dark">{{ memberIds(member) }}</span>
            <span class="member-events text-muted small ms-auto"
              >{{ member.eventsCount }} event{{ member.eventsCount !== 1 ? 's' : '' }}</span
            >
          </div>
        </div>

        <div class="member-message d-flex align-items-center px-3 py-2">
          <span class="text-muted small">{{ memberSummary }}</span>
          <button
            v-if="hasMoreMembers"
            class="btn btn-sm btn-outline-primary ms-auto"
            type="button"
            :disabled="loadingMembers"
            @click="loadMembers"
          >
            <span
              v-if="loadingMembers"
              class="spinner-border spinner-border-sm me-2"
              role="status"
              aria-hidden="true"
            ></span>
            Show {{ matchedMembers - members.length }} more
          </button>
        </div>
      </template>

      <div class="modal-footer">
        <button type="button" class="btn btn-sm btn-secondary" @click="showInfoModal = false">
          Close
        </button>
      </div>
    </div>
  </div>

  <!-- Modal for events that contain StackTrace field -->
  <GenericModal
    v-model:show="showFlamegraphDialog"
    :modal-id="flamegraphModalId"
    :title="selectedEventCode"
    size="fullscreen"
    :show-footer="false"
  >
    <div v-if="showFlamegraphDialog" style="padding: 0.75rem">
      <SearchBarComponent :graph-updater="graphUpdater" :with-timeseries="true" />
      <TimeSeriesChart
        :graph-updater="graphUpdater"
        :primary-axis-type="
          TimeseriesEventAxeFormatter.resolveAxisFormatter(useWeightValue, selectedEventCode)
        "
        :visible-minutes="60"
        :zoom-enabled="true"
        time-unit="seconds"
      />
      <FlamegraphComponent
        :with-timeseries="true"
        :use-weight="useWeightValue"
        :use-guardian="null"
        :scrollable-wrapper-class="flamegraphModalId"
        :flamegraph-tooltip="flamegraphTooltip"
        :graph-updater="graphUpdater"
        @loaded="scrollToTop"
      />
    </div>
  </GenericModal>
</template>

<style scoped>
.thread-container {
  text-align: left;
  padding: 4px 0;
}

.thread-card {
  background: white;
  border: 1px solid var(--color-border);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.15s ease;
}

.thread-card:hover {
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
}

.thread-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background-color: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

/* Opens a collapsed lane into the threads behind it. */
.thread-disclosure {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-text-muted);
  cursor: pointer;
  border-radius: var(--radius-sm);
}

.thread-disclosure:hover {
  background-color: var(--color-border);
  color: var(--color-text);
}

.thread-disclosure:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.thread-actions {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-right: 6px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: transparent;
  border: none;
  height: 22px;
  width: 22px;
  padding: 0;
  font-size: 0.8rem;
  transition: all 0.15s ease;
  cursor: pointer;
}

.flame-btn {
  color: var(--color-indigo-text);
}

.flame-btn:hover {
  background-color: rgba(63, 81, 181, 0.1);
}

.info-btn {
  color: var(--color-info);
}

.info-btn:hover {
  background-color: rgba(33, 150, 243, 0.1);
}

.thread-title {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-grey-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-grow: 1;
}

.thread-content {
  padding: 6px;
}

/*
 * Positioned so the hover pointer inside it lands on the lane. Without this the pointer is placed
 * against whichever distant ancestor is `offsetParent`, which drifts once a lane sits inside the
 * breakdown's nested flex boxes.
 */
.thread-canvas {
  width: 100%;
  position: relative;
}

.split-btn {
  color: var(--color-text-muted);
}

.split-btn:hover {
  background-color: var(--color-border);
  color: var(--color-text);
}

.split-btn[aria-pressed='true'] {
  background-color: var(--color-primary-bg);
  color: var(--color-indigo-text);
}

/*
 * The breakdown. Sub-lanes sit in the same padded content box as the main lane, at the same width,
 * so a band on a sub-lane is directly under the same moment on the lane above it.
 */
.breakdown-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 6px;
  padding: 5px 0;
  border-top: 1px solid var(--color-border);
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text);
}

.breakdown-summary {
  font-weight: 400;
  text-transform: none;
  letter-spacing: 0;
  color: var(--color-text-muted);
}

.breakdown-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 6px;
}

.breakdown-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.breakdown-info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  line-height: 1.3;
  min-width: 0;
}

.breakdown-swatch {
  width: 10px;
  height: 10px;
  flex: none;
}

.breakdown-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-grey-text);
}

.breakdown-stats {
  display: flex;
  gap: 10px;
  margin-left: auto;
  flex: none;
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-muted);
}

.breakdown-stats b {
  font-weight: 600;
  color: var(--color-grey-text);
}

/* Custom flamegraph menu */
.flamegraph-menu {
  position: fixed;
  background: white;
  border: 1px solid var(--color-border);
  min-width: 170px;
  padding: 0 0 3px 0;
  z-index: 9999;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
  animation: fadeIn 0.15s ease;
}

.flamegraph-menu .menu-header {
  display: flex;
  justify-content: flex-end;
  padding: 3px 6px;
  border-bottom: 1px solid var(--color-border);
}

.flamegraph-menu .menu-close {
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  width: 18px;
  height: 18px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.9rem;
  transition: color 0.15s ease;
}

.flamegraph-menu .menu-close:hover {
  color: var(--color-text);
  background-color: rgba(108, 117, 125, 0.1);
}

.flamegraph-menu .menu-item {
  padding: 5px 10px;
  font-size: 0.75rem;
  color: var(--color-text);
  cursor: pointer;
  transition: all 0.1s ease;
}

.flamegraph-menu .menu-item:hover {
  background-color: rgba(63, 81, 181, 0.08);
  color: var(--color-indigo-text);
}

.flamegraph-menu .menu-item:active {
  background-color: rgba(63, 81, 181, 0.15);
}

.flamegraph-menu .menu-item.disabled {
  color: var(--color-text-light);
  font-style: italic;
  cursor: default;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* The thread-details overlay. Deliberately not named `modal-overlay`: that class also sits on
   GenericModal's root, which a scoped rule here would reach and restyle. */
.thread-info-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
  animation: modalFadeIn 0.2s ease;
  backdrop-filter: blur(2px);
}

.modal-container {
  background-color: white;
  border: 1px solid var(--color-border);
  width: 90%;
  max-width: 480px;
  max-height: 80vh;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  animation: modalSlideIn 0.2s ease;
  overflow: hidden;
  overflow-y: auto;
}

.tooltip-style-modal {
  border: 1px solid var(--color-border);
  max-width: 420px;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.tooltip-style-modal .tooltip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tooltip-style-modal .modal-close {
  position: absolute;
  right: 8px;
  top: 8px;
}

.modal-close {
  background: transparent;
  border: none;
  font-size: 1.1rem;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  transition: all 0.15s ease;
}

.modal-close:hover {
  background-color: rgba(108, 117, 125, 0.12);
  color: var(--color-dark);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  padding: 8px 12px;
  border-top: 1px solid var(--color-border);
  background-color: var(--color-light);
}

.tooltip-style-modal .modal-footer {
  background-color: var(--color-light);
  margin-top: 0;
  padding: 6px 12px;
}

/* Section header for Thread Details */
.section-header {
  font-size: 0.8rem;
  color: var(--color-grey-text);
  letter-spacing: 0.01em;
}

/* The threads behind a collapsed lane. A pool can hold hundreds, so the list scrolls on its own
   instead of pushing the footer off the modal. */
.member-list {
  max-height: 220px;
  overflow-y: auto;
}

.member-row {
  font-size: 0.8rem;
  border-bottom: 1px solid var(--color-border-light);
}

.member-row:last-child {
  border-bottom: none;
}

.member-ids {
  font-variant-numeric: tabular-nums;
}

.member-message {
  font-size: 0.8rem;
  border-top: 1px solid var(--color-border-light);
}

.section-title {
  font-size: 0.8rem;
}

/* Info cards */
.info-card {
  background-color: var(--color-white);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.info-card-header {
  background-color: var(--color-light);
  padding: 8px 12px;
  font-size: 0.85rem;
  color: var(--color-indigo-text);
  font-weight: 600;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
}

.info-card-body {
  padding: 8px 12px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 0;
  border-bottom: 1px solid var(--color-border-row);
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-weight: 500;
  color: var(--color-text);
  font-size: 0.75rem;
}

.info-value {
  font-size: 0.75rem;
  color: var(--color-dark);
  text-align: right;
}

@keyframes modalFadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes modalSlideIn {
  from {
    transform: translateY(-20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}
</style>
