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
import GuardianClient from "@/services/guardian/GuardianClient";
import Utils from "@/services/Utils";
import GraphType from "@/services/flamegraphs/GraphType";
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import CardCarousel from "@/components/CardCarousel.vue";
import {useRoute} from "vue-router";
import { useNavigation } from '@/composables/useNavigation';
import GuardianFlamegraphClient from "@/services/flamegraphs/client/GuardianFlamegraphClient";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/services/flamegraphs/tooltips/FlamegraphTooltipFactory";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import FullGraphUpdater from "@/services/flamegraphs/updater/FullGraphUpdater";
import GuardAnalysisResult from "@/services/flamegraphs/model/guard/GuardAnalysisResult";
import GuardResponse from "@/services/flamegraphs/model/guard/GuardResponse";
import GuardVisualization from "@/services/flamegraphs/model/guard/GuardVisualization";
import * as bootstrap from 'bootstrap';
import PageHeader from '@/components/layout/PageHeader.vue';
import type { PropType } from 'vue';

// Props definition
const props = defineProps({
  profile: {
    type: Object,
    required: true
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
const { workspaceId, projectId } = useNavigation();

let guards = ref<GuardResponse[]>([]);

const showFlamegraphDialog = ref(false);
let activeGuardVisualization: GuardVisualization;

// For info modal
const activeGuardInfo = ref<GuardAnalysisResult | null>(null);
let infoModalInstance: bootstrap.Modal | null = null;

// For category modal
const activeCategoryCards = ref<GuardAnalysisResult[]>([]);
const activeCategoryName = ref<string>('');
let categoryModalInstance: bootstrap.Modal | null = null;

let flamegraphTooltip: FlamegraphTooltip
let graphUpdater: GraphUpdater
let modalInstance: bootstrap.Modal | null = null

// Track window resize for responsive navigation
const handleResize = () => {
  // Force component update to recompute navigation visibility
  guards.value = [...guards.value];
};

onMounted(() => {
  GuardianClient.list(workspaceId.value!, projectId.value!, route.params.profileId as string)
      .then((data) => guards.value = data);

  // Add window resize listener for responsive navigation
  window.addEventListener('resize', handleResize);

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
  
  if (categoryModalInstance) {
    categoryModalInstance.dispose();
    categoryModalInstance = null;
  }

  // Remove resize event listener
  window.removeEventListener('resize', handleResize);

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
    // First close the category modal if it's open
    if (categoryModalInstance) {
      categoryModalInstance.hide();
    }
    
    activeGuardVisualization = guard.visualization
    let flamegraphClient = new GuardianFlamegraphClient(
        route.params.workspaceId as string,
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
  // First close the category modal if it's open
  if (categoryModalInstance) {
    categoryModalInstance.hide();
  }
  
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

// Function to show all cards in a category
const showAllCards = (category: string, cards: GuardAnalysisResult[]) => {
  // Set the active category data
  activeCategoryName.value = category;
  activeCategoryCards.value = [...cards];
  
  // Initialize modal if needed
  nextTick(() => {
    const modalEl = document.getElementById('categoryModal');
    if (modalEl) {
      if (!categoryModalInstance) {
        categoryModalInstance = new bootstrap.Modal(modalEl);
      }
      categoryModalInstance.show();
    }
  });
}

// Function to close category modal
const closeCategoryModal = () => {
  if (categoryModalInstance) {
    categoryModalInstance.hide();
  }
}

function getBadgeClass(guard: GuardAnalysisResult) {
  if (guard.severity === "OK") {
    return "bg-success"
  } else if (guard.severity === "WARNING") {
    return "bg-danger"
  } else if (guard.severity === "INFO") {
    return "bg-primary"
  } else if (guard.severity === "NA") {
    return "bg-secondary"
  } else if (guard.severity === "IGNORE") {
    return "bg-secondary"
  } else {
    return "bg-light text-dark"
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

// Removed divider function - no longer needed

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

function select_color(guard: GuardAnalysisResult, type: string) {
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

// Determine if navigation is needed based on window width and number of items
function needsNavigation(itemCount: number): boolean {
  // Get current window width
  const width = window.innerWidth;
  
  // Determine how many items can be shown based on window width
  // This should match the logic in CardCarousel.vue
  let visibleItems = 4;  // Default for large screens
  
  if (width < 700) {
    visibleItems = 1;
  } else if (width < 1200) {
    visibleItems = 2;
  } else if (width < 1600) {
    visibleItems = 3;
  }
  
  // Only show navigation if there are more items than can be displayed
  return itemCount > visibleItems;
}

// Removed unnecessary function

// Removed removeTooltip function - no longer needed
</script>

<template>
  <PageHeader
    title="Guardian Analysis"
    description="Automated analysis and recommendations for your profile based on traversing Flamegraphs"
    icon="bi-shield-check"
  >
    <div id="autoAnalysisCard">
      <div v-for="guardWithCategory in guards" class="guardian-category mb-4">
      <!-- Modern category header with navigation only when needed -->
      <div class="category-header" :class="{ 'with-navigation': guardWithCategory.results.length > 0 }">
        <div class="category-title-container">
          <h5 class="category-title">{{ guardWithCategory.category }}</h5>
          <!-- Show count badge next to category title -->
          <span class="cards-count-badge">{{ guardWithCategory.results.length }} {{ guardWithCategory.results.length === 1 ? 'card' : 'cards' }}</span>
        </div>
        <div class="category-navigation">
          <!-- View All button -->
          <button v-if="guardWithCategory.results.length > 3" 
                  class="view-all-btn"
                  @click="showAllCards(guardWithCategory.category, guardWithCategory.results)">
            <span>View All</span>
            <i class="bi bi-arrow-right-short"></i>
          </button>
          
          <!-- Show navigation for all categories with carousel -->
          <div v-if="needsNavigation(guardWithCategory.results.length)" class="nav-buttons">
            <button class="nav-btn prev-btn" :id="`prev-${guardWithCategory.category}`">
              <i class="bi bi-chevron-left"></i>
            </button>
            <button class="nav-btn next-btn" :id="`next-${guardWithCategory.category}`">
              <i class="bi bi-chevron-right"></i>
            </button>
          </div>
        </div>
      </div>
      
      <!-- Use carousel for all categories regardless of card count -->
      <div class="carousel-container">
        <CardCarousel 
          :items="guardWithCategory.results" 
          :autoplay="false"
          :max-items="4"
          :prev-button-id="`prev-${guardWithCategory.category}`"
          :next-button-id="`next-${guardWithCategory.category}`">
          <template #item="{ item: guard }">
            <div class="guardian-card" 
                 :class="[`severity-${guard.severity?.toLowerCase() || 'default'}`]"
                 :style="{ backgroundColor: getLightSeverityColor(guard) }">
              
              <!-- Status indicator -->
              <div class="guardian-card-status">
                <i class="bi" :class="[`bi-${select_icon(guard)}`, select_color(guard, 'text')]"></i>
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
          </template>
        </CardCarousel>
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
          <button type="button" class="btn-close" @click="closeModal" aria-label="Close"/>
        </div>
        <div id="scrollable-wrapper" class="modal-body pr-2 pl-2"
             v-if="showFlamegraphDialog && activeGuardVisualization">
          <TimeseriesComponent
              :graph-type="GraphType.PRIMARY"
              :event-type="activeGuardVisualization.eventType"
              :use-weight="activeGuardVisualization.useWeight"
              :zoom-enabled="true"
              :graph-updater="graphUpdater"/>
          <FlamegraphComponent
              :with-timeseries="true"
              :use-weight="activeGuardVisualization.useWeight"
              :use-guardian="activeGuardVisualization"
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
            <span class="badge" :class="getBadgeClass(activeGuardInfo)">{{ mapSeverity(activeGuardInfo.severity) }}</span>
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
  
  <!-- Category Cards Modal -->
  <div class="modal fade" id="categoryModal" tabindex="-1"
       aria-labelledby="categoryModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="categoryModalLabel">
            {{ activeCategoryName }}
            <span class="cards-count-badge ms-2">{{ activeCategoryCards.length }} {{ activeCategoryCards.length === 1 ? 'card' : 'cards' }}</span>
          </h5>
          <button type="button" class="btn-close" @click="closeCategoryModal" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <div class="category-modal-grid">
            <div v-for="(guard, index) in activeCategoryCards" 
                 :key="index" 
                 class="guardian-card" 
                 :class="[`severity-${guard.severity?.toLowerCase() || 'default'}`]"
                 :style="{ backgroundColor: getLightSeverityColor(guard) }">
              
              <!-- Status indicator -->
              <div class="guardian-card-status">
                <i class="bi" :class="[`bi-${select_icon(guard)}`, select_color(guard, 'text')]"></i>
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
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="closeCategoryModal">Close</button>
        </div>
      </div>
    </div>
  </div>
  </PageHeader>
</template>

<style scoped>
/* Page title */
.guardian-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

@media (max-width: 768px) {
  .guardian-title {
    font-size: 1.5rem;
  }
}

/* Guardian container */
/* Removed guardian-container styling */

/* Category styling */
.guardian-category {
  margin-bottom: 1.75rem;
}

.category-header {
  margin-bottom: 0.75rem;
  position: relative;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 0.35rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.category-title-container {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.category-title {
  font-weight: 600;
  margin: 0;
  color: #111827;
  font-size: 1.1rem;
  letter-spacing: -0.01em;
}

.category-navigation {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.cards-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 22px;
  padding: 0 8px;
  font-size: 0.7rem;
  font-weight: 500;
  line-height: 1;
  color: #5e64ff;
  white-space: nowrap;
  background-color: rgba(94, 100, 255, 0.12);
  border-radius: 11px;
  position: relative;
  top: -1px;
}

.nav-buttons {
  display: flex;
  gap: 0.2rem;
}

.nav-btn {
  height: 28px;
  width: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background-color: rgba(255, 255, 255, 0.8);
  border: 1px solid #e5e7eb;
  color: #5e64ff;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 1rem;
  padding: 0;
}

.nav-btn:hover {
  background-color: #5e64ff;
  color: white;
  border-color: #5e64ff;
}

.nav-btn:focus {
  outline: none;
  box-shadow: 0 0 0 2px rgba(94, 100, 255, 0.3);
}

/* View All button */
.view-all-btn {
  display: flex;
  align-items: center;
  font-size: 0.8rem;
  font-weight: 500;
  color: #5e64ff;
  background: transparent;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 0.2rem 0.6rem;
  height: 28px;
  gap: 0.2rem;
  transition: all 0.2s ease;
  cursor: pointer;
}

.view-all-btn:hover {
  background-color: #5e64ff;
  color: white;
  border-color: #5e64ff;
}

.view-all-btn:focus {
  outline: none;
  box-shadow: 0 0 0 2px rgba(94, 100, 255, 0.3);
}

.view-all-btn i {
  font-size: 1rem;
}

/* Card grid for single card categories */
.guardian-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 1rem;
}

/* Carousel container */
.carousel-container {
  width: 100%;
  overflow: hidden;
}

/* Card styling */
.guardian-card {
  position: relative;
  border-radius: 0.5rem;
  overflow: hidden;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.2s ease-in-out;
  padding: 0.65rem;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 5.5rem;
  border-left: 3px solid;
  width: 100%;
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

/* Status indicator */
.guardian-card-status {
  position: absolute;
  top: 0.55rem;
  right: 0.55rem;
  font-size: 0.9rem;
}

/* Card content */
.guardian-card-content {
  flex-grow: 1;
  padding-right: 1.25rem;
  padding-bottom: 0.1rem;
  margin-bottom: 0;
}

.guardian-card-title {
  font-size: 0.82rem;
  font-weight: 600;
  margin-bottom: 0.3rem;
  padding-right: 0.75rem;
  line-height: 1.25;
}

/* Removed different styling for carousel cards */

/* Card footer with score and actions */
.guardian-card-footer {
  margin-top: auto;
  width: 100%;
}

/* Score visualizer */
.guardian-card-score {
  margin-bottom: 0.4rem;
  margin-top: 0;
  font-size: 0.75rem;
  width: 100%;
}

.guardian-card-score-placeholder {
  height: 0.2rem;
}

/* Removed different styling for carousel card scores */

.score-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.2rem;
}

.score-label {
  font-weight: 500;
  opacity: 0.7;
}

.score-value {
  font-weight: 600;
}

.progress {
  height: 5px;
  background-color: rgba(0, 0, 0, 0.04);
  border-radius: 2px;
  overflow: hidden;
  width: 100%;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.06);
}

/* Removed different progress bar height */

.progress-bar {
  border-radius: 2px;
  transition: width 0.5s ease;
  box-shadow: 0 1px 1px rgba(0, 0, 0, 0.15);
}

.score-text {
  opacity: 0.8;
  font-size: 0.7rem;
}

.score-text span {
  font-weight: 500;
}

/* Action buttons */
.guardian-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.4rem;
  margin-top: 0.4rem;
}

/* Removed different margin for carousel card actions */

.flame-btn, .info-btn {
  width: 1.6rem;
  height: 1.6rem;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  font-size: 0.75rem;
  transition: all 0.15s ease-in-out;
  background-color: rgba(255, 255, 255, 0.7);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  padding: 0;
}

/* Ensuring consistent button size between single and multiple cards */
.flame-btn, .info-btn {
  width: 1.6rem;
  height: 1.6rem;
  border-radius: 4px;
  font-size: 0.75rem;
}

.carousel-container .flame-btn, 
.carousel-container .info-btn {
  width: 1.6rem;
  font-size: 0.75rem;
  border-radius: 4px;
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
  .guardian-card {
    min-height: 6rem;
    width: 100%;
  }
}

/* Card in carousel */
/* Ensuring consistent card height between single and multiple cards */
.guardian-card {
  min-height: 5.5rem;
}

/* Category modal grid */
.category-modal-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 1rem;
}
</style>
