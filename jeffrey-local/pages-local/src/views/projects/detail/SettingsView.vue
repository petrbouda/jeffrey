<template>
  <PageHeader
    title="Project Settings"
    description="Configure project name and manage project lifecycle"
    icon="bi-gear"
  >
    <!-- Loading State -->
    <LoadingState v-if="isLoading" message="Loading project settings..." />

    <template v-else>
      <!-- Top Row: General + Blocking -->
      <div class="settings-grid">
        <div class="settings-card">
          <div class="settings-card-header">
            <i class="bi bi-pencil-square"></i>
            <h6>General</h6>
          </div>
          <form @submit.prevent="saveChanges">
            <label class="field-label">Project Name</label>
            <input type="text" class="field-input" v-model="projectName" @input="checkForChanges" />
            <button
              type="submit"
              class="settings-btn settings-btn-primary"
              :disabled="!hasChanges || isSaving"
            >
              <span
                v-if="isSaving"
                class="spinner-border spinner-border-sm"
                role="status"
                aria-hidden="true"
              ></span>
              <i v-else class="bi bi-check-lg"></i>
              {{ isSaving ? 'Saving...' : 'Save Changes' }}
            </button>
          </form>
        </div>

        <div class="settings-card">
          <div class="settings-card-header">
            <i class="bi bi-slash-circle settings-icon-amber"></i>
            <h6>Blocking</h6>
          </div>
          <div class="toggle-row">
            <div class="toggle-row-label">
              <span class="toggle-row-title">Block project</span>
              <span class="toggle-row-desc">Stops all event processing</span>
            </div>
            <button
              class="toggle-track"
              :class="{ on: isBlocked }"
              @click="isBlocked ? unblockProject() : blockProject()"
              :disabled="isBlockingAction"
              type="button"
            >
              <span class="toggle-thumb"></span>
            </button>
          </div>
        </div>
      </div>

      <!-- Streaming Section (full width) -->
      <div class="settings-card streaming-card">
        <div class="settings-card-header">
          <i class="bi bi-broadcast settings-icon-blue"></i>
          <h6>Streaming</h6>
        </div>
        <div
          class="streaming-status"
          :class="effectiveStreamingEnabled ? 'streaming-status-on' : 'streaming-status-off'"
        >
          <span
            class="streaming-dot"
            :class="effectiveStreamingEnabled ? 'dot-on' : 'dot-off'"
          ></span>
          <span>
            Streaming is <strong>{{ effectiveStreamingEnabled ? 'enabled' : 'disabled' }}</strong>
          </span>
          <span
            v-if="effectiveStreamingLevel"
            class="streaming-badge"
            :class="'streaming-badge-' + effectiveStreamingLevel.toLowerCase()"
          >
            {{ effectiveStreamingLevel }}
          </span>
        </div>
        <div class="streaming-actions">
          <button
            type="button"
            class="settings-btn settings-btn-primary"
            @click="enableStreaming"
            :disabled="isStreamingAction || streamingEnabled === true"
          >
            <span
              v-if="isStreamingAction && pendingStreamingState === true"
              class="spinner-border spinner-border-sm"
              role="status"
              aria-hidden="true"
            ></span>
            <i v-else class="bi bi-broadcast"></i>
            Enable
          </button>
          <button
            type="button"
            class="settings-btn settings-btn-outline"
            @click="disableStreaming"
            :disabled="isStreamingAction || streamingEnabled === false"
          >
            <span
              v-if="isStreamingAction && pendingStreamingState === false"
              class="spinner-border spinner-border-sm"
              role="status"
              aria-hidden="true"
            ></span>
            <i v-else class="bi bi-broadcast"></i>
            Disable
          </button>
          <button
            v-if="streamingEnabled !== null && streamingEnabled !== undefined"
            type="button"
            class="settings-btn settings-btn-outline"
            @click="resetStreaming"
            :disabled="isStreamingAction"
          >
            <span
              v-if="isStreamingAction && pendingStreamingState === null"
              class="spinner-border spinner-border-sm"
              role="status"
              aria-hidden="true"
            ></span>
            <i v-else class="bi bi-arrow-counterclockwise"></i>
            Reset to Inherited
          </button>
        </div>
      </div>

      <!-- Danger Zone Bar -->
      <div class="danger-bar">
        <div class="danger-bar-left">
          <i class="bi bi-exclamation-triangle-fill"></i>
          <div>
            <span class="danger-bar-title">Danger Zone</span>
            <span class="danger-bar-desc">Permanently delete this project and all its data</span>
          </div>
        </div>
        <button
          type="button"
          class="settings-btn settings-btn-danger"
          @click="openDeleteConfirmation"
          :disabled="isDeleting"
        >
          <span
            v-if="isDeleting"
            class="spinner-border spinner-border-sm"
            role="status"
            aria-hidden="true"
          ></span>
          <i v-else class="bi bi-trash3"></i>
          {{ isDeleting ? 'Deleting...' : 'Delete Project' }}
        </button>
      </div>
    </template>
  </PageHeader>

  <!-- Delete Project Confirmation Modal -->
  <GenericModal
    modal-id="deleteProjectModal"
    :show="showDeleteConfirmation"
    title="Confirm Delete"
    icon="bi-exclamation-triangle-fill"
    size="md"
    modal-dialog-class="modal-dialog-centered"
    :show-footer="false"
    @update:show="closeDeleteConfirmation"
  >
    <template #header>
      <div class="d-flex align-items-center">
        <i class="bi bi-exclamation-triangle-fill fs-4 me-2 text-danger"></i>
        <h5 class="modal-title mb-0">Confirm Delete</h5>
      </div>
      <button
        type="button"
        class="btn-close"
        @click="closeDeleteConfirmation"
        :disabled="isDeleting"
      ></button>
    </template>
    <div class="mb-3">
      <p>
        Are you sure you want to delete this project? This action <strong>cannot</strong> be undone.
      </p>
      <p class="mb-0">
        Please type <strong>{{ projectName }}</strong> to confirm:
      </p>
    </div>
    <div class="form-group">
      <input
        type="text"
        class="form-control"
        v-model="deleteConfirmText"
        placeholder="Type project name here"
        :disabled="isDeleting"
      />
    </div>
    <template #footer>
      <button
        type="button"
        class="btn btn-secondary"
        @click="closeDeleteConfirmation"
        :disabled="isDeleting"
      >
        Cancel
      </button>
      <button
        type="button"
        class="btn btn-danger"
        @click="deleteProject"
        :disabled="deleteConfirmText !== projectName || isDeleting"
      >
        <span
          v-if="isDeleting"
          class="spinner-border spinner-border-sm me-2"
          role="status"
          aria-hidden="true"
        ></span>
        {{ isDeleting ? 'Deleting...' : 'Delete Project' }}
      </button>
    </template>
  </GenericModal>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import ProjectSettingsClient from '@/services/api/ProjectSettingsClient';
