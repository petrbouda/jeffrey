<script setup lang="ts">
import {nextTick, onMounted, onUnmounted, ref, watch} from 'vue';
import {useRoute} from 'vue-router'
import ProjectRepositoryClient from "@/services/project/ProjectRepositoryClient.ts";
import Utils from "@/services/Utils";
import ProjectSchedulerClient from "@/services/project/ProjectSchedulerClient.ts";
import ProjectSettingsClient from "@/services/project/ProjectSettingsClient.ts";
import JobInfo from "@/services/model/JobInfo.ts";
import SettingsResponse from "@/services/project/model/SettingsResponse.ts";
import * as bootstrap from 'bootstrap';
import ToastService from "@/services/ToastService";
import MessageBus from "@/services/MessageBus";

const route = useRoute()
const currentProject = ref(null);
const currentRepository = ref<SettingsResponse | null>(null);

const projectId = route.params.projectId as string

const repositoryService = new ProjectRepositoryClient(projectId)
const schedulerService = new ProjectSchedulerClient(projectId)
const settingsService = new ProjectSettingsClient(projectId)

// Modal references
let cleanerModalInstance: bootstrap.Modal | null = null;
let generatorModalInstance: bootstrap.Modal | null = null;
let periodicGeneratorModalInstance: bootstrap.Modal | null = null;
let copyGeneratorModalInstance: bootstrap.Modal | null = null;

// Repository Cleaner modal data
const showCleanerModal = ref(false);
const dialogCleanerDuration = ref(1);
const dialogCleanerTimeUnit = ref(['Minutes', 'Hours', 'Days'])
const dialogCleanerSelectedTimeUnit = ref('Days')
const dialogCleanerMessages = ref([])

// Interval Recording Generator modal data
const showGeneratorModal = ref(false);
const dialogGeneratorFrom = ref('00:00');
const dialogGeneratorFilePattern = ref('intervals/recording-%t.jfr');
const dialogGeneratorTo = ref('00:00');
const dialogGeneratorAt = ref('00:00');
const dialogGeneratorAtEnabled = ref(false);
const dialogGeneratorMessages = ref([])

// Periodic Recording Generator modal data
const showPeriodicGeneratorModal = ref(false);
const dialogPeriodicGeneratorFilePattern = ref('periodic-15min/recording-%t.jfr');
const dialogPeriodicGeneratorPeriodPresets = ref(['1 min', '5 min', '15 min', '1 hour', 'Custom']);
const dialogPeriodicGeneratorSelectedPeriod = ref('15 min');
const dialogPeriodicGeneratorCustomPeriod = ref(15);
const dialogPeriodicGeneratorCustomTimeUnit = ref('Minutes');
const dialogPeriodicGeneratorTimeUnits = ref(['Minutes', 'Hours']);
const dialogPeriodicGeneratorMaxRecordings = ref(10); // Default to 10 recordings
const dialogPeriodicGeneratorMessages = ref([])

// Copy Recording Generator modal data
const showCopyGeneratorModal = ref(false);
const dialogCopyGeneratorFilePattern = ref('copied');
const dialogCopyGeneratorMaxRecordings = ref(10); // Default to 10 recordings
const dialogCopyGeneratorRemoveCopiedFiles = ref(true); // Default to true
const dialogCopyGeneratorMessages = ref([])

const activeJobs = ref<JobInfo[]>([])
const cleanerJobAlreadyExists = ref(false)

const isLoading = ref(false);

onMounted(async () => {
  isLoading.value = true;

  try {
    // Load the current linked repository to figure out,
    // whether it's allowed to create jobs based on active repository
    await repositoryService.get()
      .then((data) => {
        currentRepository.value = data;
      })
      .catch((error) => {
        if (error.response?.status === 404) {
          currentRepository.value = null;
        } else {
          console.error(error);
        }
      });

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

  // Initialize Bootstrap modals after the DOM is ready
  nextTick(() => {
    const cleanerModalEl = document.getElementById('cleanerModal');
    if (cleanerModalEl) {
      cleanerModalEl.addEventListener('hidden.bs.modal', () => {
        showCleanerModal.value = false;
      });

      const closeButton = cleanerModalEl.querySelector('.btn-close');
      if (closeButton) {
        closeButton.addEventListener('click', closeCleanerModal);
      }
    }

    const generatorModalEl = document.getElementById('generatorModal');
    if (generatorModalEl) {
      generatorModalEl.addEventListener('hidden.bs.modal', () => {
        showGeneratorModal.value = false;
      });

      const closeButton = generatorModalEl.querySelector('.btn-close');
      if (closeButton) {
        closeButton.addEventListener('click', closeGeneratorModal);
      }
    }

    const periodicGeneratorModalEl = document.getElementById('periodicGeneratorModal');
    if (periodicGeneratorModalEl) {
      periodicGeneratorModalEl.addEventListener('hidden.bs.modal', () => {
        showPeriodicGeneratorModal.value = false;
      });

      const closeButton = periodicGeneratorModalEl.querySelector('.btn-close');
      if (closeButton) {
        closeButton.addEventListener('click', closePeriodicGeneratorModal);
      }
    }

    const copyGeneratorModalEl = document.getElementById('copyGeneratorModal');
    if (copyGeneratorModalEl) {
      copyGeneratorModalEl.addEventListener('hidden.bs.modal', () => {
        showCopyGeneratorModal.value = false;
      });

      const closeButton = copyGeneratorModalEl.querySelector('.btn-close');
      if (closeButton) {
        closeButton.addEventListener('click', closeCopyGeneratorModal);
      }
    }
  });
});

// Watch for changes to period settings to update file pattern
watch(dialogPeriodicGeneratorSelectedPeriod, () => {
  updateFilePattern();
});

watch(dialogPeriodicGeneratorCustomPeriod, () => {
  if (dialogPeriodicGeneratorSelectedPeriod.value === 'Custom') {
    updateFilePattern();
  }
});

watch(dialogPeriodicGeneratorCustomTimeUnit, () => {
  if (dialogPeriodicGeneratorSelectedPeriod.value === 'Custom') {
    updateFilePattern();
  }
});

// Watch for changes to modal visibility flags to control modal visibility
watch(showCleanerModal, (isVisible) => {
  if (isVisible) {
    if (!cleanerModalInstance) {
      const modalEl = document.getElementById('cleanerModal');
      if (modalEl) {
        cleanerModalInstance = new bootstrap.Modal(modalEl);
      }
    }

    if (cleanerModalInstance) {
      cleanerModalInstance.show();
    }
  } else {
    if (cleanerModalInstance) {
      cleanerModalInstance.hide();
    }
  }
});

watch(showGeneratorModal, (isVisible) => {
  if (isVisible) {
    if (!generatorModalInstance) {
      const modalEl = document.getElementById('generatorModal');
      if (modalEl) {
        generatorModalInstance = new bootstrap.Modal(modalEl);
      }
    }

    if (generatorModalInstance) {
      generatorModalInstance.show();
    }
  } else {
    if (generatorModalInstance) {
      generatorModalInstance.hide();
    }
  }
});

watch(showPeriodicGeneratorModal, (isVisible) => {
  if (isVisible) {
    if (!periodicGeneratorModalInstance) {
      const modalEl = document.getElementById('periodicGeneratorModal');
      if (modalEl) {
        periodicGeneratorModalInstance = new bootstrap.Modal(modalEl);
      }
    }

    if (periodicGeneratorModalInstance) {
      periodicGeneratorModalInstance.show();
    }
  } else {
    if (periodicGeneratorModalInstance) {
      periodicGeneratorModalInstance.hide();
    }
  }
});

watch(showCopyGeneratorModal, (isVisible) => {
  if (isVisible) {
    if (!copyGeneratorModalInstance) {
      const modalEl = document.getElementById('copyGeneratorModal');
      if (modalEl) {
        copyGeneratorModalInstance = new bootstrap.Modal(modalEl);
      }
    }

    if (copyGeneratorModalInstance) {
      copyGeneratorModalInstance.show();
    }
  } else {
    if (copyGeneratorModalInstance) {
      copyGeneratorModalInstance.hide();
    }
  }
});

// Functions to close the modals
const closeCleanerModal = () => {
  if (cleanerModalInstance) {
    cleanerModalInstance.hide();
  }
  showCleanerModal.value = false;
  resetCleanerForm();
}

const closeGeneratorModal = () => {
  if (generatorModalInstance) {
    generatorModalInstance.hide();
  }
  showGeneratorModal.value = false;
  resetGeneratorForm();
}

const closePeriodicGeneratorModal = () => {
  if (periodicGeneratorModalInstance) {
    periodicGeneratorModalInstance.hide();
  }
  showPeriodicGeneratorModal.value = false;
  resetPeriodicGeneratorForm();
}

const closeCopyGeneratorModal = () => {
  if (copyGeneratorModalInstance) {
    copyGeneratorModalInstance.hide();
  }
  showCopyGeneratorModal.value = false;
  resetCopyGeneratorForm();
}

// Clean up event listeners and modal instances when component is unmounted
onUnmounted(() => {
  if (cleanerModalInstance) {
    cleanerModalInstance.dispose();
    cleanerModalInstance = null;
  }

  if (generatorModalInstance) {
    generatorModalInstance.dispose();
    generatorModalInstance = null;
  }

  if (periodicGeneratorModalInstance) {
    periodicGeneratorModalInstance.dispose();
    periodicGeneratorModalInstance = null;
  }

  if (copyGeneratorModalInstance) {
    copyGeneratorModalInstance.dispose();
    copyGeneratorModalInstance = null;
  }

  // Remove global event listeners
  document.removeEventListener('hidden.bs.modal', () => {});
});

function alreadyContainsRepositoryCleanerJob(jobs) {
  // Reset the flag first
  cleanerJobAlreadyExists.value = false;

  // Check if any job is a repository cleaner
  for (let job of jobs) {
    if (job.jobType === 'REPOSITORY_CLEANER') {
      cleanerJobAlreadyExists.value = true;
      break;
    }
  }
}

async function updateJobList() {
  try {
    const data = await schedulerService.all();
    alreadyContainsRepositoryCleanerJob(data);
    activeJobs.value = data;

    // Emit job count change event for the sidebar to update
    MessageBus.emit(MessageBus.JOBS_COUNT_CHANGED, data.length);

    return data;
  } catch (error) {
    console.error('Failed to load active jobs:', error);
    return [];
  }
}

// Function to reset the cleaner form to default values
function resetCleanerForm() {
  dialogCleanerDuration.value = 1;
  dialogCleanerSelectedTimeUnit.value = 'Days';
  dialogCleanerMessages.value = [];
}

async function saveCleanerJob() {
  if (!Utils.isPositiveNumber(dialogCleanerDuration.value)) {
    dialogCleanerMessages.value = [{severity: 'error', content: '`Max Age` is not a positive number'}];
    return;
  }
  dialogCleanerMessages.value = [];

  const params = {
    duration: dialogCleanerDuration.value,
    timeUnit: dialogCleanerSelectedTimeUnit.value
  };

  try {
    await schedulerService.create('REPOSITORY_CLEANER', params);
    await updateJobList();
    ToastService.success('Repository Cleaner Job', 'Cleaner Job has been created');

    // Reset form to default values
    resetCleanerForm();

    closeCleanerModal();
  } catch (error) {
    console.error('Failed to create cleaner job:', error);
    dialogCleanerMessages.value = [{
      severity: 'error', 
      content: error.response?.data || 'Failed to create job. Please try again.'
    }];
  }
}

// Function to add one minute to a time string "HH:MM"
function addOneMinuteToTime(timeStr) {
  const [hours, minutes] = timeStr.split(':').map(Number);

  // Add one minute
  let newMinutes = minutes + 1;
  let newHours = hours;

  // Handle minute overflow
  if (newMinutes >= 60) {
    newMinutes = 0;
    newHours += 1;
  }

  // Handle hour overflow (24-hour format)
  if (newHours >= 24) {
    newHours = 0;
  }

  // Format back to "HH:MM"
  return `${newHours.toString().padStart(2, '0')}:${newMinutes.toString().padStart(2, '0')}`;
}

async function saveGeneratorJob() {
  if (Utils.isBlank(dialogGeneratorTo.value)
      || Utils.isBlank(dialogGeneratorFrom.value)
      || Utils.isBlank(dialogGeneratorFilePattern.value)
      || (dialogGeneratorAtEnabled.value && Utils.isBlank(dialogGeneratorAt.value))) {
    dialogGeneratorMessages.value = [{severity: 'error', content: 'All enabled fields are required'}];
    return;
  }
  dialogGeneratorMessages.value = [];

  const params = {
    from: getTime(dialogGeneratorFrom.value),
    to: getTime(dialogGeneratorTo.value),
    filePattern: dialogGeneratorFilePattern.value,
  };

  // If Generate At is enabled, use the selected time
  // Otherwise, set it to Time Range "to" + 1 minute
  if (dialogGeneratorAtEnabled.value) {
    params.at = getTime(dialogGeneratorAt.value);
  } else {
    params.at = addOneMinuteToTime(getTime(dialogGeneratorTo.value));
  }

  try {
    await schedulerService.create('INTERVAL_RECORDING_GENERATOR', params);
    await updateJobList();
    ToastService.success('Interval Recording Generator Job', 'Generator Job has been created');

    // Reset form to default values
    resetGeneratorForm();

    closeGeneratorModal();
  } catch (error) {
    console.error('Failed to create generator job:', error);
    dialogGeneratorMessages.value = [{
      severity: 'error', 
      content: error.response?.data || 'Failed to create job. Please try again.'
    }];
  }
}

async function savePeriodicGeneratorJob() {
  if (Utils.isBlank(dialogPeriodicGeneratorFilePattern.value) || 
      (dialogPeriodicGeneratorSelectedPeriod.value === 'Custom' && !Utils.isPositiveNumber(dialogPeriodicGeneratorCustomPeriod.value))) {
    dialogPeriodicGeneratorMessages.value = [{severity: 'error', content: 'All fields are required and custom period must be a positive number'}];
    return;
  }
  dialogPeriodicGeneratorMessages.value = [];

  // Calculate period in minutes
  let periodInMinutes = 15; // Default to 15 minutes

  if (dialogPeriodicGeneratorSelectedPeriod.value === 'Custom') {
    // Convert custom period to minutes
    if (dialogPeriodicGeneratorCustomTimeUnit.value === 'Hours') {
      periodInMinutes = dialogPeriodicGeneratorCustomPeriod.value * 60;
    } else {
      periodInMinutes = dialogPeriodicGeneratorCustomPeriod.value;
    }
  } else {
    // Parse the preset period value
    const periodValue = dialogPeriodicGeneratorSelectedPeriod.value;
    if (periodValue === '1 min') {
      periodInMinutes = 1;
    } else if (periodValue === '5 min') {
      periodInMinutes = 5;
    } else if (periodValue === '15 min') {
      periodInMinutes = 15;
    } else if (periodValue === '1 hour') {
      periodInMinutes = 60;
    }
  }

  const params = {
    period: periodInMinutes,
    filePattern: dialogPeriodicGeneratorFilePattern.value,
    maxRecordings: dialogPeriodicGeneratorMaxRecordings.value
  };

  try {
    await schedulerService.create('PERIODIC_RECORDING_GENERATOR', params);
    await updateJobList();
    ToastService.success('Periodic Recording Generator Job', 'Generator Job has been created');

    // Reset form to default values
    resetPeriodicGeneratorForm();

    closePeriodicGeneratorModal();
  } catch (error) {
    console.error('Failed to create periodic generator job:', error);
    dialogPeriodicGeneratorMessages.value = [{
      severity: 'error', 
      content: error.response?.data || 'Failed to create job. Please try again.'
    }];
  }
}

async function saveCopyGeneratorJob() {
  if (Utils.isBlank(dialogCopyGeneratorFilePattern.value) || 
      !Utils.isPositiveNumber(dialogCopyGeneratorMaxRecordings.value)) {
    dialogCopyGeneratorMessages.value = [{severity: 'error', content: 'All fields are required and max recordings must be a non-negative number'}];
    return;
  }
  dialogCopyGeneratorMessages.value = [];

  const params = {
    targetFolder: dialogCopyGeneratorFilePattern.value,
    maxRecordings: dialogCopyGeneratorMaxRecordings.value,
    removeCopiedFiles: dialogCopyGeneratorRemoveCopiedFiles.value
  };

  try {
    await schedulerService.create('COPY_RECORDING_GENERATOR', params);
    await updateJobList();
    ToastService.success('Copy Recording Generator Job', 'Generator Job has been created');

    // Reset form to default values
    resetCopyGeneratorForm();

    closeCopyGeneratorModal();
  } catch (error) {
    console.error('Failed to create copy generator job:', error);
    dialogCopyGeneratorMessages.value = [{
      severity: 'error', 
      content: error.response?.data || 'Failed to create job. Please try again.'
    }];
  }
}

// Function to reset the generator form to default values
function resetGeneratorForm() {
  dialogGeneratorFrom.value = '00:00';
  dialogGeneratorTo.value = '00:00';
  dialogGeneratorAt.value = '00:00';
  dialogGeneratorAtEnabled.value = false;
  dialogGeneratorFilePattern.value = 'generated-intervals/recording-%t.jfr';
  dialogGeneratorMessages.value = [];
}

// Function to calculate the duration string based on period
function calculateDurationString() {
  let periodInMinutes = 15; // Default to 15 minutes

  if (dialogPeriodicGeneratorSelectedPeriod.value === 'Custom') {
    // Convert custom period to minutes
    if (dialogPeriodicGeneratorCustomTimeUnit.value === 'Hours') {
      periodInMinutes = dialogPeriodicGeneratorCustomPeriod.value * 60;
    } else {
      periodInMinutes = dialogPeriodicGeneratorCustomPeriod.value;
    }
  } else {
    // Parse the preset period value
    const periodValue = dialogPeriodicGeneratorSelectedPeriod.value;
    if (periodValue === '1 min') {
      periodInMinutes = 1;
    } else if (periodValue === '5 min') {
      periodInMinutes = 5;
    } else if (periodValue === '15 min') {
      periodInMinutes = 15;
    } else if (periodValue === '1 hour') {
      periodInMinutes = 60;
    }
  }

  // Format the duration string
  if (periodInMinutes >= 60 && periodInMinutes % 60 === 0) {
    return periodInMinutes / 60 + 'h';
  } else {
    return periodInMinutes + 'min';
  }
}

// Function to update the file pattern based on the current period
function updateFilePattern() {
  const durationString = calculateDurationString();
  dialogPeriodicGeneratorFilePattern.value = `periodic-${durationString}/recording-%t.jfr`;
}

// Function to reset the periodic generator form to default values
function resetPeriodicGeneratorForm() {
  dialogPeriodicGeneratorSelectedPeriod.value = '15 min';
  dialogPeriodicGeneratorCustomPeriod.value = 15;
  dialogPeriodicGeneratorCustomTimeUnit.value = 'Minutes';
  dialogPeriodicGeneratorMaxRecordings.value = 10; // Reset to default (10 recordings)
  dialogPeriodicGeneratorMessages.value = [];

  // Update the file pattern based on the reset period
  updateFilePattern();
}

// Function to reset the copy generator form to default values
function resetCopyGeneratorForm() {
  dialogCopyGeneratorFilePattern.value = 'copied';
  dialogCopyGeneratorMaxRecordings.value = 10; // Reset to default (10 recordings)
  dialogCopyGeneratorRemoveCopiedFiles.value = true; // Reset to default (true)
  dialogCopyGeneratorMessages.value = [];
}

async function toggleJobEnabled(job) {
  try {
    // Toggle the enabled state
    await schedulerService.updateEnabled(job.id, !job.enabled);

    // Refresh the job list to get updated state
    await updateJobList();

    ToastService.success('Job Updated', job.enabled ? 'Job has been disabled' : 'Job has been enabled');
  } catch (error) {
    console.error('Failed to update job state:', error);
    ToastService.error('Update Failed', error.response?.data || 'Failed to update job state. Please try again.');
  }
}

async function deleteActiveTask(id) {
  try {
    await schedulerService.delete(id);

    // Update the job list and refresh card states
    await updateJobList();

    ToastService.success('Job Deleted', 'The job has been removed');
  } catch (error) {
    console.error('Failed to delete job:', error);
    ToastService.error('Delete Failed', error.response?.data || 'Failed to delete job. Please try again.');
  }
}

function getTime(timeValue) {
  // If the value is already in HH:MM format, return it directly
  if (typeof timeValue === 'string' && timeValue.includes(':')) {
    return timeValue;
  }

  // For backward compatibility if a Date object is passed
  if (timeValue instanceof Date) {
    let hour = timeValue.getHours() < 10 ? '0' + timeValue.getHours() : timeValue.getHours();
    let minute = timeValue.getMinutes() < 10 ? '0' + timeValue.getMinutes() : timeValue.getMinutes();
    return hour + ":" + minute;
  }

  return "00:00"; // Default value for empty input
}
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
            <!-- Repository Cleaner -->
            <div class="col-12 col-lg-6">
              <div class="job-card h-100 p-4 shadow-sm d-flex flex-column border">
                <div class="d-flex align-items-center mb-3">
                  <div class="job-icon bg-teal-soft me-3 d-flex align-items-center justify-content-center">
                    <i class="bi bi-trash fs-4 text-teal"></i>
                  </div>
                  <div>
                    <h5 class="mb-0 fw-semibold">Repository Cleaner</h5>
                    <div class="d-flex mt-1">
                      <span class="badge rounded-pill bg-danger" v-if="!currentRepository">No repository linked</span>
                      <span class="badge rounded-pill bg-success" v-else-if="cleanerJobAlreadyExists">Job already exists</span>
                    </div>
                  </div>
                </div>
                <p class="text-muted mb-3">Task for removing old source files from the repository based on their age.</p>
                <div class="mt-auto d-flex justify-content-end">
                  <button 
                    class="btn btn-primary" 
                    @click="showCleanerModal = true" 
                    :disabled="!currentRepository || cleanerJobAlreadyExists">
                    <i class="bi bi-plus-lg me-1"></i>Create Job
                  </button>
                </div>
              </div>
            </div>

            <!-- Copy Recording Generator -->
            <div class="col-12 col-lg-6">
              <div class="job-card h-100 p-4 shadow-sm d-flex flex-column border">
                <div class="d-flex align-items-center mb-3">
                  <div class="job-icon bg-blue-soft me-3 d-flex align-items-center justify-content-center">
                    <i class="bi bi-clock-history fs-4 text-blue"></i>
                  </div>
                  <div>
                    <h5 class="mb-0 fw-semibold">Copy Recording Generator</h5>
                    <div class="d-flex mt-1">
                      <span class="badge rounded-pill bg-danger" v-if="!currentRepository">No repository linked</span>
                    </div>
                  </div>
                </div>
                <p class="text-muted mb-3">Creates a new recording from the repository simply by copying the source file into Recording section.
                  It's up to source file generation logic to decide the size and duration of recordings.</p>
                <div class="mt-auto d-flex justify-content-end">
                  <button 
                    class="btn btn-primary" 
                    @click="showCopyGeneratorModal = true" 
                    :disabled="!currentRepository">
                    <i class="bi bi-plus-lg me-1"></i>Create Job
                  </button>
                </div>
              </div>
            </div>

            <!-- Periodic Recording Generator -->
            <div class="col-12 col-lg-6">
              <div class="job-card h-100 p-4 shadow-sm d-flex flex-column border">
                <div class="d-flex align-items-center mb-3">
                  <div class="job-icon bg-blue-soft me-3 d-flex align-items-center justify-content-center">
                    <i class="bi bi-arrow-repeat fs-4 text-blue"></i>
                  </div>
                  <div>
                    <h5 class="mb-0 fw-semibold">Periodic Recording Generator</h5>
                    <div class="d-flex mt-1">
                      <span class="badge rounded-pill bg-danger" v-if="!currentRepository">No repository linked</span>
                    </div>
                  </div>
                </div>
                <p class="text-muted mb-3">Creates a new recording from the repository based on a specified periods (e.g. every 15min).
                  Generated recording will be available in a Recordings section. Period can include several source files
                  based on their modification date (it always waits for the latest file that crosses the expected end-time to finished and written to disk).</p>
                <div class="mt-auto d-flex justify-content-end">
                  <button 
                    class="btn btn-primary" 
                    @click="showPeriodicGeneratorModal = true" 
                    :disabled="!currentRepository">
                    <i class="bi bi-plus-lg me-1"></i>Create Job
                  </button>
                </div>
              </div>
            </div>

            <!-- Interval Recording Generator -->
            <div class="col-12 col-lg-6">
              <div class="job-card h-100 p-4 shadow-sm d-flex flex-column border">
                <div class="d-flex align-items-center mb-3">
                  <div class="job-icon bg-blue-soft me-3 d-flex align-items-center justify-content-center">
                    <i class="bi bi-clock-history fs-4 text-blue"></i>
                  </div>
                  <div>
                    <h5 class="mb-0 fw-semibold">Interval Recording Generator</h5>
                    <div class="d-flex mt-1">
                      <span class="badge rounded-pill bg-danger" v-if="!currentRepository">No repository linked</span>
                    </div>
                  </div>
                </div>
                <p class="text-muted mb-3">Creates a new recording from the repository based on a specified interval (e.g. 14:00-18:00).
                  Generated recording will be available in a Recordings section. Period can include several source files based on their
                  modification date (it always waits for the latest file that crosses the expected end-time to finished and written to disk).</p>
                <div class="mt-auto d-flex justify-content-end">
                  <button 
                    class="btn btn-primary" 
                    @click="showGeneratorModal = true" 
                    :disabled="!currentRepository">
                    <i class="bi bi-plus-lg me-1"></i>Create Job
                  </button>
                </div>
              </div>
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
                      <!-- Repository Cleaner -->
                      <template v-if="job.jobType === 'REPOSITORY_CLEANER'">
                        <div class="job-icon-sm bg-teal-soft me-2 d-flex align-items-center justify-content-center"
                             v-if="currentRepository">
                          <i class="bi bi-trash text-teal"></i>
                        </div>
                        <div class="job-icon-sm bg-danger-soft me-2 d-flex align-items-center justify-content-center"
                             v-else>
                          <i class="bi bi-x-lg text-danger"></i>
                        </div>
                        <div>
                          <div class="fw-medium">
                            Repository Cleaner
                            <span v-if="!job.enabled" class="badge bg-warning text-dark ms-2 small">Disabled</span>
                          </div>
                          <small class="text-danger" v-if="!currentRepository">disabled (no repository linked)</small>
                        </div>
                      </template>

                      <!-- Interval Recording Generator -->
                      <template v-else-if="job.jobType === 'INTERVAL_RECORDING_GENERATOR'">
                        <div class="job-icon-sm bg-blue-soft me-2 d-flex align-items-center justify-content-center"
                             v-if="currentRepository">
                          <i class="bi bi-clock-history text-blue"></i>
                        </div>
                        <div class="job-icon-sm bg-danger-soft me-2 d-flex align-items-center justify-content-center"
                             v-else>
                          <i class="bi bi-x-lg text-danger"></i>
                        </div>
                        <div>
                          <div class="fw-medium">
                            Interval Recording Generator
                            <span v-if="!job.enabled" class="badge bg-warning text-dark ms-2 small">Disabled</span>
                          </div>
                          <small class="text-danger" v-if="!currentRepository">disabled (no repository linked)</small>
                        </div>
                      </template>

                      <!-- Periodic Recording Generator -->
                      <template v-else-if="job.jobType === 'PERIODIC_RECORDING_GENERATOR'">
                        <div class="job-icon-sm bg-blue-soft me-2 d-flex align-items-center justify-content-center"
                             v-if="currentRepository">
                          <i class="bi bi-arrow-repeat text-blue"></i>
                        </div>
                        <div class="job-icon-sm bg-danger-soft me-2 d-flex align-items-center justify-content-center"
                             v-else>
                          <i class="bi bi-x-lg text-danger"></i>
                        </div>
                        <div>
                          <div class="fw-medium">
                            Periodic Recording Generator
                            <span v-if="!job.enabled" class="badge bg-warning text-dark ms-2 small">Disabled</span>
                          </div>
                          <small class="text-danger" v-if="!currentRepository">disabled (no repository linked)</small>
                        </div>
                      </template>

                      <!-- Copy Recording Generator -->
                      <template v-else-if="job.jobType === 'COPY_RECORDING_GENERATOR'">
                        <div class="job-icon-sm bg-blue-soft me-2 d-flex align-items-center justify-content-center"
                             v-if="currentRepository">
                          <i class="bi bi-clock-history text-blue"></i>
                        </div>
                        <div class="job-icon-sm bg-danger-soft me-2 d-flex align-items-center justify-content-center"
                             v-else>
                          <i class="bi bi-x-lg text-danger"></i>
                        </div>
                        <div>
                          <div class="fw-medium">
                            Copy Recording Generator
                            <span v-if="!job.enabled" class="badge bg-warning text-dark ms-2 small">Disabled</span>
                          </div>
                          <small class="text-danger" v-if="!currentRepository">disabled (no repository linked)</small>
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

    <!-- Loading Placeholder (similar to RepositoryView) -->
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

  <!-- ------------------------------------------ -->
  <!-- Bootstrap Modal for Repository Cleaner Job -->
  <!-- ------------------------------------------ -->
  <div class="modal fade" id="cleanerModal" tabindex="-1"
       aria-labelledby="cleanerModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
      <div class="modal-content rounded-2 shadow">
        <div class="modal-header bg-teal-soft border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-trash fs-4 me-2 text-teal"></i>
            <h5 class="modal-title mb-0 text-dark" id="cleanerModalLabel">Create a Repository Cleaner Job</h5>
          </div>
          <button type="button" class="btn-close" @click="closeCleanerModal" aria-label="Close"></button>
        </div>
        <div class="modal-body pt-4">
          <div class="info-panel mb-4">
            <div class="info-panel-icon">
              <i class="bi bi-info-circle-fill"></i>
            </div>
            <div class="info-panel-content">
              <h6 class="fw-bold mb-1">Repository Cleaner</h6>
              <p class="mb-0">
                Fill in a duration for how long to keep files in the repository.
                The files with the last modification date (on a filesystem)
                older than the given duration will be removed. Choose a reasonable
                time-length for the source files in the repository.
              </p>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="duration" class="col-sm-3 col-form-label fw-medium">Max Age</label>
            <div class="col-sm-9">
              <div class="input-group search-container">
                <span class="input-group-text"><i class="bi bi-hourglass-split"></i></span>
                <input type="number" id="duration" v-model="dialogCleanerDuration" class="form-control search-input" autocomplete="off" min="1"/>
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label class="col-sm-3 col-form-label fw-medium">Time Unit</label>
            <div class="col-sm-9">
              <div class="btn-group" role="group" aria-label="Time units">
                <button type="button" class="btn" 
                  v-for="unit in dialogCleanerTimeUnit" :key="unit"
                  :class="dialogCleanerSelectedTimeUnit === unit ? 'btn-primary' : 'btn-outline-primary'"
                  @click="dialogCleanerSelectedTimeUnit = unit">
                  {{ unit }}
                </button>
              </div>
            </div>
          </div>

          <div v-if="dialogCleanerMessages.length > 0" class="alert alert-danger mt-3">
            <div v-for="(msg, idx) in dialogCleanerMessages" :key="idx">
              <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ msg.content }}
            </div>
          </div>
        </div>
        <div class="modal-footer border-top-0">
          <button type="button" class="btn btn-light" @click="closeCleanerModal">Cancel</button>
          <button type="button" class="btn btn-primary" @click="saveCleanerJob">
            <i class="bi bi-save me-1"></i> Save Job
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- -------------------------------------------- -->
  <!-- Bootstrap Modal for Repository Generator Job -->
  <!-- -------------------------------------------- -->
  <div class="modal fade" id="generatorModal" tabindex="-1"
       aria-labelledby="generatorModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
      <div class="modal-content rounded-1 shadow">
        <div class="modal-header bg-blue-soft border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-clock-history fs-4 me-2 text-blue"></i>
            <h5 class="modal-title mb-0 text-dark" id="generatorModalLabel">Create an Interval Recording Generator Job</h5>
          </div>
          <button type="button" class="btn-close" @click="closeGeneratorModal" aria-label="Close"></button>
        </div>
        <div class="modal-body pt-4">
          <p class="text-muted mb-3">
            File-Pattern can contain a prefix with a slash indicating a "folder" in the
            Recordings section and <span class="fw-bold">%t</span> for replacing timestamps.
          </p>

          <div class="mb-4 row">
            <label for="filepattern" class="col-sm-3 col-form-label fw-medium">File Pattern</label>
            <div class="col-sm-9">
              <div class="input-group search-container">
                <span class="input-group-text"><i class="bi bi-file-earmark-text"></i></span>
                <input type="text" id="filepattern" v-model="dialogGeneratorFilePattern" class="form-control search-input" autocomplete="off"/>
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="from" class="col-sm-3 col-form-label fw-medium">Time Range</label>
            <div class="col-sm-9">
              <div class="d-flex gap-3 align-items-center">
                <div class="input-group search-container flex-grow-1">
                  <span class="input-group-text"><i class="bi bi-hourglass-top"></i></span>
                  <div class="time-label">From</div>
                  <input type="time" id="from" v-model="dialogGeneratorFrom" class="form-control search-input" autocomplete="off"/>
                </div>
                <div class="input-group search-container flex-grow-1">
                  <span class="input-group-text"><i class="bi bi-hourglass-bottom"></i></span>
                  <div class="time-label">To</div>
                  <input type="time" id="to" v-model="dialogGeneratorTo" class="form-control search-input" autocomplete="off"/>
                </div>
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="generateAt" class="col-sm-3 col-form-label fw-medium">Generate At</label>
            <div class="col-sm-9">
              <div class="input-group search-container" :class="{'disabled-input': !dialogGeneratorAtEnabled}">
                <span class="input-group-text"><i class="bi bi-clock"></i></span>
                <input type="time" id="generateAt" v-model="dialogGeneratorAt" class="form-control search-input" 
                       autocomplete="off" :disabled="!dialogGeneratorAtEnabled"/>
                <span class="input-group-text toggle-switch">
                  <div class="form-check form-switch mb-0">
                    <input class="form-check-input" type="checkbox" id="enableGenerateAt" v-model="dialogGeneratorAtEnabled">
                    <label class="form-check-label visually-hidden" for="enableGenerateAt">Enable</label>
                  </div>
                </span>
              </div>
              <div class="text-muted small mt-1" v-if="!dialogGeneratorAtEnabled">
                <i class="bi bi-info-circle me-1"></i>Generate automatically 1 minute after the "To" time
              </div>
            </div>
          </div>

          <div v-if="dialogGeneratorMessages.length > 0" class="alert alert-danger mt-3">
            <div v-for="(msg, idx) in dialogGeneratorMessages" :key="idx">
              <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ msg.content }}
            </div>
          </div>
        </div>
        <div class="modal-footer border-top-0">
          <button type="button" class="btn btn-light" @click="closeGeneratorModal">Cancel</button>
          <button type="button" class="btn btn-primary" @click="saveGeneratorJob">
            <i class="bi bi-save me-1"></i> Save Job
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- -------------------------------------------- -->
  <!-- Bootstrap Modal for Periodic Recording Generator Job -->
  <!-- -------------------------------------------- -->
  <div class="modal fade" id="periodicGeneratorModal" tabindex="-1"
       aria-labelledby="periodicGeneratorModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
      <div class="modal-content rounded-1 shadow">
        <div class="modal-header bg-blue-soft border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-arrow-repeat fs-4 me-2 text-blue"></i>
            <h5 class="modal-title mb-0 text-dark" id="periodicGeneratorModalLabel">Create a Periodic Recording Generator Job</h5>
          </div>
          <button type="button" class="btn-close" @click="closePeriodicGeneratorModal" aria-label="Close"></button>
        </div>
        <div class="modal-body pt-4">
          <p class="text-muted mb-3">
            File-Pattern can contain a prefix with a slash indicating a "folder" in the
            Recordings section and <span class="fw-bold">%t</span> for replacing timestamps,
            e.g. <code>periodic-15m/recording-%t.jfr</code>
          </p>

          <div class="mb-4 row">
            <label for="periodicFilepattern" class="col-sm-3 col-form-label fw-medium">File Pattern</label>
            <div class="col-sm-9">
              <div class="input-group search-container">
                <span class="input-group-text"><i class="bi bi-file-earmark-text"></i></span>
                <input type="text" id="periodicFilepattern" v-model="dialogPeriodicGeneratorFilePattern" class="form-control search-input" autocomplete="off"/>
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="periodicPeriod" class="col-sm-3 col-form-label fw-medium">Period</label>
            <div class="col-sm-9">
              <div>
                <div class="btn-group" role="group" aria-label="Period presets">
                  <button type="button" class="btn" 
                    v-for="preset in dialogPeriodicGeneratorPeriodPresets" :key="preset"
                    :class="dialogPeriodicGeneratorSelectedPeriod === preset ? 'btn-primary' : 'btn-outline-primary'"
                    @click="dialogPeriodicGeneratorSelectedPeriod = preset">
                    {{ preset }}
                  </button>
                </div>
              </div>

              <div v-if="dialogPeriodicGeneratorSelectedPeriod === 'Custom'" class="d-flex gap-3 align-items-center mt-4">
                <div class="input-group search-container flex-grow-1">
                  <span class="input-group-text"><i class="bi bi-clock-history"></i></span>
                  <input type="number" id="customPeriod" v-model="dialogPeriodicGeneratorCustomPeriod" 
                         class="form-control search-input" autocomplete="off" min="1"/>
                </div>
                <div class="btn-group" role="group" aria-label="Time units">
                  <button type="button" class="btn" 
                    v-for="unit in dialogPeriodicGeneratorTimeUnits" :key="unit"
                    :class="dialogPeriodicGeneratorCustomTimeUnit === unit ? 'btn-primary' : 'btn-outline-primary'"
                    @click="dialogPeriodicGeneratorCustomTimeUnit = unit">
                    {{ unit }}
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="maxRecordings" class="col-sm-3 col-form-label fw-medium">Max # of Recordings</label>
            <div class="col-sm-9">
              <div class="input-group search-container">
                <span class="input-group-text"><i class="bi bi-list-ol"></i></span>
                <input type="number" id="maxRecordings" v-model="dialogPeriodicGeneratorMaxRecordings" 
                       class="form-control search-input" autocomplete="off" min="0"/>
              </div>
              <div class="text-muted small mt-1">
                <i class="bi bi-info-circle me-1"></i>Set to 0 for unlimited recordings
              </div>
            </div>
          </div>

          <div v-if="dialogPeriodicGeneratorMessages.length > 0" class="alert alert-danger mt-3">
            <div v-for="(msg, idx) in dialogPeriodicGeneratorMessages" :key="idx">
              <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ msg.content }}
            </div>
          </div>
        </div>
        <div class="modal-footer border-top-0">
          <button type="button" class="btn btn-light" @click="closePeriodicGeneratorModal">Cancel</button>
          <button type="button" class="btn btn-primary" @click="savePeriodicGeneratorJob">
            <i class="bi bi-save me-1"></i> Save Job
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- -------------------------------------------- -->
  <!-- Bootstrap Modal for Copy Recording Generator Job -->
  <!-- -------------------------------------------- -->
  <div class="modal fade" id="copyGeneratorModal" tabindex="-1"
       aria-labelledby="copyGeneratorModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
      <div class="modal-content rounded-1 shadow">
        <div class="modal-header bg-blue-soft border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-clock-history fs-4 me-2 text-blue"></i>
            <h5 class="modal-title mb-0 text-dark" id="copyGeneratorModalLabel">Create a Copy Recording Generator Job</h5>
          </div>
          <button type="button" class="btn-close" @click="closeCopyGeneratorModal" aria-label="Close"></button>
        </div>
        <div class="modal-body pt-4">
          <p class="text-muted mb-3">
            Target Folder specifies where the copied recordings will be stored in the
            Recordings section.
          </p>

          <div class="mb-4 row">
            <label for="copyFilepattern" class="col-sm-3 col-form-label fw-medium">Target Folder</label>
            <div class="col-sm-9">
              <div class="input-group search-container">
                <span class="input-group-text"><i class="bi bi-file-earmark-text"></i></span>
                <input type="text" id="copyFilepattern" v-model="dialogCopyGeneratorFilePattern" class="form-control search-input" autocomplete="off"/>
              </div>
            </div>
          </div>

          <div class="mb-2 row">
            <label for="copyMaxRecordings" class="col-sm-3 col-form-label fw-medium">Max # of Recordings</label>
            <div class="col-sm-9">
              <div class="input-group search-container">
                <span class="input-group-text"><i class="bi bi-list-ol"></i></span>
                <input type="number" id="copyMaxRecordings" v-model="dialogCopyGeneratorMaxRecordings" 
                       class="form-control search-input" autocomplete="off" min="0"/>
              </div>
              <div class="text-muted small mt-1">
                <i class="bi bi-info-circle me-1"></i>Set to 0 for unlimited recordings
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="removeCopiedFiles" class="col-sm-3 col-form-label fw-medium">Remove copied files</label>
            <div class="col-sm-9">
              <div class="form-check form-switch mt-2">
                <input class="form-check-input" type="checkbox" id="removeCopiedFiles" v-model="dialogCopyGeneratorRemoveCopiedFiles">
                <label class="form-check-label" for="removeCopiedFiles"></label>
              </div>
            </div>
          </div>

          <div v-if="dialogCopyGeneratorMessages.length > 0" class="alert alert-danger mt-3">
            <div v-for="(msg, idx) in dialogCopyGeneratorMessages" :key="idx">
              <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ msg.content }}
            </div>
          </div>
        </div>
        <div class="modal-footer border-top-0">
          <button type="button" class="btn btn-light" @click="closeCopyGeneratorModal">Cancel</button>
          <button type="button" class="btn btn-primary" @click="saveCopyGeneratorJob">
            <i class="bi bi-save me-1"></i> Save Job
          </button>
        </div>
      </div>
    </div>
  </div>

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

/* Job cards */
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

/* Job icons */
.job-icon {
  width: 56px;
  height: 56px;
  min-width: 56px;
  font-size: 1.5rem;
  border-radius: 0.45rem;
}

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

.bg-teal {
  background-color: #20C997;
}

.text-teal {
  color: #20C997;
}

.bg-blue-soft {
  background-color: rgba(13, 110, 253, 0.15);
}

.bg-blue {
  background-color: #0d6efd;
}

.text-blue {
  color: #0d6efd;
}

.bg-danger-soft {
  background-color: rgba(220, 53, 69, 0.15);
}

/* Form styling */
.form-check-input[id="removeCopiedFiles"] {
  width: 2.5em;
  height: 1.25em;
}

/* Info panel */
.info-panel {
  display: flex;
  background-color: #f8f9fa;
  border-radius: 6px;
  overflow: hidden;
  border-left: 4px solid #5e64ff;
}

.info-panel-icon {
  flex: 0 0 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  font-size: 1.1rem;
}

.info-panel-content {
  flex: 1;
  padding: 0.875rem 1rem;
}

.info-panel-content h6 {
  color: #343a40;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
}

/* Button styling */
.btn-primary {
  background-color: #5e64ff;
  border-color: #5e64ff;
  box-shadow: 0 0.125rem 0.25rem rgba(94, 100, 255, 0.15);
}

.btn-primary:hover, .btn-primary:active {
  background-color: #4a51eb !important;
  border-color: #4a51eb !important;
}

.btn-outline-primary {
  color: #5e64ff;
  border-color: #5e64ff;
}

.btn-outline-primary:hover {
  background-color: #5e64ff;
  border-color: #5e64ff;
  color: #fff;
}

.btn-outline-danger {
  color: #dc3545;
  border-color: #dc3545;
}

.btn-outline-danger:hover {
  background-color: #dc3545;
  border-color: #dc3545;
  color: #fff;
}

.btn-light {
  background-color: #f8f9fa;
  border-color: #f8f9fa;
}

.btn-light:hover {
  background-color: #e9ecef;
  border-color: #e9ecef;
}

/* Search input styles */
.search-container {
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
  border-radius: 0.25rem;
  overflow: hidden;
}

.search-container .input-group-text {
  background-color: #fff;
  border-right: none;
  padding: 0 0.75rem;
  display: flex;
  align-items: center;
  height: 38px;
  color: #5e64ff;
}

.search-input {
  border-left: none;
  font-size: 0.875rem;
  height: 38px;
  padding: 0.375rem 0.75rem;
  line-height: 1.5;
}

.search-input:focus {
  box-shadow: none;
  border-color: #ced4da;
}

