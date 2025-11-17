<template>
  <PageHeader
    title="Settings"
    description="Configure project settings and manage project properties."
    icon="bi-sliders"
  >
    <!-- Settings Card -->
    <div class="col-12">
      <div class="card shadow-sm border-0 mb-4">
        <div class="card-header bg-light d-flex align-items-center py-3">
          <i class="bi bi-gear fs-4 me-2 text-primary"></i>
          <h5 class="mb-0">Project Settings</h5>
        </div>
        <div class="card-body">
          <div v-if="isLoading" class="text-center py-5">
            <div class="spinner-border text-primary" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
            <p class="mt-2">Loading project settings...</p>
          </div>
          
          <form v-else @submit.prevent="saveChanges">
            <div class="row mb-4">
              <div class="col-md-6">
                
                <div class="mb-3">
                  <label for="projectName" class="form-label">Project Name</label>
                  <input 
                    type="text" 
                    class="form-control" 
                    id="projectName" 
                    v-model="projectName"
                    @input="checkForChanges"
                  >
                  <div class="form-text text-muted">The name of your project that will appear in the dashboard.</div>
                </div>
              </div>
            </div>
            
            <div class="d-flex justify-content-end mb-5">
              <button 
                type="submit" 
                class="btn btn-primary" 
                :disabled="!hasChanges" 
                :class="{'btn-opacity': !hasChanges}"
              >
                <span v-if="isSaving" class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                {{ isSaving ? 'Saving...' : 'Save Changes' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
    
    <!-- Delete Project Section (with reduced spacing) -->
    <div class="col-12">
      <div class="card shadow-sm border-0 mb-4">
        <div class="card-header bg-light d-flex align-items-center py-3">
          <i class="bi bi-trash fs-4 me-2 text-danger"></i>
          <h5 class="mb-0">Danger Zone</h5>
        </div>
        <div class="card-body">
          <p class="text-muted mb-4">
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
  </PageHeader>

  <!-- Confirmation Modal -->
  <div class="modal fade" :class="{ 'show d-block': showDeleteConfirmation }" tabindex="-1" 
       aria-labelledby="deleteConfirmationModal" :aria-hidden="!showDeleteConfirmation">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content border-0 shadow">
        <div class="modal-header bg-danger-soft text-danger border-bottom-0">
          <h5 class="modal-title">Confirm Delete</h5>
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
  
  <!-- Modal Backdrop -->
  <div class="modal-backdrop fade show" v-if="showDeleteConfirmation"></div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import ProjectSettingsClient from '@/services/project/ProjectSettingsClient';
import ProjectClient from '@/services/ProjectClient';
import ToastService from '@/services/ToastService';
import MessageBus from '@/services/MessageBus';
import PageHeader from '@/components/layout/PageHeader.vue';

const route = useRoute();
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
  // Reset confirmation text
  deleteConfirmText.value = '';
  // Show modal
  showDeleteConfirmation.value = true;
}

// Close delete confirmation modal
function closeDeleteConfirmation() {
  // Hide modal
  showDeleteConfirmation.value = false;
  // Reset confirmation text
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
.btn-opacity {
  opacity: 0.65;
}

/* Modal styling */
.modal.show {
  background-color: rgba(0, 0, 0, 0.5);
}

.modal-backdrop {
  z-index: 1040;
}

.modal {
  z-index: 1050;
}

.bg-danger-soft {
  background-color: rgba(220, 53, 69, 0.15);
}

/* Card styling */
.card {
  border-radius: 0.25rem;
  overflow: hidden;
  transition: all 0.2s ease;
}

/* Reduce spacing between cards */
.col-12:nth-child(3) {
  margin-top: -0.5rem;
}

.shadow-sm {
  box-shadow: 0 0.125rem 0.375rem rgba(0, 0, 0, 0.05) !important;
}
</style>
