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
import {nextTick, onMounted, onUnmounted, ref, watch} from 'vue';
import ThreadRowData from "@/services/thread/model/ThreadRowData";
import {useRoute} from "vue-router";
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

onMounted(() => {
  // Initialize thread row
  threadRow = new ThreadRow(props.threadCommon, props.threadRow, canvasId.value)
  threadRow.draw()

  // Initialize the Bootstrap modal after the DOM is ready
  nextTick(() => {
    const modalEl = document.getElementById('flamegraphModal')
    if (modalEl) {
      console.log("Initializing modal in onMounted")
      
      // We'll manually create and dispose of the modal
      // for better control over the behavior
      modalEl.addEventListener('hidden.bs.modal', () => {
        console.log("Modal hidden event triggered")
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
  console.log("Closing modal window");
}

// Watch for changes to showFlamegraphDialog to control modal visibility
watch(showFlamegraphDialog, (isVisible) => {
  if (isVisible) {
    if (!modalInstance) {
      const modalEl = document.getElementById('flamegraphModal');
      if (modalEl) {
        modalInstance = new bootstrap.Modal(modalEl);
      }
    }

    if (modalInstance) {
      console.log("Showing modal via watch");
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
  document.removeEventListener('hidden.bs.modal', () => {});
});

const showFlamegraph = (eventCode: string) => {
  console.log("Showing flamegraph for event code:", eventCode)

  console.log(props.threadRow.threadInfo)

  let flamegraphClient = new PrimaryFlamegraphClient(
      route.params.projectId as string,
      route.params.profileId as string,
      eventCode,
      true,
      false,
      false,
      false,
      false,
      props.threadRow.threadInfo
  )

  graphUpdater = new FullGraphUpdater(flamegraphClient)
  flamegraphTooltip = FlamegraphTooltipFactory.create(eventCode, false, false)

  // Set the event code first
  selectedEventCode.value = eventCode

  // Then set the flag to show the dialog
  // The watcher will take care of showing the modal
  showFlamegraphDialog.value = true

  console.log("Showing flamegraph dialog:", showFlamegraphDialog.value)
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
  <div v-resize="() => { threadRow.resizeCanvas() }"
       class="thread-container">
    <div class="thread-row">
      <div class="thread-info">
        <button
            class="flame-btn"
            type="button"
            title="Show flamegraph"
            @click="toggleFlamegraphMenu">
          <i class="bi bi-fire"></i>
        </button>
        <button
            class="info-btn"
            type="button"
            title="Thread information"
            @click="openInfoModal">
          <i class="bi bi-question-circle"></i>
        </button>
        <span class="thread-name" :title="threadInfo.name">{{ threadInfo.name }}</span>
      </div>
      <div class="canvas-container">
        <div :id="canvasId"></div>
      </div>
    </div>
  </div>

  <div v-if="showFlameMenu" class="flamegraph-menu shadow-sm" :style="flameMenuPosition">
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
    <div class="modal-container" @click.stop>
      <div class="modal-header">
        <h4 class="modal-title">Thread Information</h4>
        <button class="modal-close" @click="showInfoModal = false">
          <i class="bi bi-x"></i>
        </button>
      </div>
      <div class="modal-body">
        <div class="info-section">
          <h5>Thread Details</h5>
          <div class="info-row">
            <span class="info-label">Name:</span>
            <span class="info-value">{{ threadInfo.name }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">ID:</span>
            <span class="info-value">{{ threadInfo.javaId }}</span>
          </div>
        </div>

        <div class="info-section">
          <h5>Activity Summary</h5>
          <div v-if="props.threadRow.parked.length > 0" class="info-row">
            <span class="info-label">Thread Park:</span>
            <span class="info-value">{{ props.threadRow.parked.length }} events</span>
          </div>
          <div v-if="props.threadRow.sleep.length > 0" class="info-row">
            <span class="info-label">Thread Sleep:</span>
            <span class="info-value">{{ props.threadRow.sleep.length }} events</span>
          </div>
          <div v-if="props.threadRow.blocked.length > 0" class="info-row">
            <span class="info-label">Monitor Blocked:</span>
            <span class="info-value">{{ props.threadRow.blocked.length }} events</span>
          </div>
          <div v-if="props.threadRow.waiting.length > 0" class="info-row">
            <span class="info-label">Monitor Wait:</span>
            <span class="info-value">{{ props.threadRow.waiting.length }} events</span>
          </div>
          <div v-if="props.threadRow.socketRead.length > 0" class="info-row">
            <span class="info-label">Socket Read:</span>
            <span class="info-value">{{ props.threadRow.socketRead.length }} events</span>
          </div>
          <div v-if="props.threadRow.socketWrite.length > 0" class="info-row">
            <span class="info-label">Socket Write:</span>
            <span class="info-value">{{ props.threadRow.socketWrite.length }} events</span>
          </div>
          <div v-if="props.threadRow.fileRead.length > 0" class="info-row">
            <span class="info-label">File Read:</span>
            <span class="info-value">{{ props.threadRow.fileRead.length }} events</span>
          </div>
          <div v-if="props.threadRow.fileWrite.length > 0" class="info-row">
            <span class="info-label">File Write:</span>
            <span class="info-value">{{ props.threadRow.fileWrite.length }} events</span>
          </div>
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" @click="showInfoModal = false">
          <i class="bi bi-x-circle me-1"></i>
          Close
        </button>
      </div>
    </div>
  </div>

  <!-- Modal for events that contain StackTrace field -->
  <div class="modal fade" id="flamegraphModal" tabindex="-1" aria-labelledby="flamegraphModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg" style="width: 95vw; max-width: 95%;">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="flamegraphModalLabel">Flamegraph</h5>
          <button type="button" class="btn-close" @click="closeModal" aria-label="Close"></button>
        </div>
        <div id="scrollable-wrapper" class="modal-body pr-2 pl-2">
          <template v-if="showFlamegraphDialog">
            <TimeseriesComponent
                :graph-type="GraphType.PRIMARY"
                :event-type="selectedEventCode"
                :use-weight="false"
                :with-search="null"
                :search-enabled="true"
                :zoom-enabled="true"
                :graph-updater="graphUpdater"/>
            <FlamegraphComponent
                :with-timeseries="true"
                :with-search="null"
                :use-weight="false"
                :use-guardian="null"
                :time-range="null"
                :save-enabled="false"
                scrollableWrapperClass="scrollable-wrapper"
                :flamegraph-tooltip="flamegraphTooltip"
                :graph-updater="graphUpdater"/>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.thread-container {
  text-align: left;
  padding: 4px 0;
}

.thread-row {
  display: flex;
  align-items: center;
  width: 100%;
  border-radius: 0.25rem;
  padding: 4px 8px;
  transition: background-color 0.15s ease;
}

.thread-row:hover {
  background-color: rgba(191, 219, 254, 0.15);
}

.thread-info {
  display: flex;
  align-items: center;
  width: 25%;
  min-width: 180px;
}

.flame-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: transparent;
  border: 1px solid #3f51b5;
  color: #3f51b5;
  border-radius: 4px;
  height: 20px;
  width: 24px;
  padding: 0;
  font-size: 0.8rem;
  transition: all 0.2s ease;
}

.flame-btn:hover {
  background-color: rgba(63, 81, 181, 0.1);
  transform: translateY(-1px);
}

.info-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: transparent;
  border: 1px solid #2196F3;
  color: #2196F3;
  border-radius: 4px;
  height: 20px;
  width: 24px;
  padding: 0;
  margin-left: 4px;
  font-size: 0.8rem;
  transition: all 0.2s ease;
}

.info-btn:hover {
  background-color: rgba(33, 150, 243, 0.1);
  transform: translateY(-1px);
}

.thread-name {
  margin-left: 8px;
  font-size: 0.8rem;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  font-weight: 500;
  letter-spacing: 0.01em;
  color: #424242;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: calc(100% - 72px);
}

.canvas-container {
  flex-grow: 1;
  width: 100%;
}

/* Custom flamegraph menu */
.flamegraph-menu {
  position: fixed;
  background: white;
  border-radius: 6px;
  min-width: 180px;
  padding: 4px 0;
  z-index: 9999;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  animation: fadeIn 0.15s ease;
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
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
  animation: modalFadeIn 0.2s ease;
}

.modal-container {
  background-color: white;
  border-radius: 8px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  animation: modalSlideIn 0.2s ease;
}

/* Info modal styles (keeping these) */
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #e9ecef;
}

.modal-title {
  margin: 0;
  font-size: 1.1rem;
  color: #212529;
  font-weight: 500;
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
  width: 24px;
  height: 24px;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.modal-close:hover {
  background-color: rgba(108, 117, 125, 0.1);
  color: #343a40;
}

.info-section {
  margin-bottom: 16px;
}

.info-section h5 {
  font-size: 1rem;
  margin-bottom: 10px;
  color: #495057;
  font-weight: 500;
}

.info-row {
  display: flex;
  padding: 4px 0;
}

.info-label {
  width: 140px;
  font-weight: 500;
  color: #6c757d;
  font-size: 0.85rem;
}

.info-value {
  flex: 1;
  font-size: 0.85rem;
  color: #212529;
}

/* Flamegraph modal styles (copied from ProfileSubSecondView.vue) */
.modal-body {
  padding-left: 5px;
  padding-right: 5px;
  overflow: hidden;
  overflow-y: auto;
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