import ProjectClient from '@/services/api/ProjectClient';
import ToastService from '@/services/ToastService';
import MessageBus from '@/services/MessageBus';
import GenericModal from '@/components/GenericModal.vue';
import LoadingState from '@/components/LoadingState.vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import '@/styles/shared-components.css';

const router = useRouter();
const { workspaceId, projectId } = useNavigation();

// Create clients
const settingsClient = new ProjectSettingsClient(workspaceId.value!, projectId.value!);
const projectClient = new ProjectClient(workspaceId.value!, projectId.value!);

// State variables
const originalProjectName = ref('');
const projectName = ref('');
const isLoading = ref(true);
const isSaving = ref(false);
const hasChanges = ref(false);

// Block/unblock state
const isBlocked = ref(false);
const isBlockingAction = ref(false);

// Streaming state
const streamingEnabled = ref<boolean | null | undefined>(undefined);
const effectiveStreamingEnabled = ref(true);
const effectiveStreamingLevel = ref<string | undefined>(undefined);
const isStreamingAction = ref(false);
const pendingStreamingState = ref<boolean | null>(null);

// Delete project state
const isDeleting = ref(false);
const showDeleteConfirmation = ref(false);
const deleteConfirmText = ref('');

// Load project settings
onMounted(async () => {
  try {
    isLoading.value = true;
    const settings = await settingsClient.get();
    originalProjectName.value = settings.name;
    projectName.value = settings.name;
    isBlocked.value = settings.blocked;
    streamingEnabled.value = settings.streamingEnabled;
    effectiveStreamingEnabled.value = settings.effectiveStreamingEnabled ?? true;
    effectiveStreamingLevel.value = settings.effectiveStreamingLevel;
    isLoading.value = false;
  } catch (error) {
    console.error('Failed to load project settings:', error);
    ToastService.error('Error', 'Failed to load project settings');
    isLoading.value = false;
  }
});

