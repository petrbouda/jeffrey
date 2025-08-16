<template>
  <div>
    <!-- Job Types Card -->
    <div class="job-types-card mb-4">
      <div class="job-types-content">
        <!-- Loading state for plugins -->
        <div v-if="!pluginsLoaded" class="text-center py-4">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading job types...</span>
          </div>
          <p class="mt-2">Loading available job types...</p>
        </div>
        
        <!-- Job type cards -->
        <div v-else class="row row-cols-1 row-cols-md-2 row-cols-lg-3 g-4">
          <div v-for="plugin in availablePlugins" :key="plugin.jobType" class="col">
            <JobCard
                :job-type="plugin.cardMetadata.jobType"
                :title="plugin.cardMetadata.title"
                :description="plugin.cardMetadata.description"
                :icon="plugin.cardMetadata.icon"
                :icon-color="plugin.cardMetadata.iconColor"
                :icon-bg="plugin.cardMetadata.iconBg"
                :disabled="plugin.jobExists(globalJobs)"
                :badges="[
                { text: 'Job already exists', color: 'bg-success', condition: plugin.jobExists(globalJobs) }
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
                  <template v-if="getJobDisplayInfo(job)">
                    <div class="job-icon-sm me-2 d-flex align-items-center justify-content-center"
                         :class="getJobDisplayInfo(job)?.iconBg">
                      <i class="bi" :class="[getJobDisplayInfo(job)?.icon, getJobDisplayInfo(job)?.iconColor]"></i>
                    </div>
                    <div>
                      <div class="fw-medium">
                        {{ getJobDisplayInfo(job)?.title }}
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

    <!-- Dynamic Job Modal Container -->
    <div v-for="plugin in availablePlugins" :key="plugin.jobType">
      <component
          :is="plugin.modalComponent"
          :modal-id="getModalId(plugin.jobType)"
          @job-created="(params: any) => createJob(plugin.jobType, params)"
          @modal-closed="handleModalClosed"
          :ref="(el) => setModalRef(plugin.jobType, el)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import ToastService from '@/services/ToastService';
import GlobalSchedulerClient from '@/services/GlobalSchedulerClient';
import JobInfo from '@/services/model/JobInfo';
import JobCard from '@/components/JobCard.vue';
import { jobPluginRegistry } from '@/services/scheduler/JobPluginRegistry';
import { setupJobPlugins } from '@/services/scheduler/pluginSetup';

// State for Scheduler Jobs
const jobSearchQuery = ref('');
const jobsLoading = ref(false);
const jobsErrorMessage = ref('');
const globalJobs = ref<JobInfo[]>([]);
const pluginsLoaded = ref(false);

// Modal references
const modalRefs = ref<Record<string, any>>({});

// Set modal ref
const setModalRef = (jobType: string, el: any) => {
  if (el) {
    modalRefs.value[jobType] = el;
  }
};

// Get available plugins - only return plugins when they're fully loaded
const availablePlugins = computed(() => {
  return pluginsLoaded.value ? jobPluginRegistry.getAllPlugins() : [];
});

// Get modal ID for a job type
const getModalId = (jobType: string) => {
  return `${jobType.toLowerCase()}Modal`;
};

// Get job display info from plugin
const getJobDisplayInfo = (job: JobInfo) => {
  const plugin = jobPluginRegistry.getPlugin(job.jobType);
  return plugin ? plugin.getJobDisplayInfo(job) : null;
};

// Toggle job enabled/disabled state
const toggleJobEnabled = async (job: JobInfo) => {
  try {
    await GlobalSchedulerClient.updateEnabled(job.id, !job.enabled);
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
    globalJobs.value = await GlobalSchedulerClient.all();
  } catch (error) {
    ToastService.error('Failed to load jobs', 'Cannot load the jobs for Global Scheduler');
  } finally {
    jobsLoading.value = false;
  }
};

// Create job through plugin
const createJob = async (jobType: string, params: any) => {
  try {
    const plugin = jobPluginRegistry.getPlugin(jobType);
    if (!plugin) {
      throw new Error(`Plugin not found for job type: ${jobType}`);
    }

    // Validate job creation parameters
    const validationResult = await plugin.validateJobCreation(params);
    if (!validationResult.isValid) {
      // Set validation errors on the modal
      const modalRef = modalRefs.value[jobType];
      if (modalRef && modalRef.setValidationErrors) {
        modalRef.setValidationErrors(validationResult.errors);
      }
      return;
    }

    // Create job via API
    await GlobalSchedulerClient.create(jobType, params);

    // Refresh the job list
    await refreshJobs();

    ToastService.success('Global job created', `${plugin.cardMetadata.title} job created successfully`);

    // Close modal
    const modalRef = modalRefs.value[jobType];
    if (modalRef && modalRef.closeModal) {
      modalRef.closeModal();
    }
  } catch (error: any) {
    console.error('Failed to create job:', error);
    
    // Show error on modal
    const modalRef = modalRefs.value[jobType];
    if (modalRef && modalRef.setValidationErrors) {
      modalRef.setValidationErrors([error.response?.data || 'Failed to create job. Please try again.']);
    }
  }
};

// Delete a global job
const deleteGlobalJob = async (id: string) => {
  try {
    await GlobalSchedulerClient.delete(id);
    await refreshJobs();
    ToastService.success('Global job deleted', 'Global job successfully deleted');
  } catch (error) {
    ToastService.error('Deletion Failed', 'Global job deletion failed');
  }
};

// Handle job creation from JobCard component
const handleCreateJob = (jobType: string) => {
  const modalRef = modalRefs.value[jobType];
  if (modalRef && modalRef.showModal) {
    modalRef.showModal();
  }
};

// Handle modal closed
const handleModalClosed = () => {
  // Modal closed, nothing specific to do
};

// Filter jobs based on search query
const filterJobs = async () => {
  if (!jobSearchQuery.value.trim()) {
    await refreshJobs();
    return;
  }

  const allJobs = await GlobalSchedulerClient.all();
  const query = jobSearchQuery.value.toLowerCase();

  globalJobs.value = allJobs.filter(job =>
      job.jobType.toLowerCase().includes(query) ||
      Object.values(job.params).some(value =>
          value.toString().toLowerCase().includes(query)
      )
  );
};

// Component lifecycle
onMounted(async () => {
  try {
    await setupJobPlugins();
    pluginsLoaded.value = true;
    await refreshJobs();
  } catch (error) {
    console.error('Failed to setup plugins:', error);
    pluginsLoaded.value = true; // Still set to true to prevent infinite loading
  }
});
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

.fw-medium {
  font-weight: 500;
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
</style>