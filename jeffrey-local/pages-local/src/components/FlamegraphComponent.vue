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
import FlamegraphData from "@/services/api/model/FlamegraphData";
import GuardMatched from "@/services/api/model/GuardMatched";
import MessageBus from "@/services/MessageBus.ts";
import LoadingIndicator from "@/components/LoadingIndicator.vue";

const props = defineProps<{
  withTimeseries: boolean
  useWeight: boolean
  useGuardian: any | null
  scrollableWrapperClass: string | null
  flamegraphTooltip: FlamegraphTooltip
  graphUpdater: GraphUpdater
}>()

const emit = defineEmits<{
  loaded: []
}>()
const guardMatched = ref<GuardMatched | null>(null);

// Track current search term for zoom updates
let currentSearchValue: string | null = null;

// Variables for Save Dialog
let resizeTimer: number | null = null;
// ------------------------

let flamegraph: Flamegraph

const canvasWidth = ref('100%');

const contextMenu = ref<HTMLElement>();
const contextMenuItems = ref<any[]>([]);
const flamegraphCanvas = ref<HTMLCanvasElement | null>(null);

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
    if (flamegraph && flamegraphCanvas.value) {
      // 50 because of the margin from the right of the window - same margin for DIV(ProfileFlamegraphView) even for Modal (SubSecond)
      let removeWidth = 10;
      if (props.scrollableWrapperClass != null) {
        removeWidth = 32;
      }

      let clientWidth = (flamegraphCanvas.value.parentElement?.clientWidth as number) - removeWidth || 0;
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

  let flamegraphUpdate = (data: FlamegraphData) => {
    if (flamegraph != null) {
      flamegraph.close()
    }

    // Ensure canvas ref is available
    if (!flamegraphCanvas.value) {
      console.error('FlamegraphComponent: Canvas element not available');
      return;
    }

    // Create custom show method for our context menu
    const customContextMenu = {
      show: (event: MouseEvent) => showContextMenu(event),
      hide: () => hideContextMenu()
    };

    flamegraph = new Flamegraph(data, flamegraphCanvas.value, props.flamegraphTooltip, customContextMenu, props.useWeight);
    flamegraph.drawRoot();
    FlameUtils.registerAdjustableScrollableComponent(flamegraph, props.scrollableWrapperClass);

    // Initialize context menu items after flamegraph is available
    contextMenuItems.value = FlamegraphContextMenu.resolve(
        () => props.graphUpdater.updateWithSearch(flamegraph.getContextFrame()?.title || ''),
        () => flamegraph.resetZoom()
    );
  };

  let zoomUpdate = (data: FlamegraphData) => {
    flamegraphUpdate(data)
    search(currentSearchValue)
  }

  props.graphUpdater.registerFlamegraphCallbacks(
      () => preloaderActive.value = true,
      () => {
        preloaderActive.value = false
        emit('loaded')
      },
      flamegraphUpdate,
      search,
      () => {
        flamegraph.resetSearch();
        currentSearchValue = null;
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

  // Pass the handler reference to remove only this component's listener
  MessageBus.off(MessageBus.SIDEBAR_CHANGED, handleResize);

  // Clean up flamegraph resources (canvas event handlers, animation frames, etc.)
  if (flamegraph) {
    flamegraph.close();
  }
});

function search(value: string | null) {
  if (Utils.isNotBlank(value)) {
    currentSearchValue = value!.trim()
    const matched = flamegraph.search(currentSearchValue);
    props.graphUpdater.reportMatched(matched);
  } else {
    currentSearchValue = null
  }
}
</script>

<template>
  <LoadingIndicator v-if="preloaderActive" text="Generating Flamegraph..."/>

  <canvas ref="flamegraphCanvas" id="flamegraphCanvas" :style="{ width: canvasWidth }"></canvas>

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

</style>
