<template>
    <PageHeader 
      title="Auto Analysis"
      description="Calculated Auto-analysis from the events"
      icon="bi-robot"
    >

    <!-- Results Grid Section -->
    <div id="autoAnalysisCard" class="guardian-grid">
      <div v-for="(rule, index) in rules" :key="index" 
           class="guardian-card" 
           :class="[`severity-${rule.severity?.toLowerCase() || 'default'}`]"
           :style="{ backgroundColor: getLightSeverityColor(rule) }">
        
        <!-- Status indicator -->
        <div class="guardian-card-status">
          <i class="bi" :class="[`bi-${select_icon(rule)}`, select_color(rule, 'text', 700)]"></i>
        </div>
        
        <!-- Card content -->
        <div class="guardian-card-content">
          <h6 class="guardian-card-title">{{ rule.rule }}</h6>
        </div>
        
        <!-- Footer with score and actions -->
        <div class="guardian-card-footer">
          <!-- Progress bar at the top (only for percentage scores) -->
          <div v-if="rule.score != null && typeof rule.score === 'string' && rule.score.includes('%')" class="progress-container">
            <div class="progress">
              <div class="progress-bar" 
                   :style="{width: rule.score, backgroundColor: getSeverityColor(rule)}"></div>
            </div>
          </div>
          
          <!-- Action row with score and info button -->
          <div class="action-row" :class="{'justify-end': rule.score == null}">
            <!-- Score display -->
            <div v-if="rule.score != null" class="score-display">
              <div v-if="typeof rule.score === 'string' && rule.score.includes('%')" class="score-value">
                Score: {{ rule.score }}
              </div>
              <div v-else class="score-text">
                Score: {{ rule.score }}
              </div>
            </div>
            
            <!-- Action buttons -->
            <div class="guardian-card-actions">
              <button v-if="rule.severity !== 'NA'" 
                      class="info-btn"
                      @click="showInfoModal(rule)">
                <i class="bi bi-info-circle"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Information Modal -->
    <div class="modal fade" id="infoModal" tabindex="-1" 
         aria-labelledby="infoModalLabel" aria-hidden="true">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header d-flex justify-content-between align-items-center">
            <h5 class="modal-title" id="infoModalLabel" v-if="activeRuleInfo">{{ activeRuleInfo.rule }}</h5>
            <button type="button" class="btn-close ms-auto" @click="closeInfoModal" aria-label="Close"></button>
          </div>
          <div class="modal-body" v-if="activeRuleInfo">
            <!-- Severity section -->
            <div v-if="activeRuleInfo.severity" class="mb-3">
              <h6 class="text-muted text-uppercase small fw-bold">Severity</h6>
              <span class="badge" :class="getBadgeClass(activeRuleInfo)">{{ mapSeverity(activeRuleInfo.severity) }}</span>
            </div>
            
            <!-- Score section -->
            <div v-if="activeRuleInfo.score != null" class="mb-3">
              <h6 class="text-muted text-uppercase small fw-bold">Score</h6>
              <p>{{ activeRuleInfo.score }}</p>
            </div>
            
            <!-- Summary section -->
            <div v-if="activeRuleInfo.summary" class="mb-3">
              <h6 class="text-muted text-uppercase small fw-bold">Summary</h6>
              <p v-html="activeRuleInfo.summary"></p>
            </div>
            
            <!-- Explanation section -->
            <div v-if="activeRuleInfo.explanation" class="mb-3">
              <h6 class="text-muted text-uppercase small fw-bold">Explanation</h6>
              <p v-html="activeRuleInfo.explanation"></p>
            </div>
            
            <!-- Solution section -->
            <div v-if="activeRuleInfo.solution" class="mb-3">
              <h6 class="text-muted text-uppercase small fw-bold">Solution</h6>
              <p v-html="activeRuleInfo.solution"></p>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeInfoModal">Close</button>
          </div>
        </div>
      </div>
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import { onMounted, ref, nextTick } from 'vue';
import AutoAnalysisClient from "@/services/api/AutoAnalysisClient.ts";
import { useRoute } from "vue-router";
import { useNavigation } from '@/composables/useNavigation';
import AnalysisResult from "@/services/api/model/AnalysisResult.ts";
import * as bootstrap from 'bootstrap';
import PageHeader from '@/components/layout/PageHeader.vue';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();

let rules = ref<AnalysisResult[]>([]);
const activeRuleInfo = ref<AnalysisResult | null>(null);
let infoModalInstance: bootstrap.Modal | null = null;

