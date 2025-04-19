<template>
  <div class="card w-100">
    <div class="card-header bg-soft-blue text-white">
      <div class="d-flex align-items-center">
        <i class="bi bi-gear fs-5 me-2"></i>
        <h5 class="card-title mb-0">Settings</h5>
      </div>
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
            <h6 class="mb-3">Project Settings</h6>
            
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
        
        <div class="row">
          <div class="col-12">
            <h6 class="mb-3">Danger Zone</h6>
            
            <div class="card border-danger">
              <div class="card-body">
                <h6 class="card-title text-danger">Delete Project</h6>
                <p class="card-text">Once you delete a project, there is no going back. This action cannot be undone.</p>
                <button type="button" class="btn btn-danger">
                  <i class="bi bi-trash me-2"></i>Delete Project
                </button>
              </div>
            </div>
          </div>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import ProjectSettingsClient from '@/services/project/ProjectSettingsClient';
import ToastService from '@/services/ToastService';
import MessageBus from '@/services/MessageBus';

const route = useRoute();
const projectId = route.params.projectId as string;

// Create client
const settingsClient = new ProjectSettingsClient(projectId);

// State variables
const originalProjectName = ref('');
const projectName = ref('');
const isLoading = ref(true);
const isSaving = ref(false);
const hasChanges = ref(false);

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
</script>

<style scoped>
.btn-opacity {
  opacity: 0.65;
}
</style>
