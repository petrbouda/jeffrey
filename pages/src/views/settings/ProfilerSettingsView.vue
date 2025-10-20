<template>
  <div>
    <!-- Step Progress Indicator -->
    <div class="step-progress-card mb-4">
      <div class="step-progress-content">
        <div class="step-indicators">
          <div class="step-indicator" :class="{ 'active': currentStep === 1, 'completed': currentStep > 1 }">
            <div class="step-icon">
              <i class="bi bi-terminal-fill"></i>
            </div>
            <span class="step-label">Configure Command</span>
          </div>
          <div class="step-connector" :class="{ 'active': currentStep > 1 && currentStep !== 2 }"></div>
          <div class="step-indicator optional-step" :class="{ 'active': currentStep === 2, 'visited': hasVisitedBuilder }">
            <div class="step-icon">
              <i class="bi bi-ui-checks-grid"></i>
            </div>
            <span class="step-label">Build Configuration</span>
            <span class="optional-badge">Optional</span>
          </div>
          <div class="step-connector" :class="{ 'active': currentStep === 3 }"></div>
          <div class="step-indicator" :class="{ 'active': currentStep === 3, 'completed': currentStep > 3 }">
            <div class="step-icon">
              <i class="bi bi-globe2"></i>
            </div>
            <span class="step-label">Apply Settings</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Settings Card -->
    <div class="profiler-settings-main-card mb-4">
      <div class="profiler-settings-main-content">

        <!-- Step 1: Command Configuration Panel -->
        <div v-if="currentStep === 1" class="command-configuration-step">
          <div class="step-header">
            <div class="step-header-status header-primary">
              <div class="step-type-info">
                <i class="bi bi-terminal-fill"></i>
                <span>COMMAND CONFIGURATION</span>
              </div>
            </div>
            <div class="step-header-content">
              <div class="step-header-main">
                <div class="step-header-info">
                  <h4 class="step-header-title">AsyncProfiler Command</h4>
                  <div class="step-header-description">
                    Enter your AsyncProfiler command directly or use the builder to create one
                  </div>
                </div>
              </div>
            </div>
          </div>

          <ConfigureCommand
            v-model="finalCommand"
            @open-builder="openBuilder"
            @accept-command="proceedToApply"
            @clear="resetBuilderState"
          />
        </div>

        <!-- Step 2: Builder Mode -->
        <div v-if="currentStep === 2" class="builder-step">
          <CommandBuilder
            :agent-mode="agentMode"
            @cancel="cancelBuilder"
            @accept-command="acceptBuilderCommand"
          />
        </div>

        <!-- Step 3: Application Scope -->
        <div v-if="currentStep === 3" class="application-step">
          <div class="step-header">
            <div class="step-header-status header-primary">
              <div class="step-type-info">
                <i class="bi bi-globe2"></i>
                <span>APPLICATION SCOPE</span>
              </div>
            </div>
            <div class="step-header-content">
              <div class="step-header-main">
                <div class="step-header-info">
                  <h4 class="step-header-title">Apply Profiler Configuration</h4>
                  <div class="step-header-description">
                    Choose where to apply your AsyncProfiler configuration
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="scope-selection-section">
            <!-- Final Command Display -->
            <div class="final-command-display">
              <label class="command-label">Command to Apply</label>
              <div class="command-preview">
                <code>{{ finalCommand }}</code>
              </div>
            </div>

            <!-- Scope Options -->
            <div class="scope-options">
              <h5 class="scope-section-title">Application Scope</h5>

              <div class="scope-option-cards">
                <div class="scope-option-card" :class="{ 'selected': applicationScope === 'global' }" @click="applicationScope = 'global'">
                  <div class="scope-option-header">
                    <input type="radio" v-model="applicationScope" value="global" />
                    <div class="scope-option-info">
                      <i class="bi bi-globe2"></i>
                      <div>
                        <h6 class="scope-option-title">Apply Globally</h6>
                        <p class="scope-option-description">Apply to all workspaces and future projects</p>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="scope-option-card" :class="{ 'selected': applicationScope === 'workspaces' }" @click="applicationScope = 'workspaces'">
                  <div class="scope-option-header">
                    <input type="radio" v-model="applicationScope" value="workspaces" />
                    <div class="scope-option-info">
                      <i class="bi bi-folder-fill"></i>
                      <div>
                        <h6 class="scope-option-title">Apply to Selected Workspaces</h6>
                        <p class="scope-option-description">Choose specific local workspaces to apply configuration</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Workspace Selection (when workspaces scope is selected) -->
            <div v-if="applicationScope === 'workspaces'" class="workspace-selection-section">
              <h5 class="workspace-section-title">Select Local Workspaces</h5>
              <div v-if="localWorkspaces.length === 0" class="no-workspaces-message">
                <i class="bi bi-info-circle"></i>
                <span>No local workspaces available. Only local workspaces can have profiler settings applied.</span>
              </div>
              <div v-else class="workspace-selection-grid">
                <div
                  v-for="workspace in localWorkspaces"
                  :key="workspace.id"
                  class="workspace-selection-card"
                  :class="{ 'selected': selectedWorkspaces.includes(workspace.id) }"
                  @click="toggleWorkspaceSelection(workspace.id)"
                >
                  <div class="workspace-selection-header">
                    <input
                      type="checkbox"
                      :checked="selectedWorkspaces.includes(workspace.id)"
                      @click.stop
                      @change="toggleWorkspaceSelection(workspace.id)"
                    />
                    <div class="workspace-selection-info">
                      <i class="bi bi-folder-fill"></i>
                      <h6 class="workspace-selection-name">{{ workspace.name }}</h6>
                    </div>
                  </div>
                  <div class="workspace-selection-description">
                    {{ workspace.description || `Projects for ${workspace.name}` }}
                  </div>
                </div>
              </div>
            </div>

            <!-- Apply Actions -->
            <div class="apply-actions">
              <button
                type="button"
                class="btn-back-to-command"
                @click="backToCommand"
              >
                <i class="bi bi-arrow-left"></i>
                Back to Command
              </button>
              <button
                type="button"
                class="btn-apply-configuration"
                @click="applyConfiguration"
                :disabled="!canApplyConfiguration"
              >
                <i class="bi bi-check-circle-fill"></i>
                Apply Configuration
              </button>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import ToastService from '@/services/ToastService';