onMounted(() => {
  AutoAnalysisClient.rules(workspaceId.value!, projectId.value!, route.params.profileId as string)
      .then((data: AnalysisResult[]) => {
        rules.value = data;
      });
  
  // Initialize modal
  nextTick(() => {
    const modalEl = document.getElementById('infoModal');
    if (modalEl) {
      infoModalInstance = new bootstrap.Modal(modalEl);
    }
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

function select_icon(rule: AnalysisResult) {
  if (rule.severity === "OK") {
    return "check-circle-fill"
  } else if (rule.severity === "WARNING") {
    return "exclamation-triangle-fill"
  } else if (rule.severity === "INFO") {
    return "info-circle-fill"
  } else if (rule.severity === "NA") {
    return "slash-circle-fill"
  } else if (rule.severity === "IGNORE") {
    return "eye-slash-fill"
  } else {
    return "question-circle-fill"
  }
}

function select_color(rule: AnalysisResult, type: string) {
  // For Bootstrap, we'll convert to their color system
  // type can be "text" or "bg"
  if (rule.severity === "OK") {
    return type === "text" ? "text-success" : "bg-success-subtle"
  } else if (rule.severity === "WARNING") {
    return type === "text" ? "text-danger" : "bg-danger-subtle"
  } else if (rule.severity === "INFO") {
    return type === "text" ? "text-primary" : "bg-primary-subtle"
  } else if (rule.severity === "NA" || rule.severity === "IGNORE") {
    return type === "text" ? "text-secondary" : "bg-secondary-subtle"
  } else {
    return type === "text" ? "text-muted" : "bg-light"
  }
}

function getSeverityColor(rule: AnalysisResult) {
  // Return a darker color based on severity
  if (rule.severity === "OK") {
    return "#198754" // Darker green
  } else if (rule.severity === "WARNING") {
    return "#dc3545" // Darker red
  } else if (rule.severity === "INFO") {
    return "#0d6efd" // Darker blue
  } else if (rule.severity === "NA" || rule.severity === "IGNORE") {
    return "#6c757d" // Darker gray
  } else {
    return "#6c757d" // Default darker gray
  }
}

function getLightSeverityColor(rule: AnalysisResult) {
  // Return a lighter color based on severity for backgrounds
  if (rule.severity === "OK") {
    return "#d1e7dd" // Light green 
  } else if (rule.severity === "WARNING") {
    return "#f8d7da" // Light red
  } else if (rule.severity === "INFO") {
    return "#cfe2ff" // Light blue
  } else if (rule.severity === "NA" || rule.severity === "IGNORE") {
    return "#e9ecef" // Light gray
  } else {
    return "#ffffff" // Default white
  }
}

// Function to show information modal
const showInfoModal = (rule: AnalysisResult) => {
  // Set the active rule info
  activeRuleInfo.value = rule;
  
  // Show modal if exists
  if (infoModalInstance) {
    infoModalInstance.show();
  }
}

// Function to close information modal
const closeInfoModal = () => {
  if (infoModalInstance) {
    infoModalInstance.hide();
  }
}

function getBadgeClass(rule: AnalysisResult) {
  if (rule.severity === "OK") {
    return "bg-success"
  } else if (rule.severity === "WARNING") {
    return "bg-danger"
  } else if (rule.severity === "INFO") {
    return "bg-primary"
  } else if (rule.severity === "NA") {
    return "bg-secondary"
  } else if (rule.severity === "IGNORE") {
    return "bg-secondary"
  } else {
    return "bg-light text-dark"
  }
}
</script>

<style scoped>
.auto-analysis-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

@media (max-width: 768px) {
  .auto-analysis-title {
    font-size: 1.5rem;
  }
}

.analysis-status-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
}

/* Guardian grid styles */
.guardian-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 1rem;
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
  margin-bottom: 0;
}

.guardian-card-title {
  font-size: 0.82rem;
  font-weight: 600;
  margin-bottom: 0.3rem;
  padding-right: 0.75rem;
  line-height: 1.25;
}

/* Card footer with score and actions */
.guardian-card-footer {
  margin-top: auto;
  width: 100%;
}

/* Progress container at the top of footer */
.progress-container {
  margin-bottom: 0.5rem;
}

/* Action row style for aligned score and buttons */
.action-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 0.3rem;
}

.action-row.justify-end {
  justify-content: flex-end;
}

/* Score display */
.score-display {
  font-size: 0.75rem;
  font-weight: 600;
}

.score-value {
  color: #212529;
}

.score-text {
  color: #495057;
}

.progress {
  height: 5px;
  background-color: rgba(0, 0, 0, 0.04);
  border-radius: 2px;
  overflow: hidden;
  width: 100%;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.06);
}

.progress-bar {
  border-radius: 2px;
  transition: width 0.5s ease;
  box-shadow: 0 1px 1px rgba(0, 0, 0, 0.15);
}

/* Action buttons */
.guardian-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.4rem;
}

.info-btn {
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

/* Style for info button */
.guardian-card:hover .info-btn {
  opacity: 1;
}

.info-btn {
  opacity: 0.8;
  transition: opacity 0.2s ease-in-out;
}

</style>
