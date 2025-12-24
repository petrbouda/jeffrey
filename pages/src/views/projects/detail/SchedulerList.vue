<!--
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 -->

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useNavigation } from '@/composables/useNavigation';
import ProjectSchedulerClient from "@/services/project/ProjectSchedulerClient.ts";
import ProjectSettingsClient from "@/services/project/ProjectSettingsClient.ts";
import JobInfo from "@/services/model/JobInfo.ts";
import { JobType } from "@/services/model/JobType.ts";
import SettingsResponse from "@/services/project/model/SettingsResponse.ts";
import ToastService from "@/services/ToastService";
import MessageBus from "@/services/MessageBus";
import JobCard from "@/components/JobCard.vue";
import RepositorySessionCleanerModal from "@/components/scheduler/modal/RepositorySessionCleanerModal.vue";
import PeriodicRecordingGeneratorModal from "@/components/scheduler/modal/PeriodicRecordingGeneratorModal.vue";
import CopyRecordingGeneratorModal from "@/components/scheduler/modal/CopyRecordingGeneratorModal.vue";
import PageHeader from '@/components/layout/PageHeader.vue';
import JobsTable, { type JobDisplayInfo } from '@/components/scheduler/JobsTable.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import '@/styles/shared-components.css';

const { workspaceId, projectId } = useNavigation();
const currentProject = ref<SettingsResponse | null>(null);

const schedulerService = new ProjectSchedulerClient(workspaceId.value!, projectId.value!)
const settingsService = new ProjectSettingsClient(workspaceId.value!, projectId.value!)

// Modal visibility state
const showRepositorySessionCleanerModal = ref(false);
const showPeriodicRecordingGeneratorModal = ref(false);
const showCopyRecordingGeneratorModal = ref(false);

const activeJobs = ref<JobInfo[]>([])
const cleanerJobAlreadyExists = ref(false)
const copyGeneratorJobAlreadyExists = ref(false)
const jfrCompressionJobExists = ref(false)
const recordingStorageSynchronizerJobExists = ref(false)

const isLoading = ref(false);

onMounted(async () => {
  isLoading.value = true;

  try {
    // Update job list
    await updateJobList();

    // Get project settings
    await settingsService.get()
      .then((data) => {
        currentProject.value = data;
      })
      .catch((error) => {
        console.error('Failed to load project settings:', error);
      });
  } finally {
    isLoading.value = false;
  }
});

function checkForExistingJobs(jobs: JobInfo[]) {
  // Reset the flags first
  cleanerJobAlreadyExists.value = false;
  copyGeneratorJobAlreadyExists.value = false;
  jfrCompressionJobExists.value = false;
  recordingStorageSynchronizerJobExists.value = false;

  // Check for existing jobs by type
  for (let job of jobs) {
    if (job.jobType === JobType.REPOSITORY_SESSION_CLEANER) {
      cleanerJobAlreadyExists.value = true;
    } else if (job.jobType === JobType.COPY_RECORDING_GENERATOR) {
      copyGeneratorJobAlreadyExists.value = true;
    } else if (job.jobType === JobType.REPOSITORY_JFR_COMPRESSION) {
      jfrCompressionJobExists.value = true;
    } else if (job.jobType === JobType.PROJECT_RECORDING_STORAGE_SYNCHRONIZER) {
      recordingStorageSynchronizerJobExists.value = true;
    }
  }
}

async function updateJobList() {
  try {
    const data = await schedulerService.all();
    checkForExistingJobs(data);
    activeJobs.value = data;

    // Emit job count change event for the sidebar to update
    MessageBus.emit(MessageBus.JOBS_COUNT_CHANGED, data.length);

    return data;
  } catch (error) {
    console.error('Failed to load active jobs:', error);
    return [];
  }
}

// Handle modal saved events
const handleModalSaved = async () => {
  await updateJobList();
};

async function toggleJobEnabled(job: JobInfo) {
  try {
    // Toggle the enabled state
    await schedulerService.updateEnabled(job.id, !job.enabled);

    // Refresh the job list to get updated state
    await updateJobList();

    ToastService.success('Job Updated', job.enabled ? 'Job has been disabled' : 'Job has been enabled');
  } catch (error: any) {
    console.error('Failed to update job state:', error);
    ToastService.error('Update Failed', error.response?.data || 'Failed to update job state.');
  }
}

async function deleteActiveTask(id: string) {
  try {
    await schedulerService.delete(id);

    // Update the job list and refresh card states
    await updateJobList();

    ToastService.success('Job Deleted', 'The job has been removed');
  } catch (error: any) {
    console.error('Failed to delete job:', error);
    ToastService.error('Delete Failed', error.response?.data || 'Failed to delete job.');
  }
}

// Handle job creation from JobCard component
const handleCreateJob = (jobType: string) => {
  switch (jobType) {
    case JobType.REPOSITORY_SESSION_CLEANER:
      showRepositorySessionCleanerModal.value = true;
      break;
    case JobType.REPOSITORY_RECORDING_CLEANER:
      showRepositorySessionCleanerModal.value = true;
      break;
    case JobType.COPY_RECORDING_GENERATOR:
      showCopyRecordingGeneratorModal.value = true;
      break;
    case JobType.PERIODIC_RECORDING_GENERATOR:
      showPeriodicRecordingGeneratorModal.value = true;
      break;
  }
};

// Get display info for jobs in the table
const getJobDisplayInfo = (job: JobInfo): JobDisplayInfo | null => {
  switch (job.jobType) {
    case JobType.REPOSITORY_SESSION_CLEANER:
      return {
        title: 'Repository Session Cleaner',
        icon: 'bi-trash',
        iconColor: 'text-teal',
        iconBg: 'bg-teal-soft'
      };
    case JobType.REPOSITORY_RECORDING_CLEANER:
      return {
        title: 'Repository Recording Cleaner',
        icon: 'bi-trash',
        iconColor: 'text-teal',
        iconBg: 'bg-teal-soft'
      };
    case JobType.PERIODIC_RECORDING_GENERATOR:
      return {
        title: 'Periodic Recording Generator',
        icon: 'bi-arrow-repeat',
        iconColor: 'text-blue',
        iconBg: 'bg-blue-soft'
      };
    case JobType.COPY_RECORDING_GENERATOR:
      return {
        title: 'Download Recording Generator',
        icon: 'bi-clock-history',
        iconColor: 'text-blue',
        iconBg: 'bg-blue-soft'
      };
    case JobType.REPOSITORY_JFR_COMPRESSION:
      return {
        title: 'JFR Compression',
        icon: 'bi-file-zip',
        iconColor: 'text-orange',
        iconBg: 'bg-orange-soft'
      };
    case JobType.PROJECT_RECORDING_STORAGE_SYNCHRONIZER:
      return {
        title: 'Recording Storage Synchronizer',
        icon: 'bi-arrow-repeat',
        iconColor: 'text-purple',
        iconBg: 'bg-purple-soft'
      };
    default:
      return null;
  }
};
</script>

