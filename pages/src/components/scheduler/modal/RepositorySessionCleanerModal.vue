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

const duration = ref(1);
const timeUnit = ref(['Minutes', 'Hours', 'Days']);
const selectedTimeUnit = ref('Days');
const messages = ref<DialogMessage[]>([]);

const closeModal = () => {
  resetForm();
  emit('close');
};

const saveJob = async () => {
  if (!Utils.isPositiveNumber(duration.value)) {
    messages.value = [{severity: 'error', content: '`Max Age` is not a positive number'}];
    return;
  }
  messages.value = [];

  const jobParams = new Map<string, string>();
  jobParams.set('duration', duration.value.toString());
  jobParams.set('timeUnit', selectedTimeUnit.value);


  try {
    await props.schedulerService.create(JobType.REPOSITORY_SESSION_CLEANER, jobParams);
    ToastService.success('Repository Session Cleaner Job', 'Cleaner Job has been created');
    emit('saved');
    closeModal();
  } catch (error: any) {
    console.error('Failed to create cleaner job:', error);
    messages.value = [{
      severity: 'error',
      content: error.response?.data || 'Failed to create job.'
    }];
  }
};

const resetForm = () => {
  duration.value = 1;
  selectedTimeUnit.value = 'Days';
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

  <div class="modal fade" :class="{ show: props.show }" id="repositorySessionCleanerModal" tabindex="-1"
       aria-labelledby="repositorySessionCleanerModalLabel" :aria-hidden="!props.show"
       :style="{ display: props.show ? 'block' : 'none' }">
    <div class="modal-dialog modal-lg">
      <div class="modal-content rounded-2 shadow">
        <div class="modal-header bg-teal-soft border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-trash fs-4 me-2 text-teal"></i>
            <h5 class="modal-title mb-0 text-dark" id="repositorySessionCleanerModalLabel">Create a Repository Session Cleaner Job</h5>
          </div>
          <button type="button" class="btn-close" @click="closeModal" aria-label="Close"></button>
        </div>
        <div class="modal-body pt-4">
          <div class="info-panel mb-4">
            <div class="info-panel-icon">
              <i class="bi bi-info-circle-fill"></i>
            </div>
            <div class="info-panel-content">
              <h6 class="fw-bold mb-1">Repository Session Cleaner</h6>
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
                <input type="number" id="duration" v-model="duration" class="form-control search-input"
                       autocomplete="off" min="1"/>
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label class="col-sm-3 col-form-label fw-medium">Time Unit</label>
            <div class="col-sm-9">
              <div class="btn-group" role="group" aria-label="Time units">
                <button type="button" class="btn"
                        v-for="unit in timeUnit" :key="unit"
                        :class="selectedTimeUnit === unit ? 'btn-primary' : 'btn-outline-primary'"
                        @click="selectedTimeUnit = unit">
                  {{ unit }}
                </button>
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

/* Colors */
.bg-teal-soft {
  background-color: rgba(32, 201, 151, 0.15);
}

.text-teal {
  color: #20C997;
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