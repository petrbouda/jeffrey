<template>
  <div>
    <!-- Job Types Card -->
    <div class="card mb-4 border-0">
      <div class="card-body p-4">
        <div class="row row-cols-1 row-cols-md-2 row-cols-lg-3 g-4">
          <!-- Projects Synchronization -->
          <div class="col">
            <JobCard 
              job-type="PROJECTS_SYNCHRONIZER"
              title="Projects Synchronization"
              description="Synchronizes Watched Folder with the current list of projects in Jeffrey based on Synchronization Strategy"
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
    <div class="card mb-4">
      <div class="card-body">
        <div class="search-container mb-4">
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
        <div class="table-responsive">
          <table class="table table-hover mb-0" v-if="globalJobs.length > 0">
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

          <!-- Empty state for global jobs -->
          <div class="text-center py-5" v-if="globalJobs.length === 0">
            <div class="empty-state-icon mb-3">
              <i class="bi bi-calendar-x fs-1 text-muted"></i>
            </div>
            <h6 class="fw-semibold">No Active Global Jobs</h6>
            <p class="text-muted mb-0">
              There are no active global scheduled jobs. Create a new job using the options above.
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Projects Synchronization Modal -->
  <div class="modal fade" id="globalSyncModal" tabindex="-1"
       aria-labelledby="globalSyncModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
      <div class="modal-content rounded-1 shadow">
        <div class="modal-header bg-purple-soft border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-arrow-repeat fs-4 me-2 text-purple"></i>
            <h5 class="modal-title mb-0 text-dark" id="globalSyncModalLabel">Create a Projects Synchronization Job</h5>
          </div>
          <button type="button" class="btn-close" @click="closeGlobalSyncModal"></button>
        </div>
        <div class="modal-body pt-4">
          <p class="text-muted mb-3">Synchronizes <b>Watched Folder</b> dedicated for project's recordings with a list
            of projects in Jeffrey.
            Based on synchronization strategy, it automatically creates a new projects, or removes the existing ones.
            <br/>An application creates
            its folder inside the Watched Folder, starts producing the recordings and this job automatically handles
            project's initialization in Jeffrey.</p>
          <div class="mb-4 row">
            <label for="watchedFolder" class="col-sm-3 col-form-label fw-medium">Watched Folder</label>
            <div class="col-sm-9">
              <div class="input-group">
                <span class="input-group-text border-end-0"><i class="bi bi-folder"></i></span>
                <input type="text" id="watchedFolder" class="form-control border-start-0"
                       v-model="dialogSyncWatchedFolder" placeholder="Enter watched folder path" autocomplete="off"/>
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label class="col-sm-3 col-form-label fw-medium">Sync Strategy</label>
            <div class="col-sm-9">
              <div class="form-check mb-2">
                <input class="form-check-input" type="radio" name="syncType" id="createOnly" value="CREATE_ONLY"
                       v-model="dialogSyncType">
                <label class="form-check-label" for="createOnly">
                  Only creates new projects
                  <i class="bi bi-info-circle-fill text-primary ms-1 small tooltip-icon" data-bs-toggle="tooltip"
                     data-bs-placement="right" title="Only creates new projects in case of new folders"></i>
                </label>
              </div>
              <div class="form-check">
                <input class="form-check-input" type="radio" name="syncType" id="fullSync" value="FULL_SYNC"
                       v-model="dialogSyncType">
                <label class="form-check-label" for="fullSync">
                  Full synchronization
                  <i class="bi bi-info-circle-fill text-primary ms-1 small tooltip-icon" data-bs-toggle="tooltip"
                     data-bs-placement="right"
                     title="Creates a new project in case of a new folder, or removes the existing project in Jeffrey if a folder is missing"></i>
                </label>
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
          <button type="button" class="btn btn-light" @click="closeGlobalSyncModal">Cancel</button>
          <button type="button" class="btn btn-primary" @click="createGlobalSyncJob">
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
const showGlobalSyncModal = ref(false);
const globalJobs = ref<JobInfo[]>([]);
const projectsSyncJobAlreadyExists = ref(false);

// Modal references for bootstrap
let globalSyncModalInstance: bootstrap.Modal | null = null;

// Form data for Projects Synchronization
const dialogSyncWatchedFolder = ref('');
const dialogSyncType = ref('CREATE_ONLY');
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
    const globalSyncModalEl = document.getElementById('globalSyncModal');
    if (globalSyncModalEl) {
      globalSyncModalEl.addEventListener('hidden.bs.modal', () => {
        showGlobalSyncModal.value = false;
      });

      const closeButton = globalSyncModalEl.querySelector('.btn-close');
      if (closeButton) {
        closeButton.addEventListener('click', closeGlobalSyncModal);
      }

      // Initialize tooltips
      const tooltipTriggerList = globalSyncModalEl.querySelectorAll('[data-bs-toggle="tooltip"]');
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
  } catch (error) {
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
const closeGlobalSyncModal = () => {
  if (globalSyncModalInstance) {
    globalSyncModalInstance.hide();
  }
  showGlobalSyncModal.value = false;
  resetSyncForm();
};

// Reset the form to default values
function resetSyncForm() {
  dialogSyncWatchedFolder.value = '';
  dialogSyncType.value = 'CREATE_ONLY';
  selectedTemplate.value = projectTemplates.value.length > 0 ? projectTemplates.value[0].id : null;
  dialogSyncMessages.value = [];
}

// Watch for changes to modal visibility flags
watch(showGlobalSyncModal, (isVisible) => {
  if (isVisible) {
    if (!globalSyncModalInstance) {
      const modalEl = document.getElementById('globalSyncModal');
      if (modalEl) {
        globalSyncModalInstance = new bootstrap.Modal(modalEl);
      }
    }

    if (globalSyncModalInstance) {
      resetSyncForm();
      globalSyncModalInstance.show();
      // Reinitialize tooltips when modal is shown
      nextTick(() => {
        const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]');
        Array.from(tooltipTriggerList).forEach(tooltipTriggerEl => {
          new bootstrap.Tooltip(tooltipTriggerEl);
        });
      });
    }
  } else {
    if (globalSyncModalInstance) {
      globalSyncModalInstance.hide();
    }
  }
});

// Create a new global sync job
const createGlobalSyncJob = async () => {
  // Validate form
  if (Utils.isBlank(dialogSyncWatchedFolder.value)) {
    dialogSyncMessages.value = [{severity: 'error', content: 'Watched folder is required'}];
    return;
  }

  dialogSyncMessages.value = [];

  try {
    const params: any = {
      watchedFolder: dialogSyncWatchedFolder.value.trim(),
      syncType: dialogSyncType.value
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
    closeGlobalSyncModal();
  } catch (error) {
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
      showGlobalSyncModal.value = true;
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
.search-container {
  display: flex;
  gap: 10px;
  width: 100%;
  box-shadow: none;
}

.search-box {
  flex: 1;
  width: 100%;
}

.phoenix-search {
  border: 1px solid #e0e5eb;
  overflow: hidden;
  box-shadow: none;
  border-radius: 8px;
  height: 42px;

  .search-icon-container {
    width: 40px;
    display: flex;
    justify-content: center;
    background-color: transparent;
  }

  .form-control {
    height: 40px;
    font-size: 0.9rem;

    &:focus {
      box-shadow: none;
    }
  }
}

/* Job Card Styles */
.job-card {
  transition: all 0.2s ease;
  border-color: #e9ecef !important;
  border-radius: 0.25rem;
}

.job-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.08) !important;
  border-color: #dee2e6 !important;
}

/* Coming Soon job cards */
.coming-soon-card {
  background-color: #f8f9fa;
  opacity: 0.7;
  border-color: #dee2e6 !important;
}

.job-icon {
  width: 56px;
  height: 56px;
  min-width: 56px;
  font-size: 1.5rem;
  border-radius: 0.45rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bg-soft-blue {
  background-color: #5e64ff;
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
  background-color: #f8f9fa;
  border-color: #e9ecef;

  &:hover {
    background-color: #e9ecef;
  }
}

.btn-primary {
  transition: all 0.2s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
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
</style>
