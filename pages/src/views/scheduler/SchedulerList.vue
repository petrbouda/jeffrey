<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import ProjectRepositoryService from "@/services/project/ProjectRepositoryService";
import Utils from "@/services/Utils";
import ProjectSchedulerService from "@/services/project/ProjectSchedulerService";
import ProjectSettingsService from "@/services/project/ProjectSettingsService";
import RepositoryInfo from "@/services/project/model/RepositoryInfo.ts";
import SettingsResponse from "@/services/project/model/SettingsResponse.ts";
import ToastService from "@/services/ToastService";
import JobInfo from "@/services/model/JobInfo.ts";

const route = useRoute();
const toast = new ToastService();

const currentProject = ref<SettingsResponse | null>();
const currentRepository = ref<RepositoryInfo | null>();

const projectId = route.params.projectId as string;

const repositoryService = new ProjectRepositoryService(projectId);
const schedulerService = new ProjectSchedulerService(projectId);
const settingsService = new ProjectSettingsService(projectId);

// Cleaner dialog state
const showCleanerDialog = ref(false);
const cleanerDuration = ref(1);
const cleanerTimeUnits = ['Minutes', 'Hours', 'Days'];
const cleanerSelectedTimeUnit = ref('Days');
const cleanerErrorMessage = ref('');

// Generator dialog state
const showGeneratorDialog = ref(false);
const generatorFrom = ref('');
const generatorFilePattern = ref('');
const generatorTo = ref('');
const generatorAt = ref('');
const generatorErrorMessage = ref('');

const activeJobs = ref<JobInfo[]>([]);
const cleanerJobAlreadyExists = ref(false);

// Watch for modal state changes and handle body class for scrolling
watch(showCleanerDialog, (newVal) => {
  if (newVal) {
    document.body.classList.add('modal-open');
  } else {
    document.body.classList.remove('modal-open');
  }
});

watch(showGeneratorDialog, (newVal) => {
  if (newVal) {
    document.body.classList.add('modal-open');
  } else {
    document.body.classList.remove('modal-open');
  }
});

onMounted(() => {
  // Load the current linked repository to figure out,
  // whether it's allowed to create jobs based on active repository
  repositoryService.get()
    .then((data) => {
      currentRepository.value = data;
    })
    .catch((error) => {
      if (error.response.status === 404) {
        currentRepository.value = null;
      } else {
        console.error(error);
      }
    });

  updateJobList();

  settingsService.get()
    .then((data) => {
      currentProject.value = data;
    });
});

function alreadyContainsRepositoryCleanerJob(jobs) {
  for (let job of jobs) {
    if (job.jobType === 'REPOSITORY_CLEANER') {
      cleanerJobAlreadyExists.value = true;
      break;
    }
  }
}

function updateJobList() {
  schedulerService.all()
    .then((data) => {
      alreadyContainsRepositoryCleanerJob(data);
      activeJobs.value = data;
    });
}

function saveCleanerJob() {
  if (!Utils.isPositiveNumber(cleanerDuration.value)) {
    cleanerErrorMessage.value = 'Older Than is not a positive number';
    return;
  }
  cleanerErrorMessage.value = '';

  const params = {
    duration: cleanerDuration.value,
    timeUnit: cleanerSelectedTimeUnit.value
  };

  schedulerService.create('REPOSITORY_CLEANER', params)
    .then(() => {
      updateJobList();
      toast.success('Repository Cleaner Job', 'Cleaner Job has been created');
      showCleanerDialog.value = false;
    });
}

function saveGeneratorJob() {
  if (generatorAt.value == null
    || generatorTo.value == null
    || generatorFrom.value == null
    || Utils.isBlank(generatorFilePattern.value)) {
    generatorErrorMessage.value = 'Any of the fields cannot be empty';
    return;
  }
  generatorErrorMessage.value = '';

  const params = {
    from: getTime(generatorFrom.value),
    to: getTime(generatorTo.value),
    at: getTime(generatorAt.value),
    filePattern: generatorFilePattern.value,
  };

  schedulerService.create('RECORDING_GENERATOR', params)
    .then(() => {
      updateJobList();
      toast.success('Repository Generator Job', 'Generator Job has been created');
      showGeneratorDialog.value = false;
    });
}

