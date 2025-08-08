<template>
  <div>
    <!-- Job Types Card -->
    <div class="job-types-card mb-4">
      <div class="job-types-content">
        <div class="row row-cols-1 row-cols-md-2 row-cols-lg-3 g-4">
          <!-- Projects Synchronization -->
          <div class="col">
            <JobCard
                job-type="PROJECTS_SYNCHRONIZER"
                title="Projects Synchronization"
                description="Keeps Jeffrey projects in sync with your workspace directories by auto-discovering new projects, or removing existing projects based on the synchronization strategy."
                icon="bi-arrow-repeat"
                icon-color="text-purple"
                icon-bg="bg-purple-soft"
                :disabled="projectsSyncJobAlreadyExists"
                :badges="[
                { text: 'Job already exists', color: 'bg-success', condition: projectsSyncJobAlreadyExists }
              ]"
                @create-job="handleCreateJob"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Active Jobs Card -->
    <div class="jobs-main-card mb-4">
      <div class="jobs-main-content">
        <div class="d-flex align-items-center mb-4 gap-3">
          <div class="search-box">
            <div class="input-group input-group-sm phoenix-search">
              <span class="input-group-text border-0 ps-3 pe-0 search-icon-container">
                <i class="bi bi-search text-primary"></i>
              </span>
              <input
                  type="text"
                  class="form-control border-0 py-2"
                  v-model="jobSearchQuery"
                  placeholder="Search scheduled jobs..."
                  @input="filterJobs"
              >
            </div>
          </div>
        </div>

        <!-- Loading indicator -->
        <div v-if="jobsLoading" class="text-center py-4">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <p class="mt-2">Loading scheduled jobs...</p>
        </div>

        <!-- Error state -->
        <div v-else-if="jobsErrorMessage" class="text-center py-4">
          <i class="bi bi-exclamation-triangle-fill fs-1 text-warning mb-3"></i>
          <h5>Failed to load jobs</h5>
          <p class="text-muted">{{ jobsErrorMessage }}</p>
          <button class="btn btn-primary mt-2" @click="refreshJobs">
            <i class="bi bi-arrow-clockwise me-2"></i>Retry
          </button>
        </div>

        <!-- Jobs table -->
        <div v-else-if="globalJobs.length > 0" class="table-responsive">
          <table class="table table-hover mb-0">
            <thead class="table-light">
            <tr>
              <th scope="col" style="width: 30%">Job Type</th>
              <th scope="col" style="width: 60%">Parameters</th>
              <th scope="col" style="width: 10%" class="text-end">Actions</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="job in globalJobs" :key="job.id" :class="{'disabled-job': !job.enabled}">
              <td>
                <div class="d-flex align-items-center">
                  <!-- Projects Synchronization -->
                  <template v-if="job.jobType === 'PROJECTS_SYNCHRONIZER'">
                    <div class="job-icon-sm bg-purple-soft me-2 d-flex align-items-center justify-content-center">
                      <i class="bi bi-arrow-repeat text-purple"></i>
                    </div>
                    <div>
                      <div class="fw-medium">
                        Projects Synchronization
                        <span v-if="!job.enabled" class="badge bg-warning text-dark ms-2 small">Disabled</span>
                      </div>
                    </div>
                  </template>
                </div>
              </td>
              <td>
                <div class="inline-params">
                    <span v-for="(value, key) in job.params" :key="key" class="param-badge">
                      <span class="param-key">{{ key }}:</span>
                      <span class="param-value">{{ value }}</span>
                    </span>
                </div>
              </td>
              <td class="text-end">
                <div class="d-flex justify-content-end gap-2">
                  <button
                      class="btn btn-sm"
                      :class="job.enabled ? 'btn-outline-warning' : 'btn-outline-success'"
                      @click="toggleJobEnabled(job)"
                      :title="job.enabled ? 'Disable job' : 'Enable job'">
                    <i class="bi" :class="job.enabled ? 'bi-pause-fill' : 'bi-play-fill'"></i>
                  </button>
                  <button class="btn btn-sm btn-outline-danger" @click="deleteGlobalJob(job.id)" title="Delete job">
                    <i class="bi bi-trash"></i>
                  </button>
                </div>
              </td>
            </tr>
            </tbody>
          </table>
        </div>

        <!-- Empty state for global jobs -->
        <div v-else class="text-center py-5">
          <i class="bi bi-calendar-x fs-1 text-muted mb-3"></i>
          <h5>No Active Global Jobs</h5>
          <p class="text-muted mb-0">
            There are no active global scheduled jobs. Create a new job using the options above.
          </p>
        </div>
      </div>
    </div>
  </div>

  <!-- Projects Synchronization Modal -->
  <div class="modal fade" id="projectsSynchronizerModal" tabindex="-1"
       aria-labelledby="projectsSynchronizerModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
      <div class="modal-content modern-modal-content shadow">
        <div class="modal-header modern-modal-header border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-arrow-repeat fs-4 me-2 text-purple"></i>
            <h5 class="modal-title mb-0 text-dark" id="projectsSynchronizerModalLabel">Create a Projects Synchronization Job</h5>
          </div>
          <button type="button" class="btn-close" @click="closeProjectsSynchronizerModal"></button>
        </div>
        <div class="modal-body pt-4">
          <div class="modal-description-card mb-4">
            <div class="description-content">
              <p class="mb-2">Synchronizes <strong class="text-primary">workspaceInfo directories</strong> containing projects and its recordings with the projects created and maintained in Jeffrey.
                Based on the synchronization strategy, it automatically creates new projects or removes existing ones to keep consistency.</p>
              <p class="mb-0 text-muted">When an application creates its folder within a workspaceInfo directory and starts producing recordings, this job automatically handles the project initialization in Jeffrey.</p>
            </div>
          </div>
          <div class="mb-4 row">
            <label class="col-sm-3 col-form-label fw-medium">Workspaces Directory</label>
            <div class="col-sm-9">
              <div class="mb-2">
                <div class="form-check">
                  <input 
                    class="form-check-input" 
                    type="checkbox" 
                    id="useDefaultWorkspaceDir" 
                    v-model="useDefaultWorkspaceDir"
                  >
                  <label class="form-check-label small" for="useDefaultWorkspaceDir">
                    Use <span style="color: #dc3545;">JEFFREY_HOME</span> as default directory
                  </label>
                </div>
              </div>
              <div class="input-group">
                <span class="input-group-text border-end-0"><i class="bi bi-folder"></i></span>
                <input 
                  type="text" 
                  id="workspaceDir" 
                  class="form-control border-start-0" 
                  v-model="dialogSyncRepositoriesDir"
                  :disabled="useDefaultWorkspaceDir"
                  :placeholder="useDefaultWorkspaceDir ? 'Using JEFFREY_HOME as default' : 'Enter custom workspaceInfo directory path'"
                  autocomplete="off"
                />
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label class="col-sm-3 col-form-label fw-medium">Synchronization</label>
            <div class="col-sm-9">
              <div class="d-flex gap-3">
                <div class="sync-option-card" 
                     :class="{'selected': dialogsyncMode === 'CREATE_ONLY'}"
                     @click="dialogsyncMode = 'CREATE_ONLY'">
                  <input class="form-check-input d-none" type="radio" name="syncMode" id="createOnly" value="CREATE_ONLY"
                         v-model="dialogsyncMode">
                  <div class="sync-option-content">
                    <div class="sync-option-header">
                      <i class="bi bi-plus-circle text-success me-2"></i>
                      <span class="sync-option-title">Create Only</span>
                    </div>
                    <div class="sync-option-description">
                      Only creates new projects when new folders are detected
                    </div>
                  </div>
                </div>
                <div class="sync-option-card" 
                     :class="{'selected': dialogsyncMode === 'FULL_SYNC'}"
                     @click="dialogsyncMode = 'FULL_SYNC'">
                  <input class="form-check-input d-none" type="radio" name="syncMode" id="fullSync" value="FULL_SYNC"
                         v-model="dialogsyncMode">
                  <div class="sync-option-content">
                    <div class="sync-option-header">
                      <i class="bi bi-arrow-repeat text-primary me-2"></i>
                      <span class="sync-option-title">Full Sync</span>
                    </div>
                    <div class="sync-option-description">
                      Creates new projects or removes existing ones to maintain consistency
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="mb-4 row" v-if="projectTemplates.length > 0">
            <label class="col-sm-3 col-form-label fw-medium">Project Template</label>
            <div class="col-sm-9">
              <div class="d-flex flex-wrap gap-2">
                <div v-for="template in projectTemplates" :key="template.id"
                     class="template-option p-2 rounded-3 border"
                     :class="{'selected': selectedTemplate === template.id}"
                     @click="selectTemplate(template.id)">
                  <div class="d-flex align-items-center">
                    <i class="bi bi-file-earmark-code text-primary me-2"></i>
                    <span>{{ template.name }}</span>
                  </div>
                </div>
              </div>
              <div class="text-muted small mt-2">
                <i class="bi bi-info-circle me-1"></i>Templates provide pre-configured settings for new projects
              </div>
            </div>
          </div>

        </div>
        <div v-if="dialogSyncMessages.length > 0" class="alert alert-danger mx-3 mb-3">
          <div v-for="(msg, idx) in dialogSyncMessages" :key="idx">
            <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ msg.content }}
          </div>
        </div>

        <div class="modal-footer border-top-0">
          <button type="button" class="btn btn-light" @click="closeProjectsSynchronizerModal">
            Cancel
          </button>
          <button type="button" class="btn btn-primary" @click="createProjectsSynchronizerJob">
            <i class="bi bi-save me-1"></i> Save Job
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {nextTick, onMounted, ref, watch} from 'vue';
import ToastService from '@/services/ToastService';
import Utils from "@/services/Utils";
import ProjectsClient from "@/services/ProjectsClient.ts";
import ProjectTemplateInfo from "@/services/project/model/ProjectTemplateInfo.ts";
import TemplateTarget from "@/services/model/TemplateTarget.ts";
import GlobalSchedulerClient from "@/services/GlobalSchedulerClient.ts";
import JobInfo from "@/services/model/JobInfo.ts";
import JobCard from "@/components/JobCard.vue";
import * as bootstrap from 'bootstrap';

// State for Scheduler Jobs
const jobSearchQuery = ref('');
const jobsLoading = ref(false);
const jobsErrorMessage = ref('');
const showProjectsSynchronizerModal = ref(false);
const globalJobs = ref<JobInfo[]>([]);
const projectsSyncJobAlreadyExists = ref(false);

// Modal references for bootstrap
let projectsSynchronizerModalInstance: bootstrap.Modal | null = null;

// Form data for Projects Synchronization
const dialogSyncRepositoriesDir = ref('');
const useDefaultWorkspaceDir = ref(true);
const dialogsyncMode = ref('CREATE_ONLY');
const dialogSyncMessages = ref<{ severity: string, content: string }[]>([]);
const projectTemplates = ref<ProjectTemplateInfo[]>([]);
const selectedTemplate = ref<string | null>(null);

// We'll load the jobs from the API, so we don't need sample data anymore

// Load project templates
const loadTemplates = async () => {
  try {
    projectTemplates.value = await ProjectsClient.templates(TemplateTarget.GLOBAL_SCHEDULER);
  } catch (error) {
    console.error('Failed to load project templates:', error);
  }
};

// Select template
const selectTemplate = (templateId: string) => {
  selectedTemplate.value = selectedTemplate.value === templateId ? null : templateId;
};

// Fetch projects on component mount
onMounted(() => {
  refreshJobs();
  loadTemplates();

  // Initialize Bootstrap modals after the DOM is ready
  nextTick(() => {
    const projectsSynchronizerModalEl = document.getElementById('projectsSynchronizerModal');
    if (projectsSynchronizerModalEl) {
      projectsSynchronizerModalEl.addEventListener('hidden.bs.modal', () => {
        showProjectsSynchronizerModal.value = false;
      });

      const closeButton = projectsSynchronizerModalEl.querySelector('.btn-close');
      if (closeButton) {
        closeButton.addEventListener('click', closeProjectsSynchronizerModal);
      }

      // Initialize tooltips
      const tooltipTriggerList = projectsSynchronizerModalEl.querySelectorAll('[data-bs-toggle="tooltip"]');
      Array.from(tooltipTriggerList).forEach(tooltipTriggerEl => {
        new bootstrap.Tooltip(tooltipTriggerEl);
      });
    }
  });
});

// Check if a Projects Synchronization job already exists
const alreadyContainsProjectsSyncJob = (jobs: any[]) => {
  // Reset the flag first
  projectsSyncJobAlreadyExists.value = false;

  // Check if any job is a projects sync job
  for (let job of jobs) {
    if (job.jobType === 'PROJECTS_SYNCHRONIZER') {
      projectsSyncJobAlreadyExists.value = true;
      break;
    }
  }
};

