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
import {onMounted, onUnmounted, ref} from 'vue';
import Flamegraph from '@/services/flamegraphs/Flamegraph';
import FlameUtils from "@/services/flamegraphs/FlameUtils";
import Utils from "@/services/Utils";
import FlamegraphContextMenu from "@/services/flamegraphs/FlamegraphContextMenu";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import FlamegraphData from "@/services/flamegraphs/model/FlamegraphData";
import GuardMatched from "@/services/flamegraphs/model/guard/GuardMatched";
import TimeRange from "@/services/flamegraphs/model/TimeRange";
import GraphComponents from "@/services/flamegraphs/model/GraphComponents";
import ToastService from "@/services/ToastService.ts";
import MessageBus from "@/services/MessageBus.ts";

const props = defineProps<{
  withTimeseries: boolean
  withSearch: string | null
  useWeight: boolean
  useGuardian: any | null
  saveEnabled: boolean | null
  scrollableWrapperClass: string | null
  flamegraphTooltip: FlamegraphTooltip
  graphUpdater: GraphUpdater
}>()
const searchValue = ref<string | null>(null);
const searchMatched = ref<string | null>(null);
const guardMatched = ref<GuardMatched | null>(null);

// Variables for Save Dialog
const flamegraphName = ref<string | null>(null);
const saveDialog = ref(false);
let currentTimeRange: TimeRange | null;
let resizeTimer: number | null = null;
// ------------------------

let flamegraph: Flamegraph

const canvasWidth = ref('100%');

const contextMenu = ref<HTMLElement>();
const contextMenuItems = ref<any[]>([]);

// Method to convert PrimeVue icons to Bootstrap icons
function getBootstrapIcon(primeIcon: string): string {
  const iconMap: Record<string, string> = {
    'pi pi-chart-bar': 'bi-bar-chart',
    'pi pi-align-center': 'bi-search',
    'pi pi-search-minus': 'bi-arrows-angle-expand',
    'pi pi-times': 'bi-x-lg'
  };

  return iconMap[primeIcon] || 'bi-circle';
}

// Handle context menu item click
function handleContextMenuItemClick(item: any) {
  if (item.command) {
    item.command();
  }
  hideContextMenu();
}

// Show context menu
function showContextMenu(event: MouseEvent) {
  const menu = contextMenu.value as HTMLElement;
  if (!menu) return;

  // Position the menu with 10px offset from cursor position
  menu.style.display = 'block';
  menu.style.left = `${event.layerX + 10}px`;
  menu.style.top = `${event.layerY + 10}px`;

  // Ensure the menu doesn't go off-screen
  const menuRect = menu.getBoundingClientRect();
  const viewportWidth = window.innerWidth;
  const viewportHeight = window.innerHeight;

  // Adjust horizontally if needed
  if (menuRect.right > viewportWidth) {
    menu.style.left = `${event.layerX - menuRect.width - 10}px`;
  }

  // Adjust vertically if needed
  if (menuRect.bottom > viewportHeight) {
    menu.style.top = `${event.layerY - menuRect.height - 10}px`;
  }

  // Add event listener to hide when clicking outside
  document.addEventListener('click', handleDocumentClick);
}

// Hide context menu
function hideContextMenu() {
  const menu = contextMenu.value as HTMLElement;
  if (!menu) return;

  menu.style.display = 'none';
  flamegraph.closeContextMenu();
}

// Handle document click to hide context menu when clicking outside
function handleDocumentClick(event: MouseEvent) {
  const menu = contextMenu.value as HTMLElement;
  if (!menu) return;

  if (!menu.contains(event.target as Node)) {
    hideContextMenu();
    document.removeEventListener('click', handleDocumentClick);
  }
}

const preloaderActive = ref(false)

// Handle window resize event
function handleResize(event: any) {
  if (event != null) {
    event.preventDefault();
  }

  canvasWidth.value = "0%"
  if (resizeTimer) {
    clearTimeout(resizeTimer);
  }

  resizeTimer = window.setTimeout(() => {
    if (flamegraph) {
      // 50 because of the margin from the right of the window - same margin for DIV(ProfileFlamegraphView) even for Modal (SubSecond)
      let removeWidth = 10;
      if (props.scrollableWrapperClass != null) {
        removeWidth = 32;
      }

      let clientWidth = (document.getElementById('flamegraphCanvas')?.parentElement?.clientWidth as number) - removeWidth || 0;
      canvasWidth.value = "" + clientWidth;
      flamegraph.resizeWidthCanvas(clientWidth);
    }
  }, 200);
}

// Initialize the context menu on mount
onMounted(() => {
  MessageBus.on(MessageBus.SIDEBAR_CHANGED, handleResize);

  if (props.useGuardian != null && props.useGuardian.matched != null) {
    guardMatched.value = props.useGuardian.matched
  }

  // Set initial context menu state
  const menu = contextMenu.value as HTMLElement;
  if (menu) {
    menu.style.display = 'none';
  }

  let flamegraphUpdate = (data: FlamegraphData, timeRange: TimeRange | null) => {
    currentTimeRange = timeRange;

    // Create custom show method for our context menu
    const customContextMenu = {
      show: (event: MouseEvent) => showContextMenu(event),
      hide: () => hideContextMenu()
    };

    flamegraph = new Flamegraph(data, 'flamegraphCanvas', props.flamegraphTooltip, customContextMenu, props.useWeight);
    flamegraph.drawRoot();
    FlameUtils.registerAdjustableScrollableComponent(flamegraph, props.scrollableWrapperClass);

    // Initialize context menu items after flamegraph is available
    contextMenuItems.value = FlamegraphContextMenu.resolve(
        () => props.graphUpdater.updateWithSearch(flamegraph.getContextFrame()?.title || ''),
        () => search(flamegraph.getContextFrame()?.title || ''),
        () => flamegraph.resetZoom()
    );
  };

  let zoomUpdate = (data: FlamegraphData, timeRange: TimeRange | null) => {
    flamegraphUpdate(data, timeRange)
    search(searchValue.value)
  }

  props.graphUpdater.registerFlamegraphCallbacks(
      () => preloaderActive.value = true,
      () => preloaderActive.value = false,
      flamegraphUpdate,
      search,
      () => {
        flamegraph.resetSearch();
        searchMatched.value = null;
        searchValue.value = null;
      },
      zoomUpdate,
      zoomUpdate
  )

  // Add window resize event listener
  window.addEventListener('resize', handleResize);
});

// Clean up event listeners
onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick);
  window.removeEventListener('resize', handleResize);
  if (resizeTimer) {
    clearTimeout(resizeTimer);
  }

  MessageBus.off(MessageBus.SIDEBAR_CHANGED)
});

function search(value: string | null) {
  if (Utils.isNotBlank(value)) {
    searchValue.value = value!.trim()
    searchMatched.value = flamegraph.search(searchValue.value);
  } else {
    searchValue.value = null
  }
}

function resetSearch() {
  props.graphUpdater.resetSearch();
}

const openSaveDialog = () => {
  flamegraphName.value = props.flamegraphTooltip.eventType + "-" + new Date().toISOString()
  saveDialog.value = true
}

const saveFlamegraph = () => {
  const components = props.withTimeseries ? GraphComponents.BOTH : GraphComponents.FLAMEGRAPH_ONLY
  props.graphUpdater.flamegraphClient().save(components, flamegraphName.value!!, currentTimeRange)
      .then(() => {
        saveDialog.value = false
        flamegraphName.value = null
        ToastService.info("Flamegraph saved successfully")
      });
};
</script>

<template>
  <div style="text-align: left; padding-bottom: 10px;padding-top: 10px">
    <div class="row">
      <div class="col-5 d-flex">
        <button class="btn btn-outline-secondary mt-2 me-2" title="Reset Zoom" @click="flamegraph.resetZoom()">
          <i class="bi bi-arrows-angle-expand"></i> Reset Zoom
        </button>
        <button class="btn btn-outline-primary mt-2 me-2" title="Save" @click="openSaveDialog"
                v-if="Utils.parseBoolean(props.saveEnabled)">
          <i class="bi bi-download"></i> Save
        </button>
        <button class="btn btn-outline-info mt-2 disabled"
                :style="{'color': guardMatched?.color}"
                v-if="guardMatched != null">
          {{ `Guard Matched: ` + guardMatched.percent + `%` }}
        </button>
      </div>
      <div id="search_output" class="position-relative" :class="preloaderActive ? 'col-1' : 'col-2'">
        <button class="btn btn-outline-info mt-2 position-absolute end-0"
                @click="resetSearch()" v-if="searchMatched != null"
                title="Reset Search">
          {{ `Matched: ` + searchMatched + `%` }}
        </button>
      </div>

      <div class="col-1 d-flex" v-if="preloaderActive">
        <div id="preloader" class="d-flex justify-content-end align-items-center w-100" style="padding: 0;">
          <div class="spinner-border spinner-border-sm text-primary me-4" style="height: 20px; width: 20px"
               role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
      </div>

      <div class="col-5 d-flex" style="float: right">
        <div class="input-group mt-2">
          <button class="btn btn-primary d-flex align-items-center" @click="search(searchValue)">Search</button>
          <input type="text" class="form-control" v-model="searchValue" @keydown.enter="search(searchValue)"
                 placeholder="Full-text search in Flamegraph">
        </div>
      </div>
    </div>
  </div>

  <canvas id="flamegraphCanvas" :style="{ width: canvasWidth }"></canvas>

  <div class="card p-2 border-1 bg-gray-50" style="visibility:hidden; position:absolute" id="flamegraphTooltip"></div>

  <!-- Bootstrap-styled Context Menu -->
  <div class="dropdown-menu custom-context-menu" ref="contextMenu" id="flamegraphContextMenu">
    <button v-for="(item, index) in contextMenuItems"
            :key="index"
            :v-if="!item.separator"
            class="dropdown-item d-flex align-items-center"
            type="button"
            @click="handleContextMenuItemClick(item)">
      <i v-if="item.icon" class="bi me-2" :class="getBootstrapIcon(item.icon)"></i>
      {{ item.label }}
    </button>
  </div>

  <!-- ------------------------------------------------ -->
  <!-- Dialog for saving the flamegraph with timeseries -->
  <!-- ------------------------------------------------ -->
  <div class="modal fade" :class="{ 'show': saveDialog }" tabindex="-1"
       :style="{ display: saveDialog ? 'block' : 'none' }">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Save the current Flamegraph</h5>
          <button type="button" class="btn-close" @click="saveDialog = false"></button>
        </div>
        <div class="modal-body">
          <div class="mb-3">
            <input class="form-control" id="filename" v-model="flamegraphName" type="text">
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-primary" @click="saveFlamegraph"
                  :disabled="flamegraphName == null || flamegraphName.trim().length === 0">Save
          </button>
          <button type="button" class="btn btn-secondary" @click="saveDialog = false">Cancel</button>
        </div>
      </div>
    </div>
  </div>
  <div class="modal-backdrop fade show" v-if="saveDialog"></div>
</template>

<style scoped>
.custom-context-menu {
  position: absolute;
  z-index: 1000;
  min-width: 200px;
  padding: 0.5rem 0;
  margin: 0;
  font-size: 0.875rem;
  color: #212529;
  text-align: left;
  background-color: #fff;
  background-clip: padding-box;
  border: 1px solid rgba(0, 0, 0, 0.15);
  border-radius: 0.25rem;
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.15);
  display: none; /* Initially hidden */
}

.custom-context-menu .dropdown-item {
  display: block;
  width: 100%;
  padding: 0.5rem 1.5rem;
  clear: both;
  font-weight: 400;
  color: #212529;
  text-align: inherit;
  white-space: nowrap;
  background-color: transparent;
  border: 0;
  cursor: pointer;
}

.custom-context-menu .dropdown-item:hover,
.custom-context-menu .dropdown-item:focus {
  color: #16181b;
  text-decoration: none;
  background-color: #f8f9fa;
}

/* Fix for equal height of button and input */
.input-group {
  display: flex;
  align-items: stretch;
}

.input-group .btn,
.input-group .form-control {
  height: 38px; /* Standard Bootstrap input height */
  line-height: 1.5;
}

.input-group .btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 0;
  padding-bottom: 0;
}
</style>