import ConfigureCommand from '@/components/settings/ConfigureCommand.vue';
import CommandBuilder from '@/components/settings/CommandBuilder.vue';
import WorkspaceClient from '@/services/workspace/WorkspaceClient';
import WorkspaceType from '@/services/workspace/model/WorkspaceType';
import type Workspace from '@/services/workspace/model/Workspace';
import ProfilerClient from '@/services/ProfilerClient';

// Step management
const currentStep = ref(1); // 1: Configure Command, 2: Builder, 3: Apply
const hasVisitedBuilder = ref(false);

// Step 1: Command Configuration
const finalCommand = ref('');

// Step 3: Application Scope
const applicationScope = ref<'global' | 'workspaces'>('global');
const selectedWorkspaces = ref<string[]>([]);
const workspaces = ref<Workspace[]>([]);

// Agent mode for mutual exclusion
const agentMode = ref<'jeffrey' | 'custom'>('jeffrey');






// Navigation methods
const openBuilder = () => {
  currentStep.value = 2;
  hasVisitedBuilder.value = true;
};

const cancelBuilder = () => {
  currentStep.value = 1;
  hasVisitedBuilder.value = false;
};

const acceptBuilderCommand = (command: string) => {
  finalCommand.value = command;
  currentStep.value = 1;
  ToastService.success('Command Accepted', 'Builder configuration has been converted to command.');
};


const proceedToApply = () => {
  currentStep.value = 3;
};

const backToCommand = () => {
  currentStep.value = 1;
};

const resetBuilderState = () => {
  hasVisitedBuilder.value = false;
};

// Workspace management
const localWorkspaces = computed(() =>
  workspaces.value.filter(w => w.type === WorkspaceType.LOCAL)
);

const toggleWorkspaceSelection = (workspaceId: string) => {
  const index = selectedWorkspaces.value.indexOf(workspaceId);
  if (index === -1) {
    selectedWorkspaces.value.push(workspaceId);
  } else {
    selectedWorkspaces.value.splice(index, 1);
  }
};

const canApplyConfiguration = computed(() => {
  if (applicationScope.value === 'global') {
    return true;
  }
  return selectedWorkspaces.value.length > 0;
});

// Apply configuration
const applyConfiguration = async () => {
  try {
    if (applicationScope.value === 'global') {
      // Global configuration: workspaceId = null, projectId = null
      await ProfilerClient.upsert(null, null, finalCommand.value);
      ToastService.success('Configuration Applied', 'Profiler configuration has been applied globally.');
    } else {
      // Workspace-specific configuration: apply to each selected workspace
      const promises = selectedWorkspaces.value.map(workspaceId =>
        ProfilerClient.upsert(workspaceId, null, finalCommand.value)
      );

      await Promise.all(promises);
      ToastService.success('Configuration Applied', `Profiler configuration has been applied to ${selectedWorkspaces.value.length} workspace(s).`);
    }

    // Reset to step 1 after successful application
    currentStep.value = 1;
    applicationScope.value = 'global';
    selectedWorkspaces.value = [];
  } catch (error) {
    console.error('Failed to apply configuration:', error);
    ToastService.error('Application Failed', 'Failed to apply profiler configuration. Please try again.');
  }
};

// Load workspaces
const loadWorkspaces = async () => {
  try {
    workspaces.value = await WorkspaceClient.list();
  } catch (error) {
    console.error('Failed to load workspaces:', error);
    ToastService.error('Failed to load workspaces', 'Cannot load workspaces from the server.');
  }
};

onMounted(() => {
  loadWorkspaces();
});
</script>

<style scoped>
@import '@/styles/form-utilities.css';
/* Modern Main Card Styling - Matching ProjectsView */
.profiler-settings-main-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04),
  0 1px 3px rgba(0, 0, 0, 0.02);
  backdrop-filter: blur(10px);
}

.profiler-settings-main-content {
  padding: 24px 28px;
}


/* Mode Tabs Styling */
.mode-tabs {
  display: flex;
  background: rgba(248, 250, 252, 0.8);
  border-radius: 6px;
  padding: 2px;
  gap: 2px;
  border: 1px solid rgba(203, 213, 225, 0.5);
}

.mode-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: transparent;
  border: none;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.mode-tab i {
  font-size: 0.7rem;
  opacity: 0.7;
}

.mode-tab:hover {
  background: rgba(241, 245, 249, 0.8);
  color: #475569;
}

.mode-tab:hover i {
  opacity: 1;
}

.mode-tab.active {
  background: #ffffff;
  color: #334155;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  font-weight: 600;
}

.mode-tab.active i {
  opacity: 1;
  color: #5e64ff;
}

/* Configuration Card Styling */
.configuration-section {
  margin-bottom: 24px;
}


/* Modern Configuration Design */
.config-section {
  margin-bottom: 0px;
}

.section-header {
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(94, 100, 255, 0.12);
}