// Toggle job enabled/disabled state
const toggleJobEnabled = async (job: JobInfo) => {
  try {
    // Toggle the enabled state
    await GlobalSchedulerClient.updateEnabled(job.id, !job.enabled);

    // Refresh the job list to get updated state
    await refreshJobs();

    ToastService.success('Enable Switch', `Job ${job.enabled ? 'disabled' : 'enabled'} successfully`);
  } catch (error: any) {
    console.error('Failed to update job state:', error);
    ToastService.error('errorToast', error.response?.data || 'Failed to update job state');
  }
};

// Refresh jobs list
const refreshJobs = async () => {
  jobsLoading.value = true;
  jobsErrorMessage.value = '';

  try {
    // Load jobs from API
    globalJobs.value = await GlobalSchedulerClient.all();

    // Check if a Projects Synchronization job already exists
    alreadyContainsProjectsSyncJob(globalJobs.value);
  } catch (error) {
    ToastService.error('Failed to load jobs', 'Cannot load the jobs for Global Scheduler');
  } finally {
    jobsLoading.value = false;
  }
};

// Functions to close the sync modal
const closeProjectsSynchronizerModal = () => {
  if (projectsSynchronizerModalInstance) {
    projectsSynchronizerModalInstance.hide();
  }
  showProjectsSynchronizerModal.value = false;
  resetSyncForm();
};

// Reset the form to default values
function resetSyncForm() {
  dialogSyncRepositoriesDir.value = '';
  useDefaultWorkspaceDir.value = true;
  dialogsyncMode.value = 'CREATE_ONLY';
  selectedTemplate.value = projectTemplates.value.length > 0 ? projectTemplates.value[0].id : null;
  dialogSyncMessages.value = [];
}

// Watch for changes to modal visibility flags
watch(showProjectsSynchronizerModal, (isVisible) => {
  if (isVisible) {
    if (!projectsSynchronizerModalInstance) {
      const modalEl = document.getElementById('projectsSynchronizerModal');
      if (modalEl) {
        projectsSynchronizerModalInstance = new bootstrap.Modal(modalEl);
      }
    }

    if (projectsSynchronizerModalInstance) {
      resetSyncForm();
      projectsSynchronizerModalInstance.show();
      // Reinitialize tooltips when modal is shown
      nextTick(() => {
        const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]');
        Array.from(tooltipTriggerList).forEach(tooltipTriggerEl => {
          new bootstrap.Tooltip(tooltipTriggerEl);
        });
      });
    }
  } else {
    if (projectsSynchronizerModalInstance) {
      projectsSynchronizerModalInstance.hide();
    }
  }
});

// Create a new projects synchronizer job
const createProjectsSynchronizerJob = async () => {
  // Validate form
  if (!useDefaultWorkspaceDir.value && Utils.isBlank(dialogSyncRepositoriesDir.value)) {
    dialogSyncMessages.value = [{severity: 'error', content: 'Custom workspaceInfo directory path is required'}];
    return;
  }

  dialogSyncMessages.value = [];

  try {
    const params: any = {
      workspacesDir: useDefaultWorkspaceDir.value ? null : dialogSyncRepositoriesDir.value.trim(),
      syncMode: dialogsyncMode.value
    };

    // Add templateId to params if a template is selected
    if (selectedTemplate.value) {
      params.templateId = selectedTemplate.value;
    }

    // Call API to create job
    await GlobalSchedulerClient.create('PROJECTS_SYNCHRONIZER', params);

    // Refresh the job list
    await refreshJobs();

    ToastService.success('Global job created', 'Project synchronization job created successfully');

    // Reset form and close modal
    resetSyncForm();
    closeProjectsSynchronizerModal();
  } catch (error: any) {
    console.error('Failed to create sync job:', error);
    dialogSyncMessages.value = [{
      severity: 'error',
      content: error.response?.data || 'Failed to create job. Please try again.'
    }];
  }
};

// Delete a global job
const deleteGlobalJob = async (id: string) => {
  try {
    // Call API to delete job
    await GlobalSchedulerClient.delete(id);

    // Refresh the job list
    await refreshJobs();

    ToastService.success('Global job deleted', 'Global job successfully deleted');
  } catch (error) {
    ToastService.error('Deletion Failed', 'Global job deletion failed');
  }
};

// Handle job creation from JobCard component
const handleCreateJob = (jobType: string) => {
  switch (jobType) {
    case 'PROJECTS_SYNCHRONIZER':
      showProjectsSynchronizerModal.value = true;
      break;
  }
};

// Filter jobs based on search query
const filterJobs = async () => {
  if (!jobSearchQuery.value.trim()) {
    // If search query is empty, reload all jobs
    await refreshJobs();
    return;
  }

  // Apply client-side filtering on the loaded jobs
  const allJobs = await GlobalSchedulerClient.all();
  const query = jobSearchQuery.value.toLowerCase();

  globalJobs.value = allJobs.filter(job =>
      job.jobType.toLowerCase().includes(query) ||
      Object.values(job.params).some(value =>
          value.toString().toLowerCase().includes(query)
      )
  );

  // Update the job existence flag after filtering
  alreadyContainsProjectsSyncJob(globalJobs.value);
};
</script>

<style scoped>
.search-box {
  flex: 1;
  max-width: 600px;
}

.phoenix-search {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.12);
  overflow: hidden;
  border-radius: 12px;
  height: 48px;
  box-shadow: 
    inset 0 1px 3px rgba(0, 0, 0, 0.05),
    0 1px 2px rgba(0, 0, 0, 0.02);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:focus-within {
    border-color: rgba(94, 100, 255, 0.3);
    box-shadow: 
      inset 0 1px 3px rgba(0, 0, 0, 0.05),
      0 4px 12px rgba(94, 100, 255, 0.1),
      0 0 0 3px rgba(94, 100, 255, 0.05);
    transform: translateY(-1px);
  }

  .search-icon-container {
    width: 48px;
    display: flex;
    justify-content: center;
    background: transparent;
    border: none;
    color: #6c757d;
  }

  .form-control {
    height: 46px;
    font-size: 0.9rem;
    background: transparent;
    border: none;
    color: #374151;
    font-weight: 500;

    &::placeholder {
      color: #9ca3af;
      font-weight: 400;
    }

    &:focus {
      box-shadow: none;
      background: transparent;
    }
  }
}