function deleteActiveTask(id) {
  schedulerService.delete(id)
    .then(() => {
      activeJobs.value = activeJobs.value.filter((task) => task.id !== id);
      toast.success('Job Deleted', 'The job has been removed');
    });
}

function getTime(date) {
  let hour = addLeadingZero(date.getHours());
  let minute = addLeadingZero(date.getMinutes());
  return hour + ":" + minute;
}

function addLeadingZero(value) {
  return value < 10 ? '0' + value : value;
}

// Bootstrap modal helpers
function openCleanerModal() {
  showCleanerDialog.value = true;
}

function closeCleanerModal() {
  showCleanerDialog.value = false;
}

function openGeneratorModal() {
  showGeneratorDialog.value = true;
}

function closeGeneratorModal() {
  showGeneratorDialog.value = false;
}
</script>

<template>
  <div class="card w-100">
    <div class="card-header bg-primary bg-opacity-10 text-primary">
      <div class="d-flex align-items-center">
        <i class="bi bi-clock-history fs-5 me-2"></i>
        <h5 class="card-title mb-0">Scheduler</h5>
      </div>
    </div>

    <div class="card-body p-4" v-if="currentProject">
      <h3>Scheduler</h3>
      <div class="text-secondary mb-5">Creates periodical jobs to manage data belonging to the given project. e.g.
        <span class="fst-italic">removing unnecessary old files from the repository</span>
      </div>

      <div class="row g-4">
        <!-- Repository Cleaner Card -->
        <div class="col-12 col-lg-6">
          <div class="card h-100 border-0 shadow-sm" 
               @mouseover="$event.currentTarget.classList.add('shadow')"
               @mouseout="$event.currentTarget.classList.remove('shadow')">
            <div class="card-body p-3 d-flex align-items-center">
              <div class="d-flex align-items-center justify-content-center me-3 rounded-3 bg-teal bg-opacity-10" 
                   style="width: 48px; height: 48px;">
                <i class="bi bi-trash text-teal fs-4"></i>
              </div>
              <div>
                <h5 class="card-title mb-0">Repository Cleaner</h5>
                <span class="text-danger" v-if="!currentRepository">(no repository linked)</span>
                <span class="text-success" v-else-if="cleanerJobAlreadyExists">(cleaner job already exists)</span>
                <p class="card-text text-secondary mt-2 mb-0 small">Task for removing old source files from the repository</p>
              </div>
              <div class="ms-auto">
                <button class="btn btn-sm btn-outline-secondary rounded-circle" 
                        @click="openCleanerModal()" 
                        :disabled="!currentRepository || cleanerJobAlreadyExists"
                        data-bs-toggle="modal" 
                        data-bs-target="#cleanerModal">
                  <i class="bi bi-plus fs-5"></i>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Recording Generator Card -->
        <div class="col-12 col-lg-6">
          <div class="card h-100 border-0 shadow-sm"
               @mouseover="$event.currentTarget.classList.add('shadow')"
               @mouseout="$event.currentTarget.classList.remove('shadow')">
            <div class="card-body p-3 d-flex align-items-center">
              <div class="d-flex align-items-center justify-content-center me-3 rounded-3 bg-primary bg-opacity-10" 
                   style="width: 48px; height: 48px;">
                <i class="bi bi-file-earmark-text text-primary fs-4"></i>
              </div>
              <div>
                <h5 class="card-title mb-0">Recording Generator</h5>
                <span class="text-danger" v-if="!currentRepository">(no repository linked)</span>
                <p class="card-text text-secondary mt-2 mb-0 small">Generates a new Recording from the repository data</p>
              </div>
              <div class="ms-auto">
                <button class="btn btn-sm btn-outline-secondary rounded-circle" 
                        @click="openGeneratorModal()" 
                        :disabled="!currentRepository"
                        data-bs-toggle="modal" 
                        data-bs-target="#generatorModal">
                  <i class="bi bi-plus fs-5"></i>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Active Jobs Table -->
      <h3 class="mt-5 mb-3">Active Jobs</h3>
      <div class="table-responsive">
        <table class="table table-hover">
          <thead>
            <tr>
              <th>Job</th>
              <th>Parameters</th>
              <th class="text-end">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="job in activeJobs" :key="job.id">
              <td>
                <!-- Cleaner Job -->
                <div v-if="job.jobType === 'REPOSITORY_CLEANER'" class="d-flex align-items-center">
                  <div class="d-flex align-items-center justify-content-center rounded-3 me-3 bg-teal bg-opacity-10" 
                       style="width: 48px; height: 48px;" 
                       v-if="currentRepository">
                    <i class="bi bi-trash text-teal fs-5"></i>
                  </div>
                  <div class="d-flex align-items-center justify-content-center rounded-3 me-3 bg-danger bg-opacity-10" 
                       style="width: 48px; height: 48px;"
                       v-else>
                    <i class="bi bi-x-lg text-danger fs-5"></i>
                  </div>
                  <div>
                    <span>Cleaner</span>
                    <span class="text-danger ms-2 small" v-if="!currentRepository">disabled (no repository linked)</span>
                  </div>
                </div>
                
                <!-- Generator Job -->
                <div v-else-if="job.jobType === 'RECORDING_GENERATOR'" class="d-flex align-items-center">
                  <div class="d-flex align-items-center justify-content-center rounded-3 me-3 bg-primary bg-opacity-10" 
                       style="width: 48px; height: 48px;"
                       v-if="currentRepository">
                    <i class="bi bi-file-earmark-text text-primary fs-5"></i>
                  </div>
                  <div class="d-flex align-items-center justify-content-center rounded-3 me-3 bg-danger bg-opacity-10" 
                       style="width: 48px; height: 48px;"
                       v-else>
                    <i class="bi bi-x-lg text-danger fs-5"></i>
                  </div>
                  <div>
                    <span>Generator</span>
                    <span class="text-danger ms-2 small" v-if="!currentRepository">disabled (no repository linked)</span>
                  </div>
                </div>
              </td>
              <td>{{ job.params }}</td>
              <td class="text-end">
                <button class="btn btn-sm btn-outline-danger rounded-circle"
                        @click="deleteActiveTask(job.id)">
                  <i class="bi bi-trash"></i>
                </button>
              </td>
            </tr>
            <tr v-if="activeJobs && activeJobs.length === 0">
              <td colspan="3" class="text-center text-secondary py-4">No active jobs found</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <!-- Repository Cleaner Modal -->
  <div class="modal" id="cleanerModal" tabindex="-1" :class="{ 'show': showCleanerDialog }" 
       style="display: none;" v-show="showCleanerDialog">
    <div class="modal-dialog modal-lg">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Create a Repository Cleaner Job</h5>
          <button type="button" class="btn-close" @click="closeCleanerModal()"></button>
        </div>
        <div class="modal-body">
          <p class="text-secondary mb-4">
            Fill in a duration for how long to keep files in the repository.
            The files with the last modification date (on a filesystem)
            older than the given duration will be removed. Choose a reasonable
            time-length for the source files in the repository.
          </p>
          
          <div class="alert alert-danger" v-if="cleanerErrorMessage">
            {{ cleanerErrorMessage }}
          </div>

          <div class="mb-3 row">
            <label for="duration" class="col-sm-3 col-form-label">Older than</label>
            <div class="col-sm-9">
              <input type="number" class="form-control" id="duration" v-model="cleanerDuration" autocomplete="off">
            </div>
          </div>
          
          <div class="mb-3 row">
            <label class="col-sm-3 col-form-label">Time Unit</label>
            <div class="col-sm-9">
              <div class="btn-group" role="group">
                <input type="radio" class="btn-check" name="timeUnit" id="timeUnit1" v-model="cleanerSelectedTimeUnit" value="Minutes" autocomplete="off">
                <label class="btn btn-outline-primary" for="timeUnit1">Minutes</label>
                
                <input type="radio" class="btn-check" name="timeUnit" id="timeUnit2" v-model="cleanerSelectedTimeUnit" value="Hours" autocomplete="off">
                <label class="btn btn-outline-primary" for="timeUnit2">Hours</label>
                
                <input type="radio" class="btn-check" name="timeUnit" id="timeUnit3" v-model="cleanerSelectedTimeUnit" value="Days" autocomplete="off">
                <label class="btn btn-outline-primary" for="timeUnit3">Days</label>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="closeCleanerModal()">Cancel</button>
          <button type="button" class="btn btn-primary" @click="saveCleanerJob()">Save a new Job</button>
        </div>
      </div>
    </div>
  </div>

  <!-- Repository Generator Modal -->
  <div class="modal" id="generatorModal" tabindex="-1" :class="{ 'show': showGeneratorDialog }" 
       style="display: none;" v-show="showGeneratorDialog">
    <div class="modal-dialog modal-lg">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Create a Repository Generator Job</h5>
          <button type="button" class="btn-close" @click="closeGeneratorModal()"></button>
        </div>
        <div class="modal-body">
          <p class="text-secondary mb-4">
            Creates a new Recording from the repository data. The new Recording will
            be available in a Recordings section. From/To specifies the time-range
            for the files to be included in the new generated Recording
            (based on the latest modification date-time of the file).
            It's not based on the exact recorded time of the events, it's approximate
            and impacted by the length of the files in the repository.
            Consider smaller files for a more accurate result (5 minutes, 10 minutes, etc.)
          </p>
          
          <p class="text-secondary mb-4">
            File-Pattern can contain a prefix with a slash indicating a "folder" in the
            Recordings section and <span class="fw-bold">%t</span> for replacing timestamps,
            e.g.
          </p>
          
          <ul>
            <li><span class="fst-italic">generated/recording-%t.jfr</span></li>
            <li><span class="fst-italic">generated/recording-2024-01-01-000000.jfr</span></li>
          </ul>
          
          <div class="alert alert-danger" v-if="generatorErrorMessage">
            {{ generatorErrorMessage }}
          </div>

          <div class="mb-3 row">
            <label for="generateAt" class="col-sm-3 col-form-label">Generate At</label>
            <div class="col-sm-9">
              <input type="time" class="form-control" id="generateAt" v-model="generatorAt" autocomplete="off">
            </div>
          </div>
          
          <div class="mb-3 row">
            <label for="filepattern" class="col-sm-3 col-form-label">File Pattern</label>
            <div class="col-sm-9">
              <input type="text" class="form-control" id="filepattern" v-model="generatorFilePattern" autocomplete="off">
            </div>
          </div>
          
          <div class="mb-3 row">
            <label for="from" class="col-sm-3 col-form-label">Time From</label>
            <div class="col-sm-4">
              <input type="time" class="form-control" id="from" v-model="generatorFrom" autocomplete="off">
            </div>
            
            <label for="to" class="col-sm-1 col-form-label">To</label>
            <div class="col-sm-4">
              <input type="time" class="form-control" id="to" v-model="generatorTo" autocomplete="off">
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="closeGeneratorModal()">Cancel</button>
          <button type="button" class="btn btn-primary" @click="saveGeneratorJob()">Save a new Job</button>
        </div>
      </div>
    </div>
  </div>

  <!-- Modal Backdrops -->
  <div class="modal-backdrop show" v-if="showCleanerDialog || showGeneratorDialog"></div>
</template>

<style scoped>
.bg-teal {
  background-color: #20c997;
}

.text-teal {
  color: #20c997;
}

/* Modal styling */
.modal.show {
  display: block !important;
}

body.modal-open {
  overflow: hidden;
  padding-right: 17px; /* Adjusts for scrollbar width to prevent layout shift */
}
</style>