.section-title {
  display: flex;
  align-items: center;
  color: #374151;
  font-weight: 600;
  font-size: 0.85rem;
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.section-title i {
  color: #5e64ff;
  font-size: 0.9rem;
}


/* Component-specific styles not covered by utilities */


/* Builder and Live Command Layout */
.builder-and-command-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.builder-panel {
  flex: 2 1 640px;
}

.live-command-panel {
  flex: 1 1 360px;
}

.live-command-panel .config-output {
  position: sticky;
  top: 24px;
}

/* Builder Layout */
.builder-mode-content {
}

.parameter-panel {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.parameter-panel .section-header {
  margin-bottom: 12px;
}

/* Card stacking */
.config-cards-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.token-summary {
  padding-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.token-summary-title {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #475569;
}

.token-chip-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.token-chip {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px dashed rgba(94, 100, 255, 0.2);
  background: rgba(248, 250, 252, 0.85);
  font-size: 0.8rem;
  color: #475569;
  transition: border-color 0.15s ease, transform 0.15s ease, box-shadow 0.15s ease;
  cursor: default;
}

.token-chip:hover {
  border-color: rgba(94, 100, 255, 0.35);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(94, 100, 255, 0.12);
}

.token-chip-label {
  font-weight: 600;
  text-transform: uppercase;
  font-size: 0.7rem;
  letter-spacing: 0.06em;
  color: #5e64ff;
}

.token-chip-value {
  background: rgba(94, 100, 255, 0.12);
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 0.76rem;
  white-space: nowrap;
  color: #1f2937;
}

.token-chip[title] {
  cursor: help;
}

@media (max-width: 992px) {
  .builder-and-command-layout {
    flex-direction: column;
  }

  .builder-panel {
    flex: 1 1 auto;
  }

  .live-command-panel {
    flex: 1 1 auto;
  }

  .live-command-panel .config-output {
    position: static;
  }
}

@media (max-width: 600px) {
  .token-chip {
    padding: 10px 12px;
    font-size: 0.75rem;
  }

  .token-chip-value {
    font-size: 0.7rem;
  }
}


/* JFR Mode Selector Styling */
.jfr-mode-selector {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(248, 250, 252, 0.6);
  border-radius: 8px;
  border: 1px solid rgba(203, 213, 225, 0.4);
}

.jfr-mode-selector .form-check {
  margin-bottom: 0;
}

.jfr-mode-selector .form-check-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #374151;
  cursor: pointer;
}

.jfr-mode-selector .form-check-input:checked + .form-check-label {
  color: #5e64ff;
  font-weight: 600;
}

/* Agent Mode Selector Styling */
.agent-mode-selector {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(241, 245, 249, 0.6);
  border-radius: 8px;
  border: 1px solid rgba(2, 132, 199, 0.2);
}

.agent-mode-selector .form-check {
  margin-bottom: 0;
}

.agent-mode-selector .form-check-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #374151;
  cursor: pointer;
}

.agent-mode-selector .form-check-input:checked + .form-check-label {
  color: #0284c7;
  font-weight: 600;
}

/* Jeffrey Agent Display */
.jeffrey-agent-display {
  padding: 12px 16px;
  background: rgba(2, 132, 199, 0.08);
  border: 1px solid rgba(2, 132, 199, 0.2);
  border-radius: 8px;
  margin-bottom: 8px;
}

.agent-parameter {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.85rem;
  color: #0369a1;
  background: transparent;
  padding: 0;
  border: none;
  font-weight: 600;
}

/* Form Grid Layout */
.configuration-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  align-items: start;
}

.config-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-field.full-width {
  grid-column: 1 / -1;
}

.config-field.half-width {
  grid-column: span 1;
}

/* Form Control Styling */
.config-label {
  display: flex;
  align-items: center;
  font-size: 0.875rem;
  font-weight: 600;
  color: #374151;
  margin-bottom: 4px;
}

.config-label i {
  color: #5e64ff;
  font-size: 0.8rem;
}

