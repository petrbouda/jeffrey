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
import ProjectSchedulerClient from "@/services/project/ProjectSchedulerClient.ts";
import { JobType } from "@/services/model/JobType.ts";
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

const from = ref('00:00');
const filePattern = ref('intervals/recording-%t.jfr');
const to = ref('00:00');
const at = ref('00:00');
const atEnabled = ref(false);
const messages = ref<DialogMessage[]>([]);

const closeModal = () => {
  resetForm();
  emit('close');
};

// Function to add one minute to a time string "HH:MM"
const addOneMinuteToTime = (timeStr: string) => {
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
};

const getTime = (timeValue: any) => {
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
};

const saveJob = async () => {
  if (Utils.isBlank(to.value)
      || Utils.isBlank(from.value)
      || Utils.isBlank(filePattern.value)
      || (atEnabled.value && Utils.isBlank(at.value))) {
    messages.value = [{severity: 'error', content: 'All enabled fields are required'}];
    return;
  }
  messages.value = [];

  // If Generate At is enabled, use the selected time
  // Otherwise, set it to Time Range "to" + 1 minute
  const atTime = atEnabled.value
    ? getTime(at.value)
    : addOneMinuteToTime(getTime(to.value));

  const jobParams = new Map<string, string>();
  jobParams.set('from', getTime(from.value));
  jobParams.set('to', getTime(to.value));
  jobParams.set('filePattern', filePattern.value);
  jobParams.set('at', atTime);

  try {
    await props.schedulerService.create(JobType.INTERVAL_RECORDING_GENERATOR, jobParams);
    ToastService.success('Interval Recording Generator Job', 'Generator Job has been created');
    emit('saved');
    closeModal();
  } catch (error: any) {
    console.error('Failed to create generator job:', error);
    messages.value = [{
      severity: 'error',
      content: error.response?.data || 'Failed to create job.'
    }];
  }
};

const resetForm = () => {
  from.value = '00:00';
  to.value = '00:00';
  at.value = '00:00';
  atEnabled.value = false;
  filePattern.value = 'generated-intervals/recording-%t.jfr';
  messages.value = [];
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
</script>

<template>
  <!-- Modal backdrop -->
  <div v-if="props.show" class="modal-backdrop fade show" @click="closeModal"></div>

  <div class="modal fade" :class="{ show: props.show }" id="intervalRecordingGeneratorModal" tabindex="-1"
       aria-labelledby="intervalRecordingGeneratorModalLabel" :aria-hidden="!props.show"
       :style="{ display: props.show ? 'block' : 'none' }">
    <div class="modal-dialog modal-lg">
      <div class="modal-content rounded-1 shadow">
        <div class="modal-header bg-blue-soft border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-clock-history fs-4 me-2 text-blue"></i>
            <h5 class="modal-title mb-0 text-dark" id="intervalRecordingGeneratorModalLabel">Create an Interval Recording Generator Job</h5>
          </div>
          <button type="button" class="btn-close" @click="closeModal" aria-label="Close"></button>
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
                <input type="text" id="filepattern" v-model="filePattern"
                       class="form-control search-input" autocomplete="off"/>
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
                  <input type="time" id="from" v-model="from" class="form-control search-input"
                         autocomplete="off"/>
                </div>
                <div class="input-group search-container flex-grow-1">
                  <span class="input-group-text"><i class="bi bi-hourglass-bottom"></i></span>
                  <div class="time-label">To</div>
                  <input type="time" id="to" v-model="to" class="form-control search-input"
                         autocomplete="off"/>
                </div>
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="generateAt" class="col-sm-3 col-form-label fw-medium">Generate At</label>
            <div class="col-sm-9">
              <div class="input-group search-container" :class="{'disabled-input': !atEnabled}">
                <span class="input-group-text"><i class="bi bi-clock"></i></span>
                <input type="time" id="generateAt" v-model="at" class="form-control search-input"
                       autocomplete="off" :disabled="!atEnabled"/>
                <span class="input-group-text toggle-switch">
                  <div class="form-check form-switch mb-0">
                    <input class="form-check-input" type="checkbox" id="enableGenerateAt"
                           v-model="atEnabled">
                    <label class="form-check-label visually-hidden" for="enableGenerateAt">Enable</label>
                  </div>
                </span>
              </div>
              <div class="text-muted small mt-1" v-if="!atEnabled">
                <i class="bi bi-info-circle me-1"></i>Generate automatically 1 minute after the "To" time
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