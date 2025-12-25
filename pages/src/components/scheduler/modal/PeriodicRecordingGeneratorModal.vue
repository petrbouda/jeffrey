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
import { ref, watch } from 'vue';
import Utils from "@/services/Utils";
import ProjectSchedulerClient from "@/services/api/ProjectSchedulerClient.ts";
import { JobType } from "@/services/api/model/JobType.ts";
import ToastService from "@/services/ToastService";

interface DialogMessage {
  severity: string;
  content: string;
}

const props = defineProps<{
  show: boolean;
  schedulerService: ProjectSchedulerClient;
}>();

const emit = defineEmits<{
  close: [];
  saved: [];
}>();

const filePattern = ref('periodic-15min/recording-%t.jfr');
const periodPresets = ref(['1 min', '5 min', '15 min', '1 hour', 'Custom']);
const selectedPeriod = ref('15 min');
const customPeriod = ref(15);
const customTimeUnit = ref('Minutes');
const timeUnits = ref(['Minutes', 'Hours']);
const maxRecordings = ref(10);
const messages = ref<DialogMessage[]>([]);

const closeModal = () => {
  resetForm();
  emit('close');
};

// Function to calculate the duration string based on period
const calculateDurationString = () => {
  let periodInMinutes = 15; // Default to 15 minutes

  if (selectedPeriod.value === 'Custom') {
    // Convert custom period to minutes
    if (customTimeUnit.value === 'Hours') {
      periodInMinutes = customPeriod.value * 60;
    } else {
      periodInMinutes = customPeriod.value;
    }
  } else {
    // Parse the preset period value
    const periodValue = selectedPeriod.value;
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
};

// Function to update the file pattern based on the current period
const updateFilePattern = () => {
  const durationString = calculateDurationString();
  filePattern.value = `periodic-${durationString}/recording-%t.jfr`;
};

const saveJob = async () => {
  if (Utils.isBlank(filePattern.value) ||
      (selectedPeriod.value === 'Custom' && !Utils.isPositiveNumber(customPeriod.value))) {
    messages.value = [{severity: 'error', content: 'All fields are required and custom period must be a positive number'}];
    return;
  }
  messages.value = [];

  // Calculate period in minutes
  let periodInMinutes = 15; // Default to 15 minutes

  if (selectedPeriod.value === 'Custom') {
    // Convert custom period to minutes
    if (customTimeUnit.value === 'Hours') {
      periodInMinutes = customPeriod.value * 60;
    } else {
      periodInMinutes = customPeriod.value;
    }
  } else {
    // Parse the preset period value
    const periodValue = selectedPeriod.value;
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

  const jobParams = new Map<string, string>();
  jobParams.set('period', periodInMinutes.toString());
  jobParams.set('filePattern', filePattern.value);
  jobParams.set('maxRecordings', maxRecordings.value.toString());

  try {
    await props.schedulerService.create(JobType.PERIODIC_RECORDING_GENERATOR, jobParams);
    ToastService.success('Periodic Recording Generator Job', 'Generator Job has been created');
    emit('saved');
    closeModal();
  } catch (error: any) {
    console.error('Failed to create periodic generator job:', error);
    messages.value = [{
      severity: 'error',
      content: error.response?.data || 'Failed to create job.'
    }];
  }
};

const resetForm = () => {
  selectedPeriod.value = '15 min';
  customPeriod.value = 15;
  customTimeUnit.value = 'Minutes';
  maxRecordings.value = 10;
  messages.value = [];

  // Update the file pattern based on the reset period
  updateFilePattern();
};

// Reset form when modal is shown
watch(() => props.show, (isVisible) => {
  if (isVisible) {
    resetForm();
    // Add modal-open class to body when modal is shown
    document.body.classList.add('modal-open');
  } else {
    // Remove modal-open class from body when modal is hidden
    document.body.classList.remove('modal-open');
  }
});

// Watch for changes to period settings to update file pattern
watch(selectedPeriod, () => {
  updateFilePattern();
});

watch(customPeriod, () => {
  if (selectedPeriod.value === 'Custom') {
    updateFilePattern();
  }
});

watch(customTimeUnit, () => {
  if (selectedPeriod.value === 'Custom') {
    updateFilePattern();
  }
});
</script>

<template>
  <!-- Modal backdrop -->
  <div v-if="props.show" class="modal-backdrop fade show" @click="closeModal"></div>

  <div class="modal fade" :class="{ show: props.show }" id="periodicRecordingGeneratorModal" tabindex="-1"
       aria-labelledby="periodicRecordingGeneratorModalLabel" :aria-hidden="!props.show"
       :style="{ display: props.show ? 'block' : 'none' }">
    <div class="modal-dialog modal-lg">
      <div class="modal-content rounded-1 shadow">
        <div class="modal-header bg-blue-soft border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-arrow-repeat fs-4 me-2 text-blue"></i>
            <h5 class="modal-title mb-0 text-dark" id="periodicRecordingGeneratorModalLabel">Create a Periodic Recording Generator Job</h5>
          </div>
          <button type="button" class="btn-close" @click="closeModal" aria-label="Close"></button>
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
                <input type="text" id="periodicFilepattern" v-model="filePattern"
                       class="form-control search-input" autocomplete="off"/>
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="periodicPeriod" class="col-sm-3 col-form-label fw-medium">Period</label>
            <div class="col-sm-9">
              <div>
                <div class="btn-group" role="group" aria-label="Period presets">
                  <button type="button" class="btn"
                          v-for="preset in periodPresets" :key="preset"
                          :class="selectedPeriod === preset ? 'btn-primary' : 'btn-outline-primary'"
                          @click="selectedPeriod = preset">
                    {{ preset }}
                  </button>
                </div>
              </div>

              <div v-if="selectedPeriod === 'Custom'"
                   class="d-flex gap-3 align-items-center mt-4">
                <div class="input-group search-container flex-grow-1">
                  <span class="input-group-text"><i class="bi bi-clock-history"></i></span>
                  <input type="number" id="customPeriod" v-model="customPeriod"
                         class="form-control search-input" autocomplete="off" min="1"/>
                </div>
                <div class="btn-group" role="group" aria-label="Time units">
                  <button type="button" class="btn"
                          v-for="unit in timeUnits" :key="unit"
                          :class="customTimeUnit === unit ? 'btn-primary' : 'btn-outline-primary'"
                          @click="customTimeUnit = unit">
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
                <input type="number" id="maxRecordings" v-model="maxRecordings"
                       class="form-control search-input" autocomplete="off" min="0"/>
              </div>
              <div class="text-muted small mt-1">
                <i class="bi bi-info-circle me-1"></i>Set to 0 for unlimited recordings
              </div>
            </div>
          </div>

          <div v-if="messages.length > 0" class="alert alert-danger mt-3">
            <div v-for="(msg, idx) in messages" :key="idx">
              <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ msg.content }}
            </div>
          </div>
        </div>
        <div class="modal-footer border-top-0">
          <button type="button" class="btn btn-light" @click="closeModal">Cancel</button>
          <button type="button" class="btn btn-primary" @click="saveJob">
            <i class="bi bi-save me-1"></i> Save Job
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Colors */
.bg-blue-soft {
  background-color: rgba(13, 110, 253, 0.15);
}

.text-blue {
  color: #0d6efd;
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

/* Code styling */
code {
  background-color: #f8f9fa;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.875rem;
  word-break: break-all;
  color: #212529;
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

/* Alert styling */
.alert {
  border: none;
  border-radius: 0.5rem;
}

/* Modal backdrop styling */
.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 1040;
  width: 100vw;
  height: 100vh;
  background-color: #000;
  opacity: 0.5;
}

.modal-backdrop.fade {
  opacity: 0;
}

.modal-backdrop.show {
  opacity: 0.5;
}

/* Ensure modal appears above backdrop */
.modal {
  z-index: 1055;
}
</style>