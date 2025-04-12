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
import GuardianService from "@/services/guardian/GuardianService";
import Utils from "@/services/Utils";
import GraphType from "@/services/flamegraphs/GraphType";
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import {useRoute} from "vue-router";
import GuardianFlamegraphClient from "@/services/flamegraphs/client/GuardianFlamegraphClient";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/services/flamegraphs/tooltips/FlamegraphTooltipFactory";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import FullGraphUpdater from "@/services/flamegraphs/updater/FullGraphUpdater";
import GuardAnalysisResult from "@/services/flamegraphs/model/guard/GuardAnalysisResult";
import GuardResponse from "@/services/flamegraphs/model/guard/GuardResponse";
import GuardVisualization from "@/services/flamegraphs/model/guard/GuardVisualization";
import * as bootstrap from 'bootstrap';

const route = useRoute()

let guards = ref<GuardResponse[]>([]);

// No longer needed as we're removing tooltips
let autoAnalysisCard: HTMLElement

const showFlamegraphDialog = ref(false);
let activeGuardVisualization: GuardVisualization;

// For info modal
const activeGuardInfo = ref<GuardAnalysisResult | null>(null);
let infoModalInstance: bootstrap.Modal | null = null;

let flamegraphTooltip: FlamegraphTooltip
let graphUpdater: GraphUpdater
let modalInstance: bootstrap.Modal | null = null

onMounted(() => {
  GuardianService.list(route.params.projectId as string, route.params.profileId as string)
      .then((data) => guards.value = data);

  // Removed tooltip references

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
      modalInstance.show();
    }
  } else {
    if (modalInstance) {
      modalInstance.hide();
    }
  }
});

// Function to close the modal
const closeModal = () => {
  if (modalInstance) {
    modalInstance.hide();
  }
  showFlamegraphDialog.value = false;
}

// Clean up event listeners and modal when component is unmounted
onUnmounted(() => {
  if (modalInstance) {
    modalInstance.dispose();
    modalInstance = null;
  }
  
  if (infoModalInstance) {
    infoModalInstance.dispose();
    infoModalInstance = null;
  }

  // Remove global event listeners
  document.removeEventListener('hidden.bs.modal', () => {
  });
});

function mapSeverity(severity: string) {
  if (severity === "INFO") {
    return "Information"
  } else if (severity === "WARNING") {
    return "Warning"
  } else if (severity === "NA") {
    return "Not Applicable"
  } else if (severity === "IGNORE") {
    return "Ignored"
  } else if (severity === "OK") {
    return "OK"
  } else {
    return severity
  }
}

const click_flamegraph = (guard: GuardAnalysisResult) => {
  if (Utils.isNotNull(guard.visualization)) {
    activeGuardVisualization = guard.visualization
    let flamegraphClient = new GuardianFlamegraphClient(
        route.params.projectId as string,
        guard.visualization.primaryProfileId,
        guard.visualization.eventType,
        guard.visualization.useWeight,
        guard.visualization.markers
    )

    graphUpdater = new FullGraphUpdater(flamegraphClient)
    flamegraphTooltip = FlamegraphTooltipFactory.create(guard.visualization.eventType, guard.visualization.useWeight, false)

    // Delayed the initialization of the graphUpdater to ensure that the modal is fully rendered
    setTimeout(() => {
      graphUpdater.initialize()
    }, 200);

    // Then set the flag to show the dialog
    // The watcher will take care of showing the modal
    showFlamegraphDialog.value = true
  }
}

// Function to show information modal
const showInfoModal = (guard: GuardAnalysisResult) => {
  // Set the active guard info
  activeGuardInfo.value = guard;
  
  // Initialize modal if needed
  nextTick(() => {
    const modalEl = document.getElementById('infoModal');
    if (modalEl) {
      if (!infoModalInstance) {
        infoModalInstance = new bootstrap.Modal(modalEl);
      }
      infoModalInstance.show();
    }
  });
}

// Function to close information modal
const closeInfoModal = () => {
  if (infoModalInstance) {
    infoModalInstance.hide();
  }
}

// Function to open flamegraph from info modal
const openFlamegraphFromInfo = () => {
  // First close the info modal
  closeInfoModal();
  
  // Then if we have active guard info with visualization, open flamegraph
  if (activeGuardInfo.value && Utils.isNotNull(activeGuardInfo.value.visualization)) {
    click_flamegraph(activeGuardInfo.value);
  }
}

// Removed mouse_over function - no longer needed for tooltips

// Removed generate_and_place_tooltip function - no longer needed for tooltips

// Removed divider function - no longer needed for tooltips

// Removed generateTooltip function - no longer needed

function select_icon(guard: GuardAnalysisResult) {
  if (guard.severity === "OK") {
    return "check-circle-fill"
  } else if (guard.severity === "WARNING") {
    return "exclamation-triangle-fill"
  } else if (guard.severity === "INFO") {
    return "info-circle-fill"
  } else if (guard.severity === "NA") {
    return "slash-circle-fill"
  } else if (guard.severity === "IGNORE") {
    return "eye-slash-fill"
  }
}

function select_color(guard: GuardAnalysisResult, type: string, shade: number) {
  // For Bootstrap, we'll convert to their color system
  // type can be "text" or "bg"
  if (guard.severity === "OK") {
    return type === "text" ? "text-success" : "bg-success-subtle"
  } else if (guard.severity === "WARNING") {
    return type === "text" ? "text-danger" : "bg-danger-subtle"
  } else if (guard.severity === "INFO") {
    return type === "text" ? "text-primary" : "bg-primary-subtle"
  } else if (guard.severity === "NA" || guard.severity === "IGNORE") {
    return type === "text" ? "text-secondary" : "bg-secondary-subtle"
  }
}

function getSeverityColor(guard: GuardAnalysisResult) {
  // Return a darker color based on severity
  if (guard.severity === "OK") {
    return "#198754" // Darker green
  } else if (guard.severity === "WARNING") {
    return "#dc3545" // Darker red
  } else if (guard.severity === "INFO") {
    return "#0d6efd" // Darker blue
  } else if (guard.severity === "NA" || guard.severity === "IGNORE") {
    return "#6c757d" // Darker gray
  } else {
    return "#6c757d" // Default darker gray
  }
}

function getLightSeverityColor(guard: GuardAnalysisResult) {
  // Return a lighter color based on severity for backgrounds
  if (guard.severity === "OK") {
    return "#d1e7dd" // Light green 
  } else if (guard.severity === "WARNING") {
    return "#f8d7da" // Light red
  } else if (guard.severity === "INFO") {
    return "#cfe2ff" // Light blue
  } else if (guard.severity === "NA" || guard.severity === "IGNORE") {
    return "#e9ecef" // Light gray
  } else {
    return "#ffffff" // Default white
  }
}

// Removed unnecessary function

// Removed removeTooltip function - no longer needed
</script>

