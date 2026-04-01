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
import ProjectSchedulerClient from '@/services/api/ProjectSchedulerClient.ts';
import ProjectSettingsClient from '@/services/api/ProjectSettingsClient.ts';
import JobInfo from '@/services/api/model/JobInfo.ts';
import { JobType } from '@/services/api/model/JobType.ts';
import SettingsResponse from '@/services/api/model/SettingsResponse.ts';
import ToastService from '@/services/ToastService';
import JobCard from '@/components/JobCard.vue';
import ProjectInstanceSessionCleanerModal from '@/components/scheduler/modal/ProjectInstanceSessionCleanerModal.vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import JobsTable, { type JobDisplayInfo } from '@/components/scheduler/JobsTable.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import '@/styles/shared-components.css';

const { workspaceId, projectId } = useNavigation();
const currentProject = ref<SettingsResponse | null>(null);

const schedulerService = new ProjectSchedulerClient(workspaceId.value!, projectId.value!);
const settingsService = new ProjectSettingsClient(workspaceId.value!, projectId.value!);

// Modal state
const showCleanerModal = ref(false);
const cleanerModalJobType = ref<string>(JobType.PROJECT_INSTANCE_SESSION_CLEANER);
const activeJobs = ref<JobInfo[]>([]);
const sessionCleanerJobAlreadyExists = ref(false);
const recordingCleanerJobAlreadyExists = ref(false);
const jfrCompressionJobExists = ref(false);
const sessionFinishedDetectorJobExists = ref(false);
const expiredInstanceCleanerJobExists = ref(false);

const isLoading = ref(false);

onMounted(async () => {
  isLoading.value = true;

  try {
    // Update job list
    await updateJobList();

    // Get project settings
    await settingsService
      .get()
      .then(data => {
        currentProject.value = data;
      })
      .catch(error => {
        console.error('Failed to load project settings:', error);
      });
  } finally {
    isLoading.value = false;
  }
});

function checkForExistingJobs(jobs: JobInfo[]) {
  // Reset the flags first
  sessionCleanerJobAlreadyExists.value = false;
  recordingCleanerJobAlreadyExists.value = false;
  jfrCompressionJobExists.value = false;
  sessionFinishedDetectorJobExists.value = false;
  expiredInstanceCleanerJobExists.value = false;

  // Check for existing jobs by type
  for (let job of jobs) {
    if (job.jobType === JobType.PROJECT_INSTANCE_SESSION_CLEANER) {
      sessionCleanerJobAlreadyExists.value = true;
    } else if (job.jobType === JobType.PROJECT_INSTANCE_RECORDING_CLEANER) {
      recordingCleanerJobAlreadyExists.value = true;
    } else if (job.jobType === JobType.REPOSITORY_JFR_COMPRESSION) {
      jfrCompressionJobExists.value = true;
    } else if (job.jobType === JobType.SESSION_FINISHED_DETECTOR) {
      sessionFinishedDetectorJobExists.value = true;
    } else if (job.jobType === JobType.EXPIRED_INSTANCE_CLEANER) {
      expiredInstanceCleanerJobExists.value = true;
    }
  }
}