/* Job styles */
.job-icon-sm {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 0.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bg-purple-soft {
  background-color: rgba(111, 66, 193, 0.15);
}

.text-purple {
  color: #6f42c1;
}

.inline-params {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.param-badge {
  display: inline-flex;
  align-items: center;
  background-color: #f8f9fa;
  border-radius: 0.125rem;
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  color: #495057;
  border: 1px solid #e9ecef;
}

.param-key {
  font-weight: 600;
  margin-right: 0.25rem;
  color: #6c757d;
}

.param-value {
  color: #212529;
}

.empty-state-icon {
  font-size: 3rem;
  color: #ced4da;
  margin-bottom: 1rem;
}

.fw-medium {
  font-weight: 500;
}

.fw-semibold {
  font-weight: 600;
}

.btn-light {
  background: linear-gradient(135deg, #f8f9fa, #e9ecef);
  border: 1px solid rgba(108, 117, 125, 0.2);
  color: #6c757d;
  font-weight: 500;
  border-radius: 10px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:hover {
    background: linear-gradient(135deg, #e9ecef, #dee2e6);
    color: #495057;
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }
}

.btn-primary {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border: none;
  font-weight: 600;
  border-radius: 10px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 4px 12px rgba(94, 100, 255, 0.3),
    0 2px 4px rgba(94, 100, 255, 0.2);
  
  &:hover {
    background: linear-gradient(135deg, #4c52ff, #3f46ff);
    transform: translateY(-2px);
    box-shadow: 
      0 6px 16px rgba(94, 100, 255, 0.4),
      0 3px 6px rgba(94, 100, 255, 0.3);
  }

  &:active {
    transform: translateY(-1px);
  }
}

/* Modal input styling */
.modal .input-group-text {
  background-color: #fff;
  color: #6c757d;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  border: 1px solid #ced4da;
}

.modal .form-control {
  border: 1px solid #ced4da;
  height: 38px;
}

.modal .form-control:focus {
  box-shadow: none;
  border-color: #ced4da;
}

.modal .input-group {
  flex-wrap: nowrap;
}

.tooltip-icon {
  cursor: pointer;
  transition: color 0.2s ease;
}

.tooltip-icon:hover {
  color: #0d6efd !important;
}

.template-option {
  cursor: pointer;
  transition: all 0.2s ease;
  background-color: #f8f9fa;

  &:hover {
    background-color: #eef2ff;
    border-color: #d1d9ff !important;
  }

  &.selected {
    background-color: #eef2ff;
    border-color: #6f42c1 !important;
    box-shadow: 0 0 0 1px rgba(111, 66, 193, 0.15);
  }
}

/* Disabled job styling */
.disabled-job {
  background-color: rgba(0, 0, 0, 0.03);
  opacity: 0.75;
}

.disabled-job td {
  color: #6c757d;
}

.disabled-job .param-badge {
  background-color: #f1f3f5;
  border-color: #dee2e6;
}

/* Modern Card Styling */
.job-types-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 
    0 4px 20px rgba(0, 0, 0, 0.04),
    0 1px 3px rgba(0, 0, 0, 0.02);
  backdrop-filter: blur(10px);
}

.job-types-content {
  padding: 24px 28px;
}

.jobs-main-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 
    0 4px 20px rgba(0, 0, 0, 0.04),
    0 1px 3px rgba(0, 0, 0, 0.02);
  backdrop-filter: blur(10px);
}

.jobs-main-content {
  padding: 24px 28px;
}

/* Modern Modal Styling */
.modern-modal-content {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 
    0 20px 40px rgba(0, 0, 0, 0.08),
    0 8px 24px rgba(0, 0, 0, 0.06);
  backdrop-filter: blur(10px);
}

.modern-modal-header {
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.05), rgba(94, 100, 255, 0.08));
  border-radius: 16px 16px 0 0;
  padding: 20px 24px;
}

/* Modal Description Card */
.modal-description-card {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  padding: 0;
  box-shadow: 
    0 2px 8px rgba(0, 0, 0, 0.04),
    0 1px 2px rgba(0, 0, 0, 0.02);
}

.description-content {
  padding: 20px 24px;
}

.description-content p {
  font-size: 0.9rem;
  line-height: 1.5;
  color: #374151;
}

.description-content .text-muted {
  font-size: 0.85rem;
  font-style: italic;
}

/* Modern Sync Option Cards */
.sync-option-card {
  flex: 1;
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 2px 8px rgba(0, 0, 0, 0.04),
    0 1px 2px rgba(0, 0, 0, 0.02);

  &:hover:not(.selected) {
    transform: translateY(-2px);
    box-shadow: 
      0 6px 16px rgba(0, 0, 0, 0.06),
      0 2px 8px rgba(94, 100, 255, 0.1);
    border-color: rgba(94, 100, 255, 0.2);
  }

  &.selected {
    background: linear-gradient(135deg, #eef2ff, #f8faff);
    border-color: #5e64ff;
    transform: translateY(-1px);
    box-shadow: 
      0 6px 20px rgba(94, 100, 255, 0.15),
      0 2px 8px rgba(94, 100, 255, 0.1);
  }
}

.sync-option-content {
  text-align: center;
}

.sync-option-header {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}

.sync-option-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
}

.sync-option-description {
  font-size: 0.75rem;
  color: #6b7280;
  line-height: 1.4;
}

.sync-option-card.selected .sync-option-title {
  color: #5e64ff;
}

.sync-option-card.selected .sync-option-description {
  color: #4338ca;
}
</style>
