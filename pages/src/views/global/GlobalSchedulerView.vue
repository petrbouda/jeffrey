<template>
  <div>
    <!-- Job Types Card -->
    <div class="main-card mb-4">
      <div class="main-card-content">
        <!-- Loading state for plugins -->
        <LoadingState v-if="!pluginsLoaded" message="Loading available job types..." />

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
    <div class="main-card mb-4">
      <div class="main-card-header">
        <i class="bi bi-clock-history main-card-header-icon"></i>
        <h5 class="main-card-header-title">Active Global Jobs</h5>
      </div>
      <div class="main-card-content">
        <div class="d-flex align-items-center mb-4 gap-3">
          <SearchBox
            v-model="jobSearchQuery"
            placeholder="Search scheduled jobs..."
            @update:model-value="filterJobs"
          />
        </div>

        <!-- Loading indicator -->
        <LoadingState v-if="jobsLoading" message="Loading scheduled jobs..." />

        <!-- Error state -->
        <div v-else-if="jobsErrorMessage" class="error-state">
          <i class="bi bi-exclamation-triangle-fill"></i>
          <h5>Failed to load jobs</h5>
          <p>{{ jobsErrorMessage }}</p>
          <button class="btn btn-primary" @click="refreshJobs">
            <i class="bi bi-arrow-clockwise me-2"></i>Retry
          </button>
        </div>

        <!-- Jobs table -->
        <JobsTable
          v-else-if="globalJobs.length > 0"
          :jobs="globalJobs"
          :get-job-display-info="getJobDisplayInfoAdapter"
          @toggle-enabled="toggleJobEnabled"
          @delete="deleteGlobalJob"
        />

        <!-- Empty state for global jobs -->
        <EmptyState
          v-else
          icon="bi-calendar-x"
          title="No Active Global Jobs"
          description="There are no active global scheduled jobs. Create a new job using the options above."
        />
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
import GlobalSchedulerClient from '@/services/api/GlobalSchedulerClient';
import JobInfo from '@/services/api/model/JobInfo';
import JobCard from '@/components/JobCard.vue';
import JobsTable, { type JobDisplayInfo } from '@/components/scheduler/JobsTable.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import SearchBox from '@/components/SearchBox.vue';
import { jobPluginRegistry } from '@/services/scheduler/JobPluginRegistry';
import { setupJobPlugins } from '@/services/scheduler/pluginSetup';
import '@/styles/shared-components.css';

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
      modalRef.setValidationErrors([error.response?.data || 'Failed to create job.']);
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

// Adapter for JobsTable - converts plugin info to JobDisplayInfo
const getJobDisplayInfoAdapter = (job: JobInfo): JobDisplayInfo | null => {
  const plugin = jobPluginRegistry.getPlugin(job.jobType);
  if (!plugin) return null;

  const displayInfo = plugin.getJobDisplayInfo(job);
  if (!displayInfo) return null;

  return {
    title: displayInfo.title,
    icon: displayInfo.icon,
    iconColor: displayInfo.iconColor,
    iconBg: displayInfo.iconBg
  };
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
.fw-medium {
  font-weight: 500;
}
</style>