// Check if the project name has changed
function checkForChanges() {
  hasChanges.value = projectName.value !== originalProjectName.value;
}

// Save changes
async function saveChanges() {
  if (!hasChanges.value) return;

  try {
    isSaving.value = true;
    await settingsClient.updateName(projectName.value);
    originalProjectName.value = projectName.value;
    hasChanges.value = false;

    // Notify other components that project settings have changed
    MessageBus.emit(MessageBus.UPDATE_PROJECT_SETTINGS, { name: projectName.value });

    ToastService.success('Success', 'Project name has been updated');
  } catch (error) {
    console.error('Failed to update project name:', error);
    ToastService.error('Error', 'Failed to update project name');
  } finally {
    isSaving.value = false;
  }
}

// Block project
async function blockProject() {
  try {
    isBlockingAction.value = true;
    await settingsClient.block();
    isBlocked.value = true;
    ToastService.success(
      'Project Blocked',
      'Project has been blocked. No events will be processed.'
    );
  } catch (error) {
    console.error('Failed to block project:', error);
    ToastService.error('Error', 'Failed to block project');
  } finally {
    isBlockingAction.value = false;
  }
}

// Unblock project
async function unblockProject() {
  try {
    isBlockingAction.value = true;
    await settingsClient.unblock();
    isBlocked.value = false;
    ToastService.success(
      'Project Unblocked',
      'Project has been unblocked. Event processing will resume.'
    );
  } catch (error) {
    console.error('Failed to unblock project:', error);
    ToastService.error('Error', 'Failed to unblock project');
  } finally {
    isBlockingAction.value = false;
  }
}

// Enable streaming
async function enableStreaming() {
  try {
    isStreamingAction.value = true;
    pendingStreamingState.value = true;
    await settingsClient.updateStreaming(true);
    streamingEnabled.value = true;
    effectiveStreamingEnabled.value = true;
    effectiveStreamingLevel.value = 'PROJECT';
    ToastService.success(
      'Streaming Enabled',
      'Streaming has been explicitly enabled for this project.'
    );
  } catch (error) {
    console.error('Failed to enable streaming:', error);
    ToastService.error('Error', 'Failed to enable streaming');
  } finally {
    isStreamingAction.value = false;
    pendingStreamingState.value = null;
  }
}

// Disable streaming
async function disableStreaming() {
  try {
    isStreamingAction.value = true;
    pendingStreamingState.value = false;
    await settingsClient.updateStreaming(false);
    streamingEnabled.value = false;
    effectiveStreamingEnabled.value = false;
    effectiveStreamingLevel.value = 'PROJECT';
    ToastService.success(
      'Streaming Disabled',
      'Streaming has been explicitly disabled for this project.'
    );
  } catch (error) {
    console.error('Failed to disable streaming:', error);
    ToastService.error('Error', 'Failed to disable streaming');
  } finally {
    isStreamingAction.value = false;
    pendingStreamingState.value = null;
  }
}

// Reset streaming to inherited
async function resetStreaming() {
  try {
    isStreamingAction.value = true;
    pendingStreamingState.value = null;
    await settingsClient.updateStreaming(null);
    streamingEnabled.value = null;
    // Re-fetch to get the correct effective state after reset
    const settings = await settingsClient.get();
    effectiveStreamingEnabled.value = settings.effectiveStreamingEnabled ?? true;
    effectiveStreamingLevel.value = settings.effectiveStreamingLevel;
    ToastService.success(
      'Streaming Reset',
      'Streaming setting has been reset to inherit from workspace/global.'
    );
  } catch (error) {
    console.error('Failed to reset streaming:', error);
    ToastService.error('Error', 'Failed to reset streaming setting');
  } finally {
    isStreamingAction.value = false;
    pendingStreamingState.value = null;
  }
}

// Open delete confirmation modal
function openDeleteConfirmation() {
  deleteConfirmText.value = '';
  showDeleteConfirmation.value = true;
}

// Close delete confirmation modal
function closeDeleteConfirmation() {
  showDeleteConfirmation.value = false;
  deleteConfirmText.value = '';
}

// Delete project
async function deleteProject() {
  if (deleteConfirmText.value !== projectName.value) return;

  try {
    isDeleting.value = true;
    await projectClient.delete();

    // Show success message
    ToastService.success('Project Deleted', 'Project has been successfully deleted');

    // Redirect to projects list page
    setTimeout(() => {
      router.push('/projects');
    }, 1500);
  } catch (error) {
    console.error('Failed to delete project:', error);
    ToastService.error('Error', 'Failed to delete project');
    closeDeleteConfirmation();
    isDeleting.value = false;
  }
}
</script>

<style scoped>
/* Grid layout */
.settings-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

/* Card */
.settings-card {
  background: var(--card-bg);
  border: 1px solid var(--card-border-color);
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  padding: 20px;
}

.settings-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.settings-card-header i {
  font-size: 0.9rem;
  color: var(--color-primary);
}

.settings-card-header h6 {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  margin: 0;
}

.settings-icon-amber {
  color: var(--color-warning) !important;
}

.settings-icon-blue {
  color: var(--color-info) !important;
}

/* Form */
.field-label {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 6px;
}

.field-input {
  width: 100%;
  padding: var(--input-padding-y) var(--input-padding-x);
  border: 1px solid var(--input-border-color);
  border-radius: var(--input-border-radius);
  font-size: var(--input-font-size);
  background: var(--input-bg);
  color: var(--color-text);
  outline: none;
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
  margin-bottom: 12px;
}

.field-input:focus {
  border-color: var(--input-focus-border-color);
  box-shadow: var(--input-focus-shadow);
}

/* Buttons */
.settings-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  border: none;
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.settings-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.settings-btn-primary {
  background: var(--color-primary);
  color: var(--color-white);
}

.settings-btn-primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.settings-btn-outline {
  background: var(--color-white);
  color: var(--color-text);
  border: 1px solid var(--color-border);
}

.settings-btn-outline:hover:not(:disabled) {
  background: var(--color-bg-hover);
  border-color: var(--color-border-input);
}

.settings-btn-danger {
  background: var(--color-danger);
  color: var(--color-white);
}

.settings-btn-danger:hover:not(:disabled) {
  background: var(--color-danger-hover);
}

/* Toggle */
.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toggle-row-title {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.toggle-row-desc {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.toggle-track {
  width: 44px;
  height: 24px;
  border-radius: 12px;
  background: var(--color-border-input);
  position: relative;
  cursor: pointer;
  transition: background var(--transition-fast);
  flex-shrink: 0;
  border: none;
  padding: 0;
}

.toggle-track.on {
  background: var(--color-warning);
}

.toggle-track:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toggle-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--color-white);
  box-shadow: var(--shadow-sm);
  transition: transform var(--transition-fast);
  pointer-events: none;
}

.toggle-track.on .toggle-thumb {
  transform: translateX(20px);
}

/* Streaming card */
.streaming-card {
  margin-bottom: 16px;
}

.streaming-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--radius-base);
  font-size: var(--font-size-sm);
  margin-bottom: 14px;
}

.streaming-status-on {
  background: var(--color-success-light);
  border: 1px solid rgba(0, 210, 122, 0.2);
  color: #0a6640;
}

.streaming-status-off {
  background: var(--color-danger-light);
  border: 1px solid rgba(230, 55, 87, 0.2);
  color: #8b1a2b;
}

.streaming-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-on {
  background: var(--color-success);
}

.dot-off {
  background: var(--color-danger);
}

.streaming-badge {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  margin-left: 4px;
}

.streaming-badge-global {
  background: rgba(107, 114, 128, 0.1);
  color: var(--color-text);
}

.streaming-badge-workspace {
  background: rgba(57, 175, 209, 0.1);
  color: var(--color-info-hover);
}

.streaming-badge-project {
  background: var(--color-success-light);
  color: #0a6640;
}

.streaming-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* Danger bar */
.danger-bar {
  background: var(--color-bg-card);
  border: 1px solid rgba(230, 55, 87, 0.15);
  border-radius: var(--radius-md);
  padding: 14px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.danger-bar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.danger-bar-left > i {
  color: var(--color-danger);
  font-size: 1rem;
}

.danger-bar-title {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: #8b1a2b;
}

.danger-bar-desc {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
</style>