<template>
  <PageHeader
    title="Scheduler"
    description="Creates periodical jobs to manage data belonging to the given project, such as removing unnecessary old files from the repository."
    icon="bi-calendar-check"
  >
    <!-- Job Types -->
    <div class="col-12 mb-4">
      <div class="row g-4">
        <!-- Repository Session Cleaner -->
        <div class="col-12 col-lg-6">
          <JobCard
                  :job-type="JobType.REPOSITORY_SESSION_CLEANER"
                  title="Repository Session Cleaner"
                  description="Task for removing Repository Session older than the configured duration. Once a Repository Session is removed, all associated Recordings and Additional Files (e.g. HeapDup, PerfCounters, ...) are being removed as well."
                  icon="bi-trash"
                  icon-color="text-teal"
                  icon-bg="bg-teal-soft"
                  :disabled="cleanerJobAlreadyExists"
                  :badges="[
                  { text: 'Job already exists', color: 'bg-success', condition: cleanerJobAlreadyExists }
                ]"
                  @create-job="handleCreateJob"
              />
            </div>

            <!-- Repository Recording Cleaner -->
            <div class="col-12 col-lg-6">
              <JobCard
                  :job-type="JobType.REPOSITORY_RECORDING_CLEANER"
                  title="Repository Recording Cleaner"
                  description="Task for removing only Recording in the active (latest) Repository Session. It does not remove recordings in older sessions, it just ensure that the rolling recordings in the latest Repository Session are limited."
                  icon="bi-trash"
                  icon-color="text-teal"
                  icon-bg="bg-teal-soft"
                  :disabled="cleanerJobAlreadyExists"
                  :badges="[
                  { text: 'Job already exists', color: 'bg-success', condition: cleanerJobAlreadyExists }
                ]"
                  @create-job="handleCreateJob"
              />
            </div>

            <!-- Copy Recording Generator -->
            <div class="col-12 col-lg-6">
              <JobCard
                  :job-type="JobType.COPY_RECORDING_GENERATOR"
                  title="Download Recording Generator"
                  description="Creates a new recording from the repository simply by merging last configured number of recordings and placing them to Recording section."
                  icon="bi-clock-history"
                  icon-color="text-blue"
                  icon-bg="bg-blue-soft"
                  :coming-soon="true"
                  :disabled="true"
                  :badges="[
                  { text: 'Coming Soon', color: 'bg-warning text-dark', condition: true }
                ]"
                  @create-job="handleCreateJob"
              />
            </div>

            <!-- Periodic Recording Generator -->
            <div class="col-12 col-lg-6">
              <JobCard
                  :job-type="JobType.PERIODIC_RECORDING_GENERATOR"
                  title="Periodic Recording Generator"
                  description="Creates a new recording from the repository based on a specified periods (e.g. every 15min). Generated recording will be available in a Recordings section. Period can include several source files based on their modification date (it always waits for the latest file that crosses the expected end-time to finished and written to disk)."
                  icon="bi-arrow-repeat"
                  icon-color="text-blue"
                  icon-bg="bg-blue-soft"
                  :coming-soon="true"
                  :disabled="true"
                  :badges="[
                  { text: 'Coming Soon', color: 'bg-warning text-dark', condition: true }
                ]"
                  @create-job="handleCreateJob"
              />
            </div>

            <!-- JFR Compression -->
            <div class="col-12 col-lg-6">
              <JobCard
                  :job-type="JobType.REPOSITORY_JFR_COMPRESSION"
                  title="JFR Compression"
                  description="Compresses finished JFR recording files using LZ4 compression to save storage space. Processes active and latest finished sessions automatically."
                  icon="bi-file-zip"
                  icon-color="text-orange"
                  icon-bg="bg-orange-soft"
                  :disabled="jfrCompressionJobExists"
                  :badges="[
                  { text: 'Job already exists', color: 'bg-success', condition: jfrCompressionJobExists }
                ]"
              />
            </div>

            <!-- Recording Storage Synchronizer -->
            <div class="col-12 col-lg-6">
              <JobCard
                  :job-type="JobType.PROJECT_RECORDING_STORAGE_SYNCHRONIZER"
                  title="Recording Storage Synchronizer"
                  description="Synchronizes recording storage with the database by removing orphaned recordings that no longer exist in the database."
                  icon="bi-arrow-repeat"
                  icon-color="text-purple"
                  icon-bg="bg-purple-soft"
                  :disabled="recordingStorageSynchronizerJobExists"
                  :badges="[
                  { text: 'Job already exists', color: 'bg-success', condition: recordingStorageSynchronizerJobExists }
                ]"
              />
            </div>
          </div>
    </div>

    <!-- Active Jobs Card -->
    <div class="col-12">
      <div class="main-card">
        <div class="main-card-header">
          <i class="bi bi-clock-history main-card-header-icon"></i>
          <h5 class="main-card-header-title">Active Jobs</h5>
        </div>
        <div class="main-card-content p-0">
          <!-- Loading state -->
          <LoadingState v-if="isLoading" message="Loading scheduler information..." />

          <!-- Jobs table -->
          <JobsTable
            v-else-if="activeJobs.length > 0"
            :jobs="activeJobs"
            :get-job-display-info="getJobDisplayInfo"
            @toggle-enabled="toggleJobEnabled"
            @delete="deleteActiveTask"
          />

          <!-- Empty state -->
          <EmptyState
            v-else
            icon="bi-calendar-x"
            title="No Active Jobs"
            description="There are no active scheduled jobs. Create a new job using the options above."
          />
        </div>
      </div>
    </div>
  </PageHeader>

  <!-- Modal Components -->
  <RepositorySessionCleanerModal
    :show="showRepositorySessionCleanerModal"
    :scheduler-service="schedulerService"
    @close="showRepositorySessionCleanerModal = false"
    @saved="handleModalSaved" />


  <PeriodicRecordingGeneratorModal
    :show="showPeriodicRecordingGeneratorModal"
    :scheduler-service="schedulerService"
    @close="showPeriodicRecordingGeneratorModal = false"
    @saved="handleModalSaved" />

  <CopyRecordingGeneratorModal
    :show="showCopyRecordingGeneratorModal"
    :scheduler-service="schedulerService"
    @close="showCopyRecordingGeneratorModal = false"
    @saved="handleModalSaved" />

  <!-- Bootstrap toast container will be added by the ToastService -->
  <div class="toast-container position-fixed top-0 end-0 p-3">
    <!-- Toast notifications will be dynamically inserted here -->
  </div>
</template>

<style scoped>
/* Table styling */
.table th {
  font-weight: 500;
  font-size: 0.875rem;
  color: #495057;
}

.table td {
  vertical-align: middle;
}

/* Badge styling */
.badge {
  font-weight: 500;
  padding: 0.4em 0.65em;
}
</style>
