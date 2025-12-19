<template>
  <div>
    <!-- Settings Card -->
    <div class="main-card mb-4">
      <div class="main-card-header">
        <i class="bi bi-gear main-card-header-icon"></i>
        <h5 class="main-card-header-title">Project Settings</h5>
      </div>
      <div class="main-card-content">
        <LoadingState v-if="isLoading" message="Loading project settings..." />

        <form v-else @submit.prevent="saveChanges">
          <div class="settings-form-section">
            <div class="form-field">
              <label for="projectName" class="form-field-label">Project Name</label>
              <input
                type="text"
                class="form-control settings-input"
                id="projectName"
                v-model="projectName"
                @input="checkForChanges"
              >
              <div class="form-field-help">The name of your project that will appear in the dashboard.</div>
            </div>
          </div>

          <div class="settings-actions">
            <button
              type="submit"
              class="btn btn-primary-gradient"
              :disabled="!hasChanges"
            >
              <span v-if="isSaving" class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
              {{ isSaving ? 'Saving...' : 'Save Changes' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Danger Zone Card -->
    <div class="danger-card mb-4">
      <div class="danger-card-header">
        <i class="bi bi-exclamation-triangle-fill danger-card-header-icon"></i>
        <h5 class="danger-card-header-title">Danger Zone</h5>
      </div>
      <div class="main-card-content">
        <p class="danger-description">
          Actions in this section can lead to permanent data loss. Please proceed with caution.
        </p>

        <button
            type="button"
            class="btn btn-danger"
            @click="openDeleteConfirmation"
            :disabled="isDeleting"
        >
          <span v-if="isDeleting" class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
          <i v-else class="bi bi-trash me-2"></i>
          {{ isDeleting ? 'Deleting...' : 'Delete Project' }}
        </button>
      </div>
    </div>
  </div>

  <!-- Delete Project Confirmation Modal -->
  <div class="modal fade" :class="{ 'show d-block': showDeleteConfirmation }" tabindex="-1"
       aria-labelledby="deleteProjectModal" :aria-hidden="!showDeleteConfirmation">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content delete-modal-content">
        <div class="modal-header delete-modal-header border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-exclamation-triangle-fill fs-4 me-2 text-danger"></i>
            <h5 class="modal-title mb-0">Confirm Delete</h5>
          </div>
          <button type="button" class="btn-close" @click="closeDeleteConfirmation" :disabled="isDeleting"></button>
        </div>
        <div class="modal-body">
          <div class="mb-3">
            <p>Are you sure you want to delete this project? This action <strong>cannot</strong> be undone.</p>
            <p class="mb-0">Please type <strong>{{ projectName }}</strong> to confirm:</p>
          </div>
          <div class="form-group">
            <input
              type="text"
              class="form-control"
              v-model="deleteConfirmText"
              placeholder="Type project name here"
              :disabled="isDeleting"
            >
          </div>
        </div>
        <div class="modal-footer border-top-0">
          <button type="button" class="btn btn-secondary" @click="closeDeleteConfirmation" :disabled="isDeleting">Cancel</button>
          <button
            type="button"
            class="btn btn-danger"
            @click="deleteProject"
            :disabled="deleteConfirmText !== projectName || isDeleting"
          >
            <span v-if="isDeleting" class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
            {{ isDeleting ? 'Deleting...' : 'Delete Project' }}
          </button>
        </div>
      </div>
    </div>
  </div>
  <div class="modal-backdrop fade show" v-if="showDeleteConfirmation"></div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import ProjectSettingsClient from '@/services/project/ProjectSettingsClient';
import ProjectClient from '@/services/ProjectClient';
import ToastService from '@/services/ToastService';
import MessageBus from '@/services/MessageBus';
import LoadingState from '@/components/LoadingState.vue';
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
/* Settings Form */
.settings-form-section {
  max-width: 500px;
}

.form-field {
  margin-bottom: 20px;
}

.form-field-label {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text, #374151);
  margin-bottom: 8px;
}

.settings-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: 8px;
  font-size: 0.9rem;
  background: #ffffff;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  color: var(--color-text, #374151);
}

.settings-input:focus {
  outline: none;
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
}

.form-field-help {
  font-size: 0.8rem;
  color: var(--color-text-muted, #6b7280);
  margin-top: 6px;
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid rgba(94, 100, 255, 0.08);
  margin-top: 24px;
}

/* Danger Zone */
.danger-description {
  color: var(--color-text-muted, #6b7280);
  font-size: 0.9rem;
  margin-bottom: 20px;
}

/* Delete Confirmation Modal */
.modal.show {
  background-color: rgba(0, 0, 0, 0.5);
}

.modal-backdrop {
  z-index: 1040;
}

.modal {
  z-index: 1050;
}

.delete-modal-content {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(220, 53, 69, 0.15);
  border-radius: 16px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.08);
}

.delete-modal-header {
  background: linear-gradient(135deg, rgba(220, 53, 69, 0.05), rgba(220, 53, 69, 0.08));
  border-radius: 16px 16px 0 0;
  padding: 20px 24px;
}
</style>