.config-input,
.config-select,
.config-textarea {
  padding: 10px 14px;
  border: 1px solid rgba(94, 100, 255, 0.12);
  border-radius: 8px;
  font-size: 0.875rem;
  background: linear-gradient(135deg, #ffffff, #fafbff);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  color: #374151;
}

.config-input:focus,
.config-select:focus,
.config-textarea:focus {
  outline: none;
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
  transform: translateY(-1px);
}

.config-textarea {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  resize: vertical;
  min-height: 120px;
}

/* Input Group Styling */
.config-input-group {
  display: flex;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(94, 100, 255, 0.12);
  background: linear-gradient(135deg, #ffffff, #fafbff);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.config-input-group:focus-within {
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
  transform: translateY(-1px);
}

.config-input-group-field {
  flex: 1;
  padding: 10px 14px;
  border: none;
  background: transparent;
  font-size: 0.875rem;
  color: #374151;
}

.config-input-group-field:focus {
  outline: none;
}

.config-input-group-select {
  width: 80px;
  padding: 10px 12px;
  border: none;
  border-left: 1px solid rgba(94, 100, 255, 0.12);
  background: rgba(94, 100, 255, 0.03);
  font-size: 0.875rem;
  color: #374151;
}

.config-input-group-select:focus {
  outline: none;
}

.config-help {
  font-size: 0.75rem;
  color: #6b7280;
  margin-top: 4px;
}

/* Configuration Output Styling */
.config-output-section {
  margin-top: 24px;
}

.config-output {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04),
  0 1px 2px rgba(0, 0, 0, 0.02);
  overflow: hidden;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.config-output-header {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border-bottom: 1px solid rgba(94, 100, 255, 0.08);
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.config-output-label {
  display: flex;
  align-items: center;
  color: #374151;
  font-size: 1rem;
  font-weight: 600;
  margin: 0;
}

.config-output-label i {
  color: #5e64ff;
  font-size: 1rem;
}

.config-output-actions {
  display: flex;
  gap: 6px;
}

.config-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: rgba(248, 250, 252, 0.8);
  border: 1px solid rgba(203, 213, 225, 0.5);
  border-radius: 6px;
  color: #64748b;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.config-action-btn:hover:not(:disabled) {
  background: rgba(241, 245, 249, 0.9);
  color: #475569;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.generate-btn-compact:hover:not(:disabled) {
  background: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  border-color: rgba(94, 100, 255, 0.3);
}

.copy-btn-compact:hover:not(:disabled) {
  background: rgba(34, 197, 94, 0.1);
  color: #22c55e;
  border-color: rgba(34, 197, 94, 0.3);
}

.save-btn-compact:hover:not(:disabled) {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
  border-color: rgba(16, 185, 129, 0.3);
}

.config-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.config-output-content {
}

.compact-output {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.compact-output .config-output-text {
  margin-top: 0;
  border-radius: 10px;
  padding: 12px 14px;
  background: rgba(245, 158, 11, 0.08);
  border: 1px dashed rgba(245, 158, 11, 0.25);
  font-size: 0.8rem;
  line-height: 1.5;
  color: #1f2937;
  transition: border-color 0.15s ease, transform 0.15s ease, box-shadow 0.15s ease;
  cursor: pointer;
}

.compact-output .config-output-text:hover {
  border-color: rgba(245, 158, 11, 0.4);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(245, 158, 11, 0.15);
}

.config-output-text {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.8rem;
  color: #374151;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
  display: block;
  margin: 0;
  padding: 0;
  border: none;
}

/* Responsive Design */
@media (max-width: 768px) {
  .configuration-grid {
    grid-template-columns: 1fr;
  }

  .config-field.half-width {
    grid-column: span 1;
  }

  .configuration-actions {
    flex-direction: column;
  }


  .mode-tabs {
    align-self: center;
  }
}

/* Method Pattern List Styling */
.no-patterns-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(94, 100, 255, 0.03);
  border: 1px dashed rgba(94, 100, 255, 0.15);
  border-radius: 8px;
  color: #6b7280;
  font-size: 0.85rem;
  font-style: italic;
}

.no-patterns-message i {
  color: rgba(94, 100, 255, 0.6);
  font-size: 0.9rem;
}

.method-pattern-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  margin-bottom: 8px;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.05), rgba(94, 100, 255, 0.02));
  border: 1px solid rgba(94, 100, 255, 0.12);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.method-pattern-item:hover {
  border-color: rgba(94, 100, 255, 0.2);
  transform: translateY(-1px);
}

