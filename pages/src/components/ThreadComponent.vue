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
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from 'vue';
import ThreadRowData from "@/services/thread/model/ThreadRowData";
import {useRoute} from "vue-router";
import { useNavigation } from '@/composables/useNavigation';
import ThreadCommon from "@/services/thread/model/ThreadCommon";
import ThreadRow from "@/services/thread/ThreadRow";
import PrimaryFlamegraphClient from "@/services/flamegraphs/client/PrimaryFlamegraphClient";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/services/flamegraphs/tooltips/FlamegraphTooltipFactory";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import FullGraphUpdater from "@/services/flamegraphs/updater/FullGraphUpdater";
import GraphType from "@/services/flamegraphs/GraphType.ts";
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import * as bootstrap from 'bootstrap';

const props = defineProps<{
  index: number,
  projectId: string,
  primaryProfileId: string,
  threadCommon: ThreadCommon,
  threadRow: ThreadRowData
}>()

const route = useRoute()
const { workspaceId } = useNavigation();

const selectedEventCode = ref()
const showFlameMenu = ref(false)
const flameMenuPosition = ref({
  top: '0px',
  left: '0px'
})

const contextMenuItems = createContextMenuItems()

const canvasId = ref(`thread-canvas-${props.index}`)

const showFlamegraphDialog = ref(false);
const showInfoModal = ref(false);

const threadInfo = props.threadRow.threadInfo

let threadRow: ThreadRow

let flamegraphTooltip: FlamegraphTooltip

let graphUpdater: GraphUpdater

/**
 * Determines whether to use weight based on the event type.
 * By default, use weight for allocation and blocking events.
 *
 * @param eventCode the event type code
 * @returns true if weight should be used, false otherwise
 */
function resolveWeight(eventCode: string | undefined): boolean {
  if (!eventCode) {
    return false
  }

  const isAllocationEvent =
    eventCode === 'jdk.ObjectAllocationInNewTLAB' ||
    eventCode === 'jdk.ObjectAllocationOutsideTLAB' ||
    eventCode === 'jdk.ObjectAllocationSample'

  const isBlockingEvent =
    eventCode === 'jdk.JavaMonitorEnter' ||
    eventCode === 'jdk.JavaMonitorWait' ||
    eventCode === 'jdk.ThreadPark'

  return isAllocationEvent || isBlockingEvent
}

const useWeightValue = computed(() => resolveWeight(selectedEventCode.value))

onMounted(() => {
  // Initialize thread row
  threadRow = new ThreadRow(props.threadCommon, props.threadRow, canvasId.value)
  threadRow.draw()

  // Initialize the Bootstrap modal after the DOM is ready
  nextTick(() => {
    const modalEl = document.getElementById('flamegraphModal')
    if (modalEl) {
      // We'll manually create and dispose of the modal
      // for better control over the behavior
      modalEl.addEventListener('hidden.bs.modal', () => {
        showFlamegraphDialog.value = false
      })

      // Add event listener to close button that might not work with data-bs-dismiss
      const closeButton = modalEl.querySelector('.btn-close')
      if (closeButton) {
        closeButton.addEventListener('click', closeModal)
      }
    }
  })
});

document.addEventListener("scroll", () => {
  if (threadRow != null) {
    threadRow.onWindowScroll()
  }

  // Close menu on scroll
  if (showFlameMenu.value) {
    showFlameMenu.value = false;
    document.addEventListener('scroll', () => showFlameMenu.value = false);
  }
})

const toggleFlamegraphMenu = (event: MouseEvent) => {
  const buttonRect = (event.target as Element).getBoundingClientRect();
  flameMenuPosition.value = {
    top: `${buttonRect.bottom + 5}px`,
    left: `${buttonRect.left}px`
  };
  showFlameMenu.value = !showFlameMenu.value;
}

const openInfoModal = () => {
  showInfoModal.value = true
}

const executeMenuItem = (item: any) => {
  showFlameMenu.value = false;
  item.command();
}

let modalInstance: bootstrap.Modal | null = null

// Function to close the modal
const closeModal = () => {
  if (modalInstance) {
    modalInstance.hide();
  }
  showFlamegraphDialog.value = false;
}