.disabled-input {
  opacity: 0.65;
}

.toggle-switch {
  background-color: #f8f9fa;
  border-left: none;
  padding: 0 0.5rem;
}

.toggle-switch .form-check {
  min-height: auto;
  margin: 0;
}

.toggle-switch .form-check-input {
  cursor: pointer;
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

/* Code styling */
code {
  background-color: #f8f9fa;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.875rem;
  word-break: break-all;
  color: #212529;
}

/* Empty state styling */
.empty-state-icon {
  font-size: 3rem;
  color: #ced4da;
  margin-bottom: 1rem;
}

/* Time label */
.time-label {
  position: absolute;
  top: -18px;
  left: 38px;
  font-size: 0.75rem;
  color: #6c757d;
}

/* Modal styling */
.modal-content {
  border: none;
}

.modal-header {
  padding: 1.25rem 1.5rem;
}

.modal-footer {
  padding: 1rem 1.5rem;
}

.modal-body {
  padding: 0 1.5rem 1.5rem 1.5rem;
}

/* Badge styling */
.badge {
  font-weight: 500;
  padding: 0.4em 0.65em;
}

.badge.rounded-pill {
  font-size: 0.75rem;
}

/* Alert styling */
.alert {
  border: none;
  border-radius: 0.5rem;
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

/* Shadow and border utilities */
.shadow-sm {
  box-shadow: 0 0.125rem 0.375rem rgba(0, 0, 0, 0.05) !important;
}

.border {
  border-color: #e9ecef !important;
}

.rounded-3 {
  border-radius: 0.5rem !important;
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
.fw-semibold {
  font-weight: 600 !important;
}

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