<template>
  <div id="autoAnalysisCard">
    <div v-for="guardWithCategory in guards" class="guardian-category mb-4">
      <!-- Modern category header -->
      <div class="category-header">
        <h5 class="category-title">{{ guardWithCategory.category }}</h5>
      </div>
      
      <!-- Grid of cards -->
      <div class="guardian-grid">
        <div v-for="(guard, index) in guardWithCategory.results" 
             :key="index" 
             class="guardian-card" 
             :class="[`severity-${guard.severity?.toLowerCase() || 'default'}`]"
             :style="{ backgroundColor: getLightSeverityColor(guard) }"
             @click.stop="guard.severity !== 'NA' && showInfoModal(guard)">
          
          <!-- Status indicator -->
          <div class="guardian-card-status">
            <i class="bi" :class="[`bi-${select_icon(guard)}`, select_color(guard, 'text', 700)]"></i>
          </div>
          
          <!-- Card content -->
          <div class="guardian-card-content">
            <h6 class="guardian-card-title">{{ guard.rule }}</h6>
          </div>
          
          <!-- Footer with score and actions -->
          <div class="guardian-card-footer">
            <!-- Score section with visualization -->
            <div v-if="guard.score != null" class="guardian-card-score">
              <div v-if="typeof guard.score === 'string' && guard.score.includes('%')" class="score-visualizer">
                <div class="score-header">
                  <span class="score-label">Score</span>
                  <span class="score-value">{{ guard.score }}</span>
                </div>
                <div class="progress">
                  <div class="progress-bar" 
                       :style="{width: guard.score, backgroundColor: getSeverityColor(guard)}"></div>
                </div>
              </div>
              <div v-else class="score-text">
                <span>Score:</span> {{ guard.score }}
              </div>
            </div>
            <div v-else class="guardian-card-score-placeholder"></div>
            
            <!-- Action buttons -->
            <div class="guardian-card-actions">
              <button v-if="Utils.isNotNull(guard.visualization)"
                      class="flame-btn"
                      @click.stop="click_flamegraph(guard)">
                <i class="bi bi-fire"></i>
              </button>
              <button v-if="guard.severity !== 'NA'" 
                      class="info-btn"
                      @click.stop="showInfoModal(guard)">
                <i class="bi bi-info-circle"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Tooltip element removed -->

  <!-- Modal for flamegraph visualization -->
  <div class="modal fade" id="flamegraphModal" tabindex="-1"
       aria-labelledby="flamegraphModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg" style="width: 95vw; max-width: 95%;">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="flamegraphModalLabel">Guardian Flamegraph</h5>
          <button type="button" class="btn-close" @click="closeModal" aria-label="Close"></button>
        </div>
        <div id="scrollable-wrapper" class="modal-body pr-2 pl-2"
             v-if="showFlamegraphDialog && activeGuardVisualization">
          <TimeseriesComponent
              :graph-type="GraphType.PRIMARY"
              :event-type="activeGuardVisualization.eventType"
              :use-weight="activeGuardVisualization.useWeight"
              :with-search="null"
              :search-enabled="false"
              :zoom-enabled="true"
              :graph-updater="graphUpdater"/>
          <FlamegraphComponent
              :with-timeseries="true"
              :with-search="null"
              :use-weight="activeGuardVisualization.useWeight"
              :use-guardian="activeGuardVisualization"
              :time-range="null"
              :save-enabled="false"
              scrollableWrapperClass="scrollable-wrapper"
              :flamegraph-tooltip="flamegraphTooltip"
              :graph-updater="graphUpdater"/>
        </div>
      </div>
    </div>
  </div>
  
  <!-- Information Modal -->
  <div class="modal fade" id="infoModal" tabindex="-1" 
       aria-labelledby="infoModalLabel" aria-hidden="true">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="infoModalLabel" v-if="activeGuardInfo">{{ activeGuardInfo.rule }}</h5>
          <button type="button" class="btn-close" @click="closeInfoModal" aria-label="Close"></button>
        </div>
        <div class="modal-body" v-if="activeGuardInfo">
          <!-- Severity section -->
          <div v-if="activeGuardInfo.severity" class="mb-3">
            <h6 class="text-muted text-uppercase small fw-bold">Severity</h6>
            <p>{{ mapSeverity(activeGuardInfo.severity) }}</p>
          </div>
          
          <!-- Score section -->
          <div v-if="activeGuardInfo.score != null" class="mb-3">
            <h6 class="text-muted text-uppercase small fw-bold">Score</h6>
            <p>{{ activeGuardInfo.score }}</p>
          </div>
          
          <!-- Summary section -->
          <div v-if="activeGuardInfo.summary" class="mb-3">
            <h6 class="text-muted text-uppercase small fw-bold">Summary</h6>
            <p v-html="activeGuardInfo.summary"></p>
          </div>
          
          <!-- Explanation section -->
          <div v-if="activeGuardInfo.explanation" class="mb-3">
            <h6 class="text-muted text-uppercase small fw-bold">Explanation</h6>
            <p v-html="activeGuardInfo.explanation"></p>
          </div>
          
          <!-- Solution section -->
          <div v-if="activeGuardInfo.solution" class="mb-3">
            <h6 class="text-muted text-uppercase small fw-bold">Solution</h6>
            <p v-html="activeGuardInfo.solution"></p>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="closeInfoModal">Close</button>
          <button v-if="Utils.isNotNull(activeGuardInfo?.visualization)" 
                  type="button" 
                  class="btn btn-primary"
                  @click="openFlamegraphFromInfo">
            <i class="bi bi-fire me-1"></i> View Flamegraph
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Guardian container */
/* Removed guardian-container styling */

/* Category styling */
.guardian-category {
  margin-bottom: 2.5rem;
}

.category-header {
  margin-bottom: 1.5rem;
  position: relative;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 0.5rem;
}

.category-title {
  font-weight: 700;
  margin: 0;
  color: #111827;
  font-size: 1.35rem;
}

/* Card grid */
.guardian-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 1rem;
}

/* Card styling */
.guardian-card {
  position: relative;
  border-radius: 0.75rem;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: all 0.2s ease-in-out;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 7rem;
  border-left: 4px solid;
  cursor: pointer;
}

.guardian-card.severity-ok {
  border-left-color: #28a745;
}

.guardian-card.severity-warning {
  border-left-color: #dc3545;
}

.guardian-card.severity-info {
  border-left-color: #0d6efd;
}

.guardian-card.severity-na, .guardian-card.severity-ignore {
  border-left-color: #6c757d;
}

.guardian-card:hover {
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

/* Status indicator */
.guardian-card-status {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  font-size: 1.1rem;
}

/* Card content */
.guardian-card-content {
  flex-grow: 1;
  padding-right: 1.5rem;
}

.guardian-card-title {
  font-size: 0.9rem;
  font-weight: 600;
  margin-bottom: 0.75rem;
  padding-right: 1rem;
}

/* Card footer with score and actions */
.guardian-card-footer {
  margin-top: auto;
  width: 100%;
}

/* Score visualizer */
.guardian-card-score {
  margin-bottom: 0.75rem;
  font-size: 0.8rem;
  width: 100%;
}

.guardian-card-score-placeholder {
  height: 0.5rem;
}

.score-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.25rem;
}

.score-label {
  font-weight: 500;
  opacity: 0.7;
}

.score-value {
  font-weight: 600;
}

.progress {
  height: 6px;
  background-color: rgba(0, 0, 0, 0.05);
  border-radius: 3px;
  overflow: hidden;
  width: 100%;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.08);
}

.progress-bar {
  border-radius: 3px;
  transition: width 0.6s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.score-text {
  opacity: 0.8;
}

.score-text span {
  font-weight: 500;
}

/* Action buttons */
.guardian-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.flame-btn, .info-btn {
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  font-size: 0.875rem;
  transition: all 0.15s ease-in-out;
  background-color: rgba(255, 255, 255, 0.7);
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
  padding: 0;
}

.flame-btn {
  color: #dc3545;
}

.flame-btn:hover {
  background-color: #dc3545;
  color: white;
  box-shadow: 0 2px 8px rgba(220, 53, 69, 0.25);
}

.info-btn {
  color: #0d6efd;
}

.info-btn:hover {
  background-color: #0d6efd;
  color: white;
  box-shadow: 0 2px 8px rgba(13, 110, 253, 0.25);
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .guardian-grid {
    grid-template-columns: repeat(auto-fill, minmax(100%, 1fr));
  }
  
  .guardian-card {
    min-height: 6rem;
  }
}
</style>