.pattern-display {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.pattern-value {
  font-weight: 600;
  color: #374151;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.9rem;
}

.pattern-preview {
  font-size: 0.75rem;
  color: #6b7280;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-style: italic;
}

.btn-remove-pattern {
  background: none;
  border: none;
  color: #ef4444;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 1.1rem;
  line-height: 1;
}

.btn-remove-pattern:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}


.btn-add-pattern {
  white-space: nowrap;
  font-weight: 600;
  min-width: 80px;
  text-align: center;
  cursor: pointer;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.btn-add-pattern:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Step Progress Styling */
.step-progress-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04),
  0 1px 3px rgba(0, 0, 0, 0.02);
  backdrop-filter: blur(10px);
}

.step-progress-content {
  padding: 20px 28px;
}

.step-indicators {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
}

.step-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  opacity: 0.5;
  transition: all 0.3s ease;
}

.step-indicator.active,
.step-indicator.completed {
  opacity: 1;
}

.step-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
  border: 2px solid #e5e7eb;
  background: #ffffff;
  color: #6b7280;
  transition: all 0.3s ease;
}

.step-indicator.active .step-icon {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border-color: #5e64ff;
  color: white;
}

.step-indicator.completed .step-icon {
  background: linear-gradient(135deg, #10b981, #047857);
  border-color: #10b981;
  color: white;
}

.step-indicator.optional-step {
  opacity: 0.7;
  position: relative;
}

.step-indicator.optional-step.visited {
  opacity: 1;
}

.step-indicator.optional-step .step-icon {
  background: #ffffff;
  border: 2px dashed #f59e0b;
  color: #f59e0b;
  font-size: 0.9rem;
}

.step-indicator.optional-step.active .step-icon {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  border: 2px solid #f59e0b;
  color: white;
}

.step-indicator.optional-step.visited .step-icon {
  border: 2px solid #f59e0b;
  background: rgba(245, 158, 11, 0.1);
  color: #d97706;
}

.optional-badge {
  position: absolute;
  top: -8px;
  right: -8px;
  background: #f59e0b;
  color: white;
  font-size: 0.6rem;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 8px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.step-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #6b7280;
  text-align: center;
}

.step-indicator.active .step-label,
.step-indicator.completed .step-label {
  color: #374151;
}

.step-connector {
  width: 60px;
  height: 2px;
  background: #e5e7eb;
  margin: 0 20px;
  transition: all 0.3s ease;
}

.step-connector.active {
  background: linear-gradient(90deg, #10b981, #047857);
}

/* Step Header Styling - Matching ProjectsView */
.step-header {
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  margin-bottom: 24px;
}

.step-header-status {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: white;
}

.header-primary {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
}

.header-secondary {
  background: linear-gradient(135deg, #10b981, #059669);
}

.header-tertiary {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.step-type-info {
  display: flex;
  align-items: center;
  gap: 4px;
}

.step-type-info i {
  font-size: 10px;
}

.step-header-content {
  padding: 20px 24px;
}

.step-header-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 8px 0;
  letter-spacing: -0.02em;
}

.step-header-description {
  color: #6b7280;
  font-size: 0.9rem;
}

/* Action Buttons (Builder-specific) */
.btn-cancel-builder,
.btn-accept-command,
.btn-back-to-command,
.btn-apply-configuration {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border: none;
}

.btn-apply-configuration {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
}

.btn-apply-configuration:hover:not(:disabled) {
  background: linear-gradient(135deg, #4c52ff, #3f46ff);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.4);
}

.btn-apply-configuration:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-cancel-builder,
.btn-back-to-command {
  background: linear-gradient(135deg, #f9fafb, #ffffff);
  border: 1px solid #d1d5db;
  color: #6b7280;
  font-size: 0.8rem;
  padding: 8px 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.btn-cancel-builder:hover,
.btn-back-to-command:hover {
  background: linear-gradient(135deg, #f3f4f6, #e5e7eb);
  color: #374151;
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.btn-accept-command {
  background: linear-gradient(135deg, #10b981, #047857);
  color: white;
  font-size: 0.8rem;
  padding: 8px 14px;
  box-shadow: 0 2px 6px rgba(16, 185, 129, 0.25);
  border: none;
}

.btn-accept-command:hover {
  background: linear-gradient(135deg, #047857, #065f46);
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(16, 185, 129, 0.35);
}

/* Builder Actions */
.builder-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding-top: 12px;
}

/* Scope Selection Styling */
.scope-selection-section {
  margin-top: 20px;
}

.final-command-display {
  margin-bottom: 24px;
}

.command-preview {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: 8px;
  padding: 14px 16px;
  margin-top: 8px;
}

.command-preview code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.85rem;
  color: #374151;
  background: none;
  word-break: break-all;
}

.scope-section-title,
.workspace-section-title {
  font-size: 1rem;
  font-weight: 600;
  color: #374151;
  margin-bottom: 16px;
}

.scope-option-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}

.scope-option-card {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 2px solid rgba(94, 100, 255, 0.1);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.scope-option-card:hover {
  border-color: rgba(94, 100, 255, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.1);
}

.scope-option-card.selected {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border-color: #5e64ff;
  box-shadow: 0 4px 16px rgba(94, 100, 255, 0.2);
}

.scope-option-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.scope-option-info {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex: 1;
}

.scope-option-info i {
  font-size: 1.2rem;
  color: #5e64ff;
  margin-top: 2px;
}

.scope-option-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
  margin: 0 0 4px 0;
}

.scope-option-description {
  font-size: 0.8rem;
  color: #6b7280;
  margin: 0;
  line-height: 1.4;
}

/* Workspace Selection */
.workspace-selection-section {
  margin-top: 20px;
}

.no-workspaces-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: rgba(94, 100, 255, 0.05);
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: 8px;
  color: #6b7280;
  font-size: 0.85rem;
}

.workspace-selection-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.workspace-selection-card {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border: 2px solid rgba(94, 100, 255, 0.3);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.workspace-selection-card:hover {
  background: linear-gradient(135deg, #e8eaf6, #c5cae9);
  border-color: rgba(94, 100, 255, 0.4);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.15);
}

.workspace-selection-card.selected {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border-color: #4c52ff;
  color: white;
  box-shadow: 0 4px 16px rgba(94, 100, 255, 0.3);
}

.workspace-selection-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.workspace-selection-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workspace-selection-info i {
  font-size: 0.9rem;
  color: #5e64ff;
}

.workspace-selection-card.selected .workspace-selection-info i {
  color: white;
}

.workspace-selection-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #1a237e;
  margin: 0;
}

.workspace-selection-card.selected .workspace-selection-name {
  color: white;
}

.workspace-selection-description {
  font-size: 0.75rem;
  color: #283593;
  line-height: 1.4;
}

.workspace-selection-card.selected .workspace-selection-description {
  color: rgba(255, 255, 255, 0.8);
}

/* Apply Actions */
.apply-actions {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid rgba(94, 100, 255, 0.1);
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

/* Responsive Design */
@media (max-width: 768px) {
  .scope-option-cards {
    grid-template-columns: 1fr;
  }

  .workspace-selection-grid {
    grid-template-columns: 1fr;
  }

  .command-actions,
  .builder-actions,
  .apply-actions {
    flex-wrap: wrap;
  }

  .step-indicators {
    flex-direction: column;
    gap: 20px;
  }

  .step-connector {
    width: 2px;
    height: 30px;
    margin: 0;
  }
}

</style>
