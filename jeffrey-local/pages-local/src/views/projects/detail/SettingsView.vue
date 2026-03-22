<template>
  <PageHeader
      title="Project Settings"
      description="Configure project name and manage project lifecycle"
      icon="bi-gear"
  >
    <!-- Loading State -->
    <LoadingState v-if="isLoading" message="Loading project settings..."/>

    <template v-else>
      <!-- General Settings Section -->
      <div class="settings-section mb-4">
        <div class="section-header">
          <i class="bi bi-pencil-square section-icon"></i>
          <h6 class="section-title">General Settings</h6>
        </div>

        <form @submit.prevent="saveChanges">
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

          <div class="settings-actions">
            <button
                type="submit"
                class="btn-action btn-action-primary"
                :disabled="!hasChanges"
            >
              <span v-if="isSaving" class="spinner-border spinner-border-sm" role="status"
                    aria-hidden="true"></span>
              <i v-else class="bi bi-check-lg"></i>
              {{ isSaving ? 'Saving...' : 'Save Changes' }}
            </button>
          </div>
        </form>
      </div>

      <!-- Danger Zone Section -->
      <div class="settings-section danger-section">
        <div class="section-header danger-header">
          <i class="bi bi-exclamation-triangle-fill section-icon danger-icon"></i>
          <h6 class="section-title danger-title">Danger Zone</h6>
        </div>

        <p class="danger-description">
          Actions in this section can lead to permanent data loss. Please proceed with caution.
        </p>

        <button
            type="button"
            class="btn-action btn-action-danger"
            @click="openDeleteConfirmation"
            :disabled="isDeleting"
        >
          <span v-if="isDeleting" class="spinner-border spinner-border-sm" role="status"
                aria-hidden="true"></span>
          <i v-else class="bi bi-trash3"></i>
          {{ isDeleting ? 'Deleting...' : 'Delete Project' }}
        </button>
      </div>
    </template>
  </PageHeader>

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
          <button type="button" class="btn btn-secondary" @click="closeDeleteConfirmation" :disabled="isDeleting">
            Cancel
          </button>
          <button
              type="button"
              class="btn btn-danger"
              @click="deleteProject"
              :disabled="deleteConfirmText !== projectName || isDeleting"
          >
            <span v-if="isDeleting" class="spinner-border spinner-border-sm me-2" role="status"
                  aria-hidden="true"></span>
            {{ isDeleting ? 'Deleting...' : 'Delete Project' }}
          </button>
        </div>
      </div>
    </div>
  </div>
  <div class="modal-backdrop fade show" v-if="showDeleteConfirmation"></div>
</template>

<script setup lang="ts">
import {ref, onMounted} from 'vue';
import {useRouter} from 'vue-router';
import {useNavigation} from '@/composables/useNavigation';
import ProjectSettingsClient from '@/services/api/ProjectSettingsClient';
import ProjectClient from '@/services/api/ProjectClient';
import ToastService from '@/services/ToastService';
import MessageBus from '@/services/MessageBus';
import LoadingState from '@/components/LoadingState.vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import '@/styles/shared-components.css';

const router = useRouter();
const {workspaceId, projectId} = useNavigation();

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
    MessageBus.emit(MessageBus.UPDATE_PROJECT_SETTINGS, {name: projectName.value});

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
/* Settings Section */
.settings-section {
  background: white;
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(94, 100, 255, 0.08);
}

.section-icon {
  color: #5e64ff;
  font-size: 1rem;
}

.section-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

/* Form Field */
.form-field {
  margin-bottom: 20px;
  max-width: 500px;
}

.form-field-label {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  color: #374151;
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
  color: #374151;
}

.settings-input:focus {
  outline: none;
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
}

.form-field-help {
  font-size: 0.8rem;
  color: #6b7280;
  margin-top: 6px;
}

.settings-actions {
  display: flex;
  justify-content: flex-start;
  padding-top: 16px;
  border-top: 1px solid rgba(94, 100, 255, 0.08);
  margin-top: 8px;
}

/* Action Buttons */
.btn-action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  font-size: 0.875rem;
  font-weight: 600;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.btn-action-primary {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.3);
}

.btn-action-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4c52ff, #3f46ff);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(94, 100, 255, 0.4);
}

.btn-action-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-action-danger {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

.btn-action-danger:hover:not(:disabled) {
  background: linear-gradient(135deg, #dc2626, #b91c1c);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(239, 68, 68, 0.4);
}

.btn-action-danger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Danger Zone */
.danger-section {
  background: linear-gradient(135deg, #ffffff, #fff8f8);
  border-color: rgba(239, 68, 68, 0.12);
}

.danger-header {
  border-bottom-color: rgba(239, 68, 68, 0.12);
}

.danger-icon {
  color: #ef4444;
}

.danger-title {
  color: #991b1b;
}

.danger-description {
  color: #6b7280;
  font-size: 0.9rem;
  margin-bottom: 16px;
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
