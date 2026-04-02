<template>
  <div class="threads-timeline-container">
    <PageHeader
      title="Threads Timeline"
      description="View and analyze thread activities over time"
      icon="bi-clock-history"
    />
    <div class="d-flex align-items-center mb-3">
      <div class="input-group search-container me-3" style="max-width: 60%">
        <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
        <input
          type="text"
          class="form-control search-input"
          placeholder="Filter threads..."
          v-model="fulltextFilter"
          @input="onFilterChange(($event.target as HTMLInputElement).value)"
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

    <div class="thread-components-container" :key="forceRenderThreads">
      <div class="thread-row-wrapper" v-for="(threadRow, index) in threadRows" :key="index">
        <ThreadComponent
          v-if="threadRow.threadInfo.name.includes(fulltextFilterAfterTimeout)"
          :index="index"
          :project-id="projectId"
          :primary-profile-id="profileId"
          :thread-common="threadCommon as ThreadCommon"
          :thread-row="threadRow"
        />
      </div>

      <div v-if="filteredThreadCount === 0" class="no-threads-message">
        <i class="bi bi-exclamation-circle"></i>
        No threads match the current filter
      </div>
    </div>
  </div>

  <GenericModal
    modal-id="threadInfoModal"
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
            One pixel of the timeline can keep multiple events of same type (the first one shows
            details), or different types.
          </li>
        </ul>
      </div>

      <div class="info-section mt-4">
        <h5 class="section-title">Event Types</h5>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0 event-type-table">
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
          </table>
        </div>
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
import Konva from 'konva';
import ThreadRow from '@/services/thread/ThreadRow';
import PageHeader from '@/components/layout/PageHeader.vue';
import GenericModal from '@/components/GenericModal.vue';
import type { PropType } from 'vue';
import '@/styles/shared-components.css';

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

const EVENT_COUNT_COMPARATOR = (a: ThreadRowData, b: ThreadRowData) =>
  b.eventsCount - a.eventsCount;
const LIFESPAN_COMPARATOR = (a: ThreadRowData, b: ThreadRowData) =>
  b.totalDuration - a.totalDuration;
const ALPHABETICAL_COMPARATOR = (a: ThreadRowData, b: ThreadRowData) =>
  a.threadInfo.name.localeCompare(b.threadInfo.name);

const profileId = route.params.profileId as string;

const threadRows = ref<ThreadRowData[]>();
const threadCommon = ref<ThreadCommon>();

const fulltextFilter = ref<string>('');
const fulltextFilterAfterTimeout = ref<string>('');
const selectedSorting = ref<string>('Event Count');
const sortingTypes = ref<string[]>(['Event Count', 'Lifespan', 'Alphabetically']);

const forceRenderThreads = ref<number>(0);

const infoDialogVisible = ref<boolean>(false);

const filteredThreadCount = computed(() => {
  if (!threadRows.value) return 0;
  return threadRows.value.filter(row =>
    row.threadInfo.name.includes(fulltextFilterAfterTimeout.value)
  ).length;
});

let filterTimeout: ReturnType<typeof setTimeout>;

let threadService;

onBeforeMount(() => {
  // Too many layers, it spams the console
  Konva.showWarnings = false;

  threadService = new ProfileThreadClient(profileId);

  threadService.list().then(response => {
    threadRows.value = sortThreadRows(selectedSorting.value, response.rows);
    threadCommon.value = response.common;
  });
});

function sortThreadRows(
  sortingType: string,
  threadRows: ThreadRowData[] | undefined
): ThreadRowData[] | undefined {
  if (!threadRows) return undefined;

  return [...threadRows].sort((a, b) => {
    switch (sortingType) {
      case 'Event Count':
        return EVENT_COUNT_COMPARATOR(a, b);
      case 'Lifespan':
        return LIFESPAN_COMPARATOR(a, b);
      case 'Alphabetically':
        return ALPHABETICAL_COMPARATOR(a, b);
      default:
        return 0;
    }
  });
}

function sortingChanged(newSorting: { value: string }) {
  selectedSorting.value = newSorting.value;
  if (threadRows.value) {
    threadRows.value = sortThreadRows(newSorting.value, threadRows.value);
    forceRenderThreads.value++;
  }
}

function onFilterChange(newFilter: string) {
  fulltextFilterAfterTimeout.value = '';
  clearTimeout(filterTimeout);

  if (newFilter === '') {
    fulltextFilterAfterTimeout.value = newFilter;
    return;
  }

  filterTimeout = setTimeout(() => {
    fulltextFilterAfterTimeout.value = newFilter;
  }, 300);
}

function clearFilter() {
  fulltextFilter.value = '';
  fulltextFilterAfterTimeout.value = '';
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

.no-threads-message {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
  background-color: var(--color-light);
  border: 1px solid var(--card-border-color);
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

.no-threads-message i {
  margin-right: 0.5rem;
  font-size: 1rem;
}

/* Modal styles */
.modal-content {
  border: 1px solid var(--card-border-color);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.modal-header {
  border-bottom: 1px solid var(--card-border-color);
  background-color: var(--color-light);
  padding: 10px 16px;
}

.modal-footer {
  border-top: 1px solid var(--card-border-color);
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
