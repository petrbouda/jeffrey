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

  // Check for existing jobs by type
  for (let job of jobs) {
    if (job.jobType === JobType.REPOSITORY_SESSION_CLEANER) {
      cleanerJobAlreadyExists.value = true;
    } else if (job.jobType === JobType.COPY_RECORDING_GENERATOR) {
      copyGeneratorJobAlreadyExists.value = true;
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
</script>

<template>
  <div class="row g-4">
    <!-- Page Header -->
    <div class="col-12">
      <div class="d-flex align-items-center mb-3">
        <i class="bi bi-calendar-check fs-4 me-2 text-primary"></i>
        <h3 class="mb-0">Scheduler</h3>
      </div>
      <p class="text-muted mb-2">
        Creates periodical jobs to manage data belonging to the given project, such as
        <span class="fst-italic">removing unnecessary old files from the repository</span>.
      </p>
    </div>

    <!-- Job Types Card -->
    <div class="col-12">
      <div class="card shadow-sm border-0">
        <div class="card-body p-4">
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

          </div>
        </div>
      </div>
    </div>

    <!-- Active Jobs Card -->
    <div class="col-12">
      <div class="card shadow-sm border-0">
        <div class="card-header bg-soft-blue d-flex justify-content-between align-items-center text-white py-3">
          <div class="d-flex align-items-center">
            <i class="bi bi-clock-history fs-4 me-2"></i>
            <h5 class="card-title mb-0">Active Jobs</h5>
          </div>
        </div>
        <div class="card-body p-0">
          <div class="table-responsive">
            <table class="table table-hover mb-0" v-if="activeJobs.length > 0">
              <thead class="table-light">
              <tr>
                <th scope="col" style="width: 30%">Job Type</th>
                <th scope="col" style="width: 60%">Parameters</th>
                <th scope="col" style="width: 10%" class="text-end">Actions</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="job in activeJobs" :key="job.id" :class="{'disabled-job': !job.enabled}">
                <td>
                  <div class="d-flex align-items-center">
                    <!-- Repository Session Cleaner -->
                    <template v-if="job.jobType === JobType.REPOSITORY_SESSION_CLEANER">
                      <div class="job-icon-sm bg-teal-soft me-2 d-flex align-items-center justify-content-center">
                        <i class="bi bi-trash text-teal"></i>
                      </div>
                      <div>
                        <div class="fw-medium">
                          Repository Session Cleaner
                          <span v-if="!job.enabled" class="badge bg-warning text-dark ms-2 small">Disabled</span>
                        </div>
                      </div>
                    </template>

                    <template v-if="job.jobType === JobType.REPOSITORY_RECORDING_CLEANER">
                      <div class="job-icon-sm bg-teal-soft me-2 d-flex align-items-center justify-content-center">
                        <i class="bi bi-trash text-teal"></i>
                      </div>
                      <div>
                        <div class="fw-medium">
                          Repository Recording Cleaner
                          <span v-if="!job.enabled" class="badge bg-warning text-dark ms-2 small">Disabled</span>
                        </div>
                      </div>
                    </template>


                    <!-- Periodic Recording Generator -->
                    <template v-else-if="job.jobType === JobType.PERIODIC_RECORDING_GENERATOR">
                      <div class="job-icon-sm bg-blue-soft me-2 d-flex align-items-center justify-content-center">
                        <i class="bi bi-arrow-repeat text-blue"></i>
                      </div>
                      <div>
                        <div class="fw-medium">
                          Periodic Recording Generator
                          <span v-if="!job.enabled" class="badge bg-warning text-dark ms-2 small">Disabled</span>
                        </div>
                      </div>
                    </template>

                    <!-- Copy Recording Generator -->
                    <template v-else-if="job.jobType === JobType.COPY_RECORDING_GENERATOR">
                      <div class="job-icon-sm bg-blue-soft me-2 d-flex align-items-center justify-content-center">
                        <i class="bi bi-clock-history text-blue"></i>
                      </div>
                      <div>
                        <div class="fw-medium">
                          Download Recording Generator
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
                    <button class="btn btn-sm btn-outline-danger" @click="deleteActiveTask(job.id)" title="Delete job">
                      <i class="bi bi-trash"></i>
                    </button>
                  </div>
                </td>
              </tr>
              </tbody>
            </table>

            <!-- Empty state for active jobs -->
            <div class="text-center py-5" v-if="activeJobs.length === 0">
              <div class="empty-state-icon mb-3">
                <i class="bi bi-calendar-x fs-1 text-muted"></i>
              </div>
              <h6 class="fw-medium">No Active Jobs</h6>
              <p class="text-muted mb-0">
                There are no active scheduled jobs. Create a new job using the options above.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading Placeholder -->
    <div class="col-12" v-if="isLoading">
      <div class="card shadow-sm border-0">
        <div class="card-header bg-soft-blue d-flex justify-content-between align-items-center text-white py-3">
          <div class="d-flex align-items-center">
            <i class="bi bi-calendar-check fs-4 me-2"></i>
            <h5 class="card-title mb-0">Scheduler</h5>
          </div>
        </div>
        <div class="card-body p-5 text-center">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <p class="mt-3">Loading scheduler information...</p>
        </div>
      </div>
    </div>
  </div>

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
/* Card styling */
.card {
  border-radius: 0.25rem;
  overflow: hidden;
  transition: all 0.2s ease;
  border: none;
}

.card-header {
  border-bottom: none;
}

.bg-soft-blue {
  background-color: #5e64ff;
}

/* Job icons for table */
.job-icon-sm {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 0.25rem;
}

/* Colors */
.bg-teal-soft {
  background-color: rgba(32, 201, 151, 0.15);
}

.text-teal {
  color: #20C997;
}

.bg-blue-soft {
  background-color: rgba(13, 110, 253, 0.15);
}

.text-blue {
  color: #0d6efd;
}

/* Job Parameters styling */
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

/* Empty state styling */
.empty-state-icon {
  font-size: 3rem;
  color: #ced4da;
  margin-bottom: 1rem;
}

/* Badge styling */
.badge {
  font-weight: 500;
  padding: 0.4em 0.65em;
}

/* Table styling */
.table th {
  font-weight: 500;
  font-size: 0.875rem;
  color: #495057;
}

.table td {
  vertical-align: middle;
}

.table-light {
  background-color: #f8f9fa;
}

/* Shadow utilities */
.shadow-sm {
  box-shadow: 0 0.125rem 0.375rem rgba(0, 0, 0, 0.05) !important;
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

/* Typography utilities */
.text-muted {
  color: #6c757d !important;
}

/* Spinner styling */
.spinner-border {
  width: 1.5rem;
  height: 1.5rem;
  border-width: 0.15em;
}
</style>