// Watch for changes to showFlamegraphDialog to control modal visibility
watch(showFlamegraphDialog, (isVisible) => {
  if (isVisible) {
    if (!modalInstance) {
      const modalEl = document.getElementById('flamegraphModal-' + props.threadRow.threadInfo.osId);
      if (modalEl) {
        modalInstance = new bootstrap.Modal(modalEl);
      }
    }

    if (modalInstance) {
      modalInstance.show();
    }
  } else {
    if (modalInstance) {
      modalInstance.hide();
    }
  }
});

// Clean up event listeners and modal when component is unmounted
onUnmounted(() => {
  if (modalInstance) {
    modalInstance.dispose();
    modalInstance = null;
  }

  // Remove global event listeners
  document.removeEventListener('hidden.bs.modal', () => {
  });
});

const showFlamegraph = (eventCode: string) => {
  if (!workspaceId.value) return;

  let flamegraphClient = new PrimaryFlamegraphClient(
      workspaceId.value,
      props.projectId,
      props.primaryProfileId,
      eventCode,
      true,
      null,
      false,
      false,
      false,
      props.threadRow.threadInfo
  )

  graphUpdater = new FullGraphUpdater(flamegraphClient, false)
  flamegraphTooltip = FlamegraphTooltipFactory.create(eventCode, false, false)

  // Delayed the initialization of the graphUpdater to ensure that the modal is fully rendered
  setTimeout(() => {
    graphUpdater.initialize()
  }, 200);

  // Set the event code first
  selectedEventCode.value = eventCode

  // Then set the flag to show the dialog
  // The watcher will take care of showing the modal
  showFlamegraphDialog.value = true
}

function createContextMenuItems() {
  let items = []

  if (props.threadCommon.containsWallClock) {
    items.push({
      label: 'Wall-Clock',
      command: () => {
        showFlamegraph("profiler.WallClockSample")
      }
    })
  }

  if (props.threadRow.parked.length > 0) {
    items.push({
      label: 'Thread Park',
      command: () => {
        showFlamegraph("jdk.ThreadPark")
      }
    })
  }

  if (props.threadRow.sleep.length > 0) {
    items.push({
      label: 'Thread Sleep',
      command: () => {
        showFlamegraph("jdk.ThreadSleep")
      }
    })
  }

  if (props.threadRow.blocked.length > 0) {
    items.push({
      label: 'Monitor Blocked (Synchronized)',
      command: () => {
        showFlamegraph("jdk.JavaMonitorEnter")
      }
    })
  }

  if (props.threadRow.waiting.length > 0) {
    items.push({
      label: 'Monitor Wait',
      command: () => {
        showFlamegraph("jdk.JavaMonitorWait")
      }
    })
  }

  if (props.threadRow.socketRead.length > 0) {
    items.push({
      label: 'Socket Read',
      command: () => {
        showFlamegraph("jdk.SocketRead")
      }
    })
  }

  if (props.threadRow.socketWrite.length > 0) {
    items.push({
      label: 'Socket Write',
      command: () => {
        showFlamegraph("jdk.SocketWrite")
      }
    })
  }

  if (props.threadRow.fileRead.length > 0) {
    items.push({
      label: 'File Read',
      command: () => {
        showFlamegraph("jdk.FileRead")
      }
    })
  }

  if (props.threadRow.fileWrite.length > 0) {
    items.push({
      label: 'File Write',
      command: () => {
        showFlamegraph("jdk.FileWrite")
      }
    })
  }

  return items
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
              title="Show flamegraph"
              @click="toggleFlamegraphMenu">
            <i class="bi bi-fire"></i>
          </button>
          <button
              class="action-btn info-btn"
              type="button"
              title="Thread information"
              @click="openInfoModal">
            <i class="bi bi-question-circle"></i>
          </button>
        </div>
        <h6 class="thread-title" :title="threadInfo.name">{{ threadInfo.name }}</h6>
      </div>
      <div class="thread-content">
        <div :id="canvasId" class="thread-canvas"></div>
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
    <div v-for="(item, index) in contextMenuItems" :key="index"
         class="menu-item"
         @click="executeMenuItem(item)">
      {{ item.label }}
    </div>
  </div>

  <!-- Thread Information Modal -->
  <div v-if="showInfoModal" class="modal-overlay" @click="showInfoModal = false">
    <div class="modal-container tooltip-style-modal" @click.stop>
      <div class="tooltip-header p-3 d-flex justify-content-between align-items-center">
        <h5 class="m-0 text-dark fw-bold text-truncate" style="max-width: 85%;">{{ threadInfo.name }}</h5>
        <button class="modal-close" @click="showInfoModal = false">
          <i class="bi bi-x"></i>
        </button>
      </div>
      
      <div class="section-header px-3 py-2 bg-white border-bottom border-light">
        <span class="section-title fw-semibold">Thread Details</span>
      </div>
      
      <div class="tooltip-content">
        <div class="tooltip-row d-flex px-3 py-2">
          <span class="field-name text-secondary fw-medium">Name:</span>
          <span class="field-value text-dark">{{ threadInfo.name }}</span>
        </div>
        <div class="tooltip-row d-flex px-3 py-2">
          <span class="field-name text-secondary fw-medium">Java ID:</span>
          <span class="field-value text-dark">{{ threadInfo.javaId }}</span>
        </div>
        <div class="tooltip-row d-flex px-3 py-2">
          <span class="field-name text-secondary fw-medium">OS ID:</span>
          <span class="field-value text-dark">{{ threadInfo.osId }}</span>
        </div>
      </div>
      
      <div v-if="props.threadRow.parked.length > 0" class="tooltip-category d-flex align-items-center px-3 py-2 bg-light">
        <div class="color-indicator me-3" style="width: 12px; height: 12px; border-radius: 3px; background-color: #E57373"></div>
        <div class="d-flex justify-content-between w-100">
          <span class="category-name fw-medium">Thread Park</span>
          <span class="event-count text-muted small">{{ props.threadRow.parked.length }} event{{ props.threadRow.parked.length !== 1 ? 's' : '' }}</span>
        </div>
      </div>
      
      <div v-if="props.threadRow.sleep.length > 0" class="tooltip-category d-flex align-items-center px-3 py-2 bg-light">
        <div class="color-indicator me-3" style="width: 12px; height: 12px; border-radius: 3px; background-color: #64B5F6"></div>
        <div class="d-flex justify-content-between w-100">
          <span class="category-name fw-medium">Thread Sleep</span>
          <span class="event-count text-muted small">{{ props.threadRow.sleep.length }} event{{ props.threadRow.sleep.length !== 1 ? 's' : '' }}</span>
        </div>
      </div>
      
      <div v-if="props.threadRow.blocked.length > 0" class="tooltip-category d-flex align-items-center px-3 py-2 bg-light">
        <div class="color-indicator me-3" style="width: 12px; height: 12px; border-radius: 3px; background-color: #FFB74D"></div>
        <div class="d-flex justify-content-between w-100">
          <span class="category-name fw-medium">Monitor Blocked</span>
          <span class="event-count text-muted small">{{ props.threadRow.blocked.length }} event{{ props.threadRow.blocked.length !== 1 ? 's' : '' }}</span>
        </div>
      </div>
      
      <div v-if="props.threadRow.waiting.length > 0" class="tooltip-category d-flex align-items-center px-3 py-2 bg-light">
        <div class="color-indicator me-3" style="width: 12px; height: 12px; border-radius: 3px; background-color: #AED581"></div>
        <div class="d-flex justify-content-between w-100">
          <span class="category-name fw-medium">Monitor Wait</span>
          <span class="event-count text-muted small">{{ props.threadRow.waiting.length }} event{{ props.threadRow.waiting.length !== 1 ? 's' : '' }}</span>
        </div>
      </div>
      
      <div v-if="props.threadRow.socketRead.length > 0" class="tooltip-category d-flex align-items-center px-3 py-2 bg-light">
        <div class="color-indicator me-3" style="width: 12px; height: 12px; border-radius: 3px; background-color: #9575CD"></div>
        <div class="d-flex justify-content-between w-100">
          <span class="category-name fw-medium">Socket Read</span>
          <span class="event-count text-muted small">{{ props.threadRow.socketRead.length }} event{{ props.threadRow.socketRead.length !== 1 ? 's' : '' }}</span>
        </div>
      </div>
      
      <div v-if="props.threadRow.socketWrite.length > 0" class="tooltip-category d-flex align-items-center px-3 py-2 bg-light">
        <div class="color-indicator me-3" style="width: 12px; height: 12px; border-radius: 3px; background-color: #4DB6AC"></div>
        <div class="d-flex justify-content-between w-100">
          <span class="category-name fw-medium">Socket Write</span>
          <span class="event-count text-muted small">{{ props.threadRow.socketWrite.length }} event{{ props.threadRow.socketWrite.length !== 1 ? 's' : '' }}</span>
        </div>
      </div>
      
      <div v-if="props.threadRow.fileRead.length > 0" class="tooltip-category d-flex align-items-center px-3 py-2 bg-light">
        <div class="color-indicator me-3" style="width: 12px; height: 12px; border-radius: 3px; background-color: #F06292"></div>
        <div class="d-flex justify-content-between w-100">
          <span class="category-name fw-medium">File Read</span>
          <span class="event-count text-muted small">{{ props.threadRow.fileRead.length }} event{{ props.threadRow.fileRead.length !== 1 ? 's' : '' }}</span>
        </div>
      </div>
      
      <div v-if="props.threadRow.fileWrite.length > 0" class="tooltip-category d-flex align-items-center px-3 py-2 bg-light">
        <div class="color-indicator me-3" style="width: 12px; height: 12px; border-radius: 3px; background-color: #7986CB"></div>
        <div class="d-flex justify-content-between w-100">
          <span class="category-name fw-medium">File Write</span>
          <span class="event-count text-muted small">{{ props.threadRow.fileWrite.length }} event{{ props.threadRow.fileWrite.length !== 1 ? 's' : '' }}</span>
        </div>
      </div>
      
      <div class="modal-footer">
        <button type="button" class="btn btn-sm btn-secondary" @click="showInfoModal = false">
          Close
        </button>
      </div>
    </div>
  </div>

  <!-- Modal for events that contain StackTrace field -->
  <div class="modal fade" :id="'flamegraphModal-' + props.threadRow.threadInfo.osId" tabindex="-1"
       aria-labelledby="flamegraphModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg" style="width: 95vw; max-width: 95%;">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="flamegraphModalLabel">{{ selectedEventCode }}</h5>
          <button type="button" class="btn-close" @click="closeModal" aria-label="Close"></button>
        </div>
        <div id="scrollable-wrapper" class="modal-body p-3" v-if="showFlamegraphDialog">
          <TimeseriesComponent
              :graph-type="GraphType.PRIMARY"
              :event-type="selectedEventCode"
              :use-weight="useWeightValue"
              :zoom-enabled="true"
              :graph-updater="graphUpdater"/>
          <FlamegraphComponent
              :with-timeseries="true"
              :use-weight="useWeightValue"
              :use-guardian="null"
              scrollableWrapperClass="scrollable-wrapper"
              :flamegraph-tooltip="flamegraphTooltip"
              :graph-updater="graphUpdater"/>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.thread-container {
  text-align: left;
  padding: 6px 0;
}

.thread-card {
  background: white;
  border-radius: 8px;
  border: 1px solid #e9ecef;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.thread-card:hover {
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.thread-header {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background-color: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.thread-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-right: 8px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: transparent;
  border: none;
  border-radius: 4px;
  height: 24px;
  width: 24px;
  padding: 0;
  font-size: 0.85rem;
  transition: all 0.15s ease;
}

.flame-btn {
  color: #3f51b5;
}

.flame-btn:hover {
  background-color: rgba(63, 81, 181, 0.1);
}

.info-btn {
  color: #2196F3;
}

.info-btn:hover {
  background-color: rgba(33, 150, 243, 0.1);
}

.thread-title {
  margin: 0;
  font-size: 0.875rem;
  font-weight: 500;
  color: #424242;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-grow: 1;
}

.thread-content {
  padding: 8px;
}

.thread-canvas {
  width: 100%;
}

/* Custom flamegraph menu */
.flamegraph-menu {
  position: fixed;
  background: white;
  border-radius: 6px;
  min-width: 180px;
  padding: 0 0 4px 0;
  z-index: 9999;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  animation: fadeIn 0.15s ease;
}

.flamegraph-menu .menu-header {
  display: flex;
  justify-content: flex-end;
  padding: 4px 8px;
  border-bottom: 1px solid #f1f3f5;
}

.flamegraph-menu .menu-close {
  background: transparent;
  border: none;
  color: #6c757d;
  cursor: pointer;
  width: 20px;
  height: 20px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
  transition: color 0.15s ease;
  border-radius: 3px;
}

.flamegraph-menu .menu-close:hover {
  color: #495057;
  background-color: rgba(108, 117, 125, 0.1);
}

.flamegraph-menu .menu-item {
  padding: 6px 12px;
  font-size: 0.8rem;
  color: #495057;
  cursor: pointer;
  transition: all 0.1s ease;
}

.flamegraph-menu .menu-item:hover {
  background-color: rgba(63, 81, 181, 0.08);
  color: #3f51b5;
}

.flamegraph-menu .menu-item:active {
  background-color: rgba(63, 81, 181, 0.2);
}

.flamegraph-menu .menu-item.disabled {
  color: #adb5bd;
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

/* Modal styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
  animation: modalFadeIn 0.2s ease;
  backdrop-filter: blur(2px);
}

.modal-container {
  background-color: white;
  border-radius: 8px;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  animation: modalSlideIn 0.2s ease;
  overflow: hidden;
  overflow-y: auto;
}

.tooltip-style-modal {
  border: none;
  max-width: 450px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

.tooltip-style-modal .tooltip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tooltip-style-modal .modal-close {
  position: absolute;
  right: 10px;
  top: 10px;
}

/* Info modal styles */
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e9ecef;
  background-color: #f8f9fa;
}

.modal-title {
  margin: 0;
  font-size: 1.1rem;
  color: #3f51b5;
  font-weight: 600;
  display: flex;
  align-items: center;
}

.modal-close {
  background: transparent;
  border: none;
  font-size: 1.2rem;
  color: #6c757d;
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  transition: all 0.15s ease;
}

.modal-close:hover {
  background-color: rgba(108, 117, 125, 0.15);
  color: #343a40;
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  border-top: 1px solid #e9ecef;
  background-color: #f8f9fa;
}

.tooltip-style-modal .modal-footer {
  background-color: #f8f9fa;
  margin-top: 0;
  padding: 8px 16px;
}

/* Section header for Thread Details */
.section-header {
  font-size: 0.9rem;
  color: #424242;
  letter-spacing: 0.01em;
}

.section-title {
  font-size: 0.9rem;
}

/* Info cards */
.info-card {
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  border: 1px solid #e9ecef;
  overflow: hidden;
}

.info-card-header {
  background-color: #f8f9fa;
  padding: 12px 16px;
  font-size: 0.95rem;
  color: #3f51b5;
  font-weight: 600;
  border-bottom: 1px solid #e9ecef;
  display: flex;
  align-items: center;
}

.info-card-body {
  padding: 12px 16px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid #f5f5f5;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-weight: 500;
  color: #495057;
  font-size: 0.85rem;
}

.info-value {
  font-size: 0.85rem;
  color: #212529;
  text-align: right;
}


/* Flamegraph modal styles */
.modal-fade .modal-body {
  padding-left: 5px;
  padding-right: 5px;
  overflow: hidden;
}

.modal-body-content {
  overflow: auto;
}

/* Add a subtle animation to the modal */
.modal.fade .modal-dialog {
  transition: transform 0.3s ease-out;
  transform: translate(0, -50px);
}

.modal.show .modal-dialog {
  transform: none;
}

/* Custom header styling */
.modal-header {
  background-color: #f8f9fa;
  border-bottom: 1px solid #dee2e6;
}

.modal-title {
  font-weight: 600;
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
