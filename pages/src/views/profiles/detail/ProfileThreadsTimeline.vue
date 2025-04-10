<template>
  <div class="threads-timeline-container">
    <div class="d-flex align-items-center">
      <div class="input-group input-group-sm search-container me-3 flex-grow-1">
        <span class="input-group-text">
          <i class="bi bi-search search-icon"></i>
        </span>
        <input 
          type="text" 
          class="form-control search-input" 
          placeholder="Filter threads..." 
          v-model="fulltextFilter"
          @input="onFilterChange($event.target.value)"
        />
        <button 
          v-if="fulltextFilter" 
          class="btn btn-outline-secondary clear-btn" 
          type="button"
          @click="clearFilter">
          <i class="bi bi-x-lg"></i>
        </button>
      </div>
      
      <div class="btn-group btn-group-sm mini-sort-buttons">
        <button v-for="(option, index) in sortingTypes" 
                :key="index" 
                type="button" 
                class="btn compact-btn" 
                :class="[
                  selectedSorting === option 
                    ? 'btn-primary active' 
                    : 'btn-outline-primary'
                ]"
                @click="sortingChanged({ value: option })"
                :title="`Sort by ${option}`">
          {{ option }}
        </button>
      </div>
      <button 
        type="button" 
        class="btn icon-info-btn ms-2" 
        @click="infoDialogVisible = true"
        title="Thread Information">
        <i class="bi bi-info-circle"></i>
      </button>
    </div>

    <div class="timeline-divider">
      <div class="divider-line"></div>
      <div class="divider-text">THREADS</div>
      <div class="divider-line"></div>
    </div>

    <div class="thread-components-container" :key="forceRenderThreads">
      <div class="thread-row-wrapper" v-for="(threadRow, index) in threadRows" :key="index">
        <ThreadComponent 
          v-if="threadRow.threadInfo.name.includes(fulltextFilterAfterTimeout)"
          :index="index"
          :project-id="projectId"
          :primary-profile-id="profileId"
          :thread-common="threadCommon as ThreadCommon"
          :thread-row="threadRow"/>
      </div>
      
      <div v-if="filteredThreadCount === 0" class="no-threads-message">
        <i class="bi bi-exclamation-circle"></i>
        No threads match the current filter
      </div>
    </div>
  </div>

  <!-- Bootstrap Modal -->
  <div class="modal fade" 
       :class="{ 'show d-block': infoDialogVisible }" 
       :style="{ 'background-color': infoDialogVisible ? 'rgba(0, 0, 0, 0.5)' : 'transparent' }"
       tabindex="-1" 
       role="dialog" 
       aria-labelledby="threadInfoModalLabel" 
       :aria-hidden="!infoDialogVisible">
    <div class="modal-dialog modal-lg modal-dialog-scrollable" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="threadInfoModalLabel">
            <i class="bi bi-info-circle text-info me-2"></i>
            Thread View Information
          </h5>
          <button type="button" class="btn-close" @click="infoDialogVisible = false" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <div class="info-section">
            <h5 class="section-title">Timeline</h5>
            <ul class="info-list">
              <li>Timeline contains green parts representing the lifespan of the threads.</li>
              <li>Other events fits into the thread's lifespan.</li>
              <li>Entire timeline is divided into pixels. For longer timelines, multiple events can be represented by a single pixel.</li>
              <li>One pixel of the timeline can keep multiple events of same type (the first one shows details), or different types.</li>
            </ul>
          </div>

          <div class="info-section mt-4">
            <h5 class="section-title">Event Types</h5>
            <div class="table-responsive">
              <table class="table table-hover event-type-table">
                <tbody>
                  <tr>
                    <td class="color-cell">
                      <div class="event-color-box" :style="{'background-color': ThreadRow.lifespanColor}"></div>
                    </td>
                    <td>Lifespan of the thread, time between Thread Start and End</td>
                  </tr>
                  <tr>
                    <td class="color-cell">
                      <div class="event-color-box" :style="{'background-color': ThreadRow.parkedColor}"></div>
                    </td>
                    <td>Parking of the thread: <b>LockSupport#park()</b> (e.g. parking threads in Thread Pools)</td>
                  </tr>
                  <tr>
                    <td class="color-cell">
                      <div class="event-color-box" :style="{'background-color': ThreadRow.sleepColor}"></div>
                    </td>
                    <td>Threads Sleep, emitted by <b>Thread#sleep()</b></td>
                  </tr>
                  <tr>
                    <td class="color-cell">
                      <div class="event-color-box" :style="{'background-color': ThreadRow.waitingColor}"></div>
                    </td>
                    <td>Thread Wait, emitted by <b>Thread#wait()</b></td>
                  </tr>
                  <tr>
                    <td class="color-cell">
                      <div class="event-color-box" :style="{'background-color': ThreadRow.blockedColor}"></div>
                    </td>
                    <td>Blocked thread, caused by <b>MonitorEnter</b> (e.g. synchronized)</td>
                  </tr>
                  <tr>
                    <td class="color-cell">
                      <div class="event-color-box" :style="{'background-color': ThreadRow.socketReadColor}"></div>
                    </td>
                    <td>Blocking reads from a Socket (e.g. <b>SocketInputStream#read</b>)</td>
                  </tr>
                  <tr>
                    <td class="color-cell">
                      <div class="event-color-box" :style="{'background-color': ThreadRow.socketWriteColor}"></div>
                    </td>
                    <td>Blocking writes to a Socket (e.g. <b>SocketOutputStream#write</b>)</td>
                  </tr>
                  <tr>
                    <td class="color-cell">
                      <div class="event-color-box" :style="{'background-color': ThreadRow.fileReadColor}"></div>
                    </td>
                    <td>Blocking reads from a File (e.g. <b>FileInputStream#read</b>)</td>
                  </tr>
                  <tr>
                    <td class="color-cell">
                      <div class="event-color-box" :style="{'background-color': ThreadRow.fileWriteColor}"></div>
                    </td>
                    <td>Blocking writes to a File (e.g. <b>FileOutputStream#write</b>)</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="infoDialogVisible = false">
            <i class="bi bi-x-circle me-1"></i>
            Close
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {useRoute} from "vue-router";
import {onBeforeMount, ref, computed} from "vue";
import ThreadService from "@/services/ThreadService";
import ThreadComponent from "@/components/ThreadComponent.vue";
import ThreadCommon from "@/services/thread/model/ThreadCommon";
import ThreadRowData from "@/services/thread/model/ThreadRowData";
import Konva from "konva";
import ThreadRow from "@/services/thread/ThreadRow";

const route = useRoute()

const EVENT_COUNT_COMPARATOR = (a: ThreadRowData, b: ThreadRowData) => b.eventsCount - a.eventsCount
const LIFESPAN_COMPARATOR = (a: ThreadRowData, b: ThreadRowData) => b.totalDuration - a.totalDuration
const ALPHABETICAL_COMPARATOR = (a: ThreadRowData, b: ThreadRowData) => a.threadInfo.name.localeCompare(b.threadInfo.name)

const projectId = route.params.projectId as string
const profileId = route.params.profileId as string

const threadRows = ref<ThreadRowData[]>()
const threadCommon = ref<ThreadCommon>()

const fulltextFilter = ref<string>('')
const fulltextFilterAfterTimeout = ref<string>('')
const selectedSorting = ref<string>('Event Count')
const sortingTypes = ref<string[]>(['Event Count', 'Lifespan', 'Alphabetically'])

const forceRenderThreads = ref<number>(0)

const infoDialogVisible = ref<boolean>(false)

const filteredThreadCount = computed(() => {
  if (!threadRows.value) return 0
  return threadRows.value.filter(row => row.threadInfo.name.includes(fulltextFilterAfterTimeout.value)).length
})

let filterTimeout: ReturnType<typeof setTimeout>

let threadService;

onBeforeMount(() => {
  // Too many layers, it spams the console
  Konva.showWarnings = false;

  threadService = new ThreadService(projectId, profileId)

  threadService.list()
      .then((response) => {
        threadRows.value = sortThreadRows(selectedSorting.value, response.rows)
        threadCommon.value = response.common
      })
});

function sortThreadRows(sortingType: string, threadRows: ThreadRowData[] | undefined): ThreadRowData[] | undefined {
  if (threadRows != undefined) {
    const comparator = selectComparator(sortingType)
    return threadRows.sort(comparator)
  } else {
    return threadRows
  }
}

function selectComparator(sortingType: string) {
  if (sortingType === 'Event Count') {
    return EVENT_COUNT_COMPARATOR
  } else if (sortingType === 'Lifespan') {
    return LIFESPAN_COMPARATOR
  } else if (sortingType === 'Alphabetically') {
    return ALPHABETICAL_COMPARATOR
  }
}

function onFilterChange(event: string) {
  clearTimeout(filterTimeout)
  filterTimeout = setTimeout(() => {
    fulltextFilterAfterTimeout.value = event
  }, 750)
}

function clearFilter() {
  fulltextFilter.value = ''
  fulltextFilterAfterTimeout.value = ''
}

function sortingChanged(event: any) {
  selectedSorting.value = event.value
  threadRows.value = sortThreadRows(event.value, threadRows.value)

  // Trigger re-render of the threads
  forceRenderThreads.value++
}
</script>

<style scoped>
.threads-timeline-container {
  display: flex;
  flex-direction: column;
  padding: 0;
  margin-bottom: 2rem;
}

.timeline-divider {
  display: flex;
  align-items: center;
  margin: 1.5rem 0;
  padding: 0 1.5rem;
}

.divider-line {
  flex-grow: 1;
  height: 1px;
  background-color: rgba(0, 0, 0, 0.1);
}

.divider-text {
  padding: 0 1rem;
  font-size: 0.75rem;
  font-weight: 700;
  color: #6c757d;
  letter-spacing: 1px;
}

.thread-row-wrapper {
  margin-bottom: 0.5rem;
  border-radius: 4px;
  transition: transform 0.15s ease;
}

.no-threads-message {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem 0;
  color: #6c757d;
  font-size: 1rem;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.no-threads-message i {
  margin-right: 0.5rem;
  font-size: 1.25rem;
}

.btn-group {
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
  border-radius: 0.25rem;
  display: flex;
}

.mini-sort-buttons {
  width: auto;
}

.btn-group .btn {
  transition: all 0.2s ease;
  font-weight: 500;
  font-size: 0.75rem;
  padding: 0.375rem 0.5rem;
  line-height: 1.4;
}

.compact-btn {
  min-width: 0;
  white-space: nowrap;
}

.btn-group .btn.active {
  font-weight: 600;
}

/* Search input styles */
.search-container {
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
  border-radius: 0.25rem;
  overflow: hidden;
}

.search-container .input-group-text {
  background-color: #fff;
  border-right: none;
  padding: 0.375rem 0.5rem;
  display: flex;
  align-items: center;
}

.search-icon {
  font-size: 0.85rem;
  color: #6c757d;
}

.search-input {
  border-left: none;
  font-size: 0.875rem;
  height: auto;
  padding-top: 0.375rem;
  padding-bottom: 0.375rem;
}

.search-input:focus {
  box-shadow: none;
  border-color: #ced4da;
}

.clear-btn {
  border-color: #ced4da;
  border-left: none;
  background-color: #fff;
  padding: 0 0.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.clear-btn:hover {
  background-color: #f8f9fa;
}

.clear-btn i {
  font-size: 0.75rem;
}

/* Info button styles */
.icon-info-btn {
  padding: 0.375rem 0.5rem;
  color: #17a2b8;
  background: transparent;
  border: none;
  box-shadow: none;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.icon-info-btn i {
  font-size: 1.1rem;
}

.icon-info-btn:hover {
  color: #138496;
  background-color: rgba(23, 162, 184, 0.1);
}

/* Timeline key styles */
.timeline-state {
  width: 20px;
  height: 12px;
  border-radius: 2px;
}

.timeline-state.runnable {
  background-color: #28a745;
}

.timeline-state.blocked {
  background-color: #dc3545;
}

.timeline-state.waiting {
  background-color: #ffc107;
}

.timeline-state.timed-waiting {
  background-color: #fd7e14;
}

.timeline-state.terminated {
  background-color: #6c757d;
}

/* Timeline scale styles */
.timeline-scale {
  height: 30px;
  position: relative;
  background-color: #f8f9fa;
  border-bottom: 1px solid #dee2e6;
  margin-right: 10px;
}

.timeline-scale-container {
  position: relative;
  height: 100%;
  margin-left: 200px; /* Same width as thread info column */
}

.timeline-ruler {
  position: relative;
  height: 100%;
  width: 100%;
}

.timeline-tick {
  position: absolute;
  height: 10px;
  width: 1px;
  background-color: #adb5bd;
  top: 0;
}

.timeline-tick-label {
  position: absolute;
  top: 12px;
  transform: translateX(-50%);
  font-size: 0.7rem;
  color: #6c757d;
}

/* Thread timeline styles */
.thread-timelines-container {
  max-height: 500px;
  overflow-y: auto;
}

.thread-row {
  display: flex;
  height: 30px;
  margin-bottom: 5px;
  align-items: center;
}

.thread-info {
  width: 200px;
  padding-right: 10px;
  position: sticky;
  left: 0;
  background-color: white;
  z-index: 2;
}

.thread-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 0.85rem;
}

.thread-timeline {
  flex-grow: 1;
  height: 100%;
  position: relative;
  border-radius: 3px;
  background-color: #f8f9fa;
}

.thread-period {
  position: absolute;
  height: 100%;
  top: 0;
  border-radius: 3px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.thread-period:hover {
  opacity: 0.8;
}

.state-runnable {
  background-color: #28a745;
}

.state-blocked {
  background-color: #dc3545;
}

.state-waiting {
  background-color: #ffc107;
}

.state-timed-waiting {
  background-color: #fd7e14;
}

.state-terminated {
  background-color: #6c757d;
}

/* Modal styles */
.modal-backdrop {
  background-color: rgba(0, 0, 0, 0.5);
}

/* Modal styles */
.modal-content {
  border: none;
  border-radius: 8px;
  box-shadow: 0 11px 15px -7px rgba(0, 0, 0, 0.2), 0 24px 38px 3px rgba(0, 0, 0, 0.14), 0 9px 46px 8px rgba(0, 0, 0, 0.12);
}

.modal-header {
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  padding: 1rem 1.5rem;
}

.modal-title {
  display: flex;
  align-items: center;
  font-weight: 600;
}

.modal-body {
  padding: 1.5rem;
}

.modal-footer {
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  padding: 1rem 1.5rem;
}

.info-section {
  margin-bottom: 1.5rem;
}

.section-title {
  font-weight: 600;
  margin-bottom: 1rem;
  color: #495057;
  font-size: 1.1rem;
}

.info-list {
  padding-left: 1.5rem;
  margin-bottom: 0;
}

.info-list li {
  margin-bottom: 0.5rem;
  line-height: 1.5;
}

.event-type-table {
  margin-bottom: 0;
}

.color-cell {
  width: 60px;
  vertical-align: middle;
  text-align: center;
}

.event-color-box {
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 3px;
  display: inline-block;
  border: 1px solid rgba(0, 0, 0, 0.1);
  margin: 0 auto;
}

pre {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.8125rem;
  white-space: pre-wrap;
  word-break: break-all;
  margin-bottom: 0;
  max-height: 150px;
  overflow-y: auto;
}
</style>