async function updateJobList() {
  try {
    const data = await schedulerService.all();
    checkForExistingJobs(data);
    activeJobs.value = data;

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

    ToastService.success(
      'Job Updated',
      job.enabled ? 'Job has been disabled' : 'Job has been enabled'
    );
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
const handleCreateJob = async (jobType: string) => {
  switch (jobType) {
    case JobType.PROJECT_INSTANCE_SESSION_CLEANER:
    case JobType.PROJECT_INSTANCE_RECORDING_CLEANER:
    case JobType.EXPIRED_INSTANCE_CLEANER:
      cleanerModalJobType.value = jobType;
      showCleanerModal.value = true;
      break;
    case JobType.REPOSITORY_JFR_COMPRESSION:
    case JobType.SESSION_FINISHED_DETECTOR:
      // These jobs don't need configuration, create directly
      await createSimpleJob(jobType);
      break;
  }
};

// Get human-readable name for job type
const getJobTypeName = (jobType: string): string => {
  switch (jobType) {
    case JobType.REPOSITORY_JFR_COMPRESSION:
      return 'JFR Compression';
    case JobType.SESSION_FINISHED_DETECTOR:
      return 'Session Finished Detector';
    default:
      return jobType;
  }
};

// Create a job without configuration (no modal needed)
const createSimpleJob = async (jobType: string) => {
  const jobName = getJobTypeName(jobType);
  try {
    await schedulerService.create(jobType, new Map());
    await updateJobList();
    ToastService.success('Job Created', `${jobName} has been created successfully`);
  } catch (error: any) {
    console.error('Failed to create job:', error);
    ToastService.error('Creation Failed', error.response?.data || `Failed to create ${jobName}.`);
  }
};

// Get display info for jobs in the table
const getJobDisplayInfo = (job: JobInfo): JobDisplayInfo | null => {
  switch (job.jobType) {
    case JobType.PROJECT_INSTANCE_SESSION_CLEANER:
      return {
        title: 'Instance Session Cleaner',
        icon: 'bi-trash',
        iconColor: 'text-teal',
        iconBg: 'bg-teal-soft'
      };
    case JobType.PROJECT_INSTANCE_RECORDING_CLEANER:
      return {
        title: 'Instance Recording Cleaner',
        icon: 'bi-trash',
        iconColor: 'text-teal',
        iconBg: 'bg-teal-soft'
      };
    case JobType.REPOSITORY_JFR_COMPRESSION:
      return {
        title: 'JFR Compression',
        icon: 'bi-file-zip',
        iconColor: 'text-orange',
        iconBg: 'bg-orange-soft'
      };
    case JobType.SESSION_FINISHED_DETECTOR:
      return {
        title: 'Session Finished Detector',
        icon: 'bi-check-circle',
        iconColor: 'text-cyan',
        iconBg: 'bg-cyan-soft'
      };
    case JobType.EXPIRED_INSTANCE_CLEANER:
      return {
        title: 'Expired Instance Cleaner',
        icon: 'bi-trash',
        iconColor: 'text-teal',
        iconBg: 'bg-teal-soft'
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
        <!-- Instance Session Cleaner -->
        <div class="col-12 col-lg-6">
          <JobCard
            :job-type="JobType.PROJECT_INSTANCE_SESSION_CLEANER"
            title="Instance Session Cleaner"
            description="Task for removing Project Instance Session older than the configured duration. Once a session is removed, all associated Recordings and Additional Files (e.g. HeapDump, PerfCounters, ...) are being removed as well."
            icon="bi-trash"
            icon-color="text-teal"
            icon-bg="bg-teal-soft"
            :disabled="sessionCleanerJobAlreadyExists"
            :badges="[
              {
                text: 'Job already exists',
                color: 'bg-success',
                condition: sessionCleanerJobAlreadyExists
              }
            ]"
            @create-job="handleCreateJob"
          />
        </div>

        <!-- Instance Recording Cleaner -->
        <div class="col-12 col-lg-6">
          <JobCard
            :job-type="JobType.PROJECT_INSTANCE_RECORDING_CLEANER"
            title="Instance Recording Cleaner"
            description="Task for removing only Recording in the active (latest) Project Instance Session. It does not remove recordings in older sessions, it just ensures that the rolling recordings in the latest session are limited."
            icon="bi-trash"
            icon-color="text-teal"
            icon-bg="bg-teal-soft"
            :disabled="recordingCleanerJobAlreadyExists"
            :badges="[
              {
                text: 'Job already exists',
                color: 'bg-success',
                condition: recordingCleanerJobAlreadyExists
              }
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
              {
                text: 'Job already exists',
                color: 'bg-success',
                condition: jfrCompressionJobExists
              }
            ]"
            @create-job="handleCreateJob"
          />
        </div>

        <!-- Session Finished Detector -->
        <div class="col-12 col-lg-6">
          <JobCard
            :job-type="JobType.SESSION_FINISHED_DETECTOR"
            title="Session Finished Detector"
            description="Detects when repository sessions become finished and emits SESSION_FINISHED workspace events. Uses heartbeat-based detection strategy."
            icon="bi-check-circle"
            icon-color="text-cyan"
            icon-bg="bg-cyan-soft"
            :disabled="sessionFinishedDetectorJobExists"
            :badges="[
              {
                text: 'Job already exists',
                color: 'bg-success',
                condition: sessionFinishedDetectorJobExists
              }
            ]"
            @create-job="handleCreateJob"
          />
        </div>

        <!-- Expired Instance Cleaner -->
        <div class="col-12 col-lg-6">
          <JobCard
            :job-type="JobType.EXPIRED_INSTANCE_CLEANER"
            title="Expired Instance Cleaner"
            description="Removes expired instance metadata after the configured retention period. Instances transition to EXPIRED when all their sessions are cleaned up, and this job permanently deletes those rows."
            icon="bi-trash"
            icon-color="text-teal"
            icon-bg="bg-teal-soft"
            :disabled="expiredInstanceCleanerJobExists"
            :badges="[
              {
                text: 'Job already exists',
                color: 'bg-success',
                condition: expiredInstanceCleanerJobExists
              }
            ]"
            @create-job="handleCreateJob"
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
  <ProjectInstanceSessionCleanerModal
    v-model:show="showCleanerModal"
    :job-type="cleanerModalJobType"
    :scheduler-service="schedulerService"
    @saved="handleModalSaved"
  />

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
  color: var(--color-text);
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
