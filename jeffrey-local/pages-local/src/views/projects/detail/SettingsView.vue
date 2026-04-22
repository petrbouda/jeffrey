<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-gear" title="Project Settings" />
      </template>

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
    </MainCard>
  </div>

  <!-- Delete Project Confirmation Modal -->
  <GenericModal
    modal-id="deleteProjectModal"
    :show="showDeleteConfirmation"
    title="Confirm Delete"
    icon="bi-exclamation-triangle-fill"
    size="md"
    modal-dialog-class="modal-dialog-centered"
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
        Please type <strong>delete</strong> to confirm:
      </p>
    </div>
    <div class="form-group">
      <input
        type="text"
        class="form-control"
        v-model="deleteConfirmText"
        placeholder="Type delete to confirm"
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
        :disabled="deleteConfirmText !== 'delete' || isDeleting"
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
import GenericModal from '@/components/GenericModal.vue';
import LoadingState from '@/components/LoadingState.vue';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
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

    ToastService.success('Success', 'Project name has been updated');
  } catch (error) {
    console.error('Failed to update project name:', error);
    ToastService.error('Error', 'Failed to update project name');
  } finally {
    isSaving.value = false;
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
  if (deleteConfirmText.value !== 'delete') return;

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
  grid-template-columns: 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

/* Card */
.settings-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
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
  padding: var(--spacing-2) var(--spacing-3);
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-base);
  background: var(--color-white);
  color: var(--color-text);
  outline: none;
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
  margin-bottom: 12px;
}

.field-input:focus {
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
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
  color: var(--color-danger-title);
}

.danger-bar-desc {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
</style>
