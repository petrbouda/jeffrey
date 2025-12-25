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

const filePattern = ref('downloaded');
const maxRecordings = ref(10);
const removeDownloadedFiles = ref(true);
const initializeRecordings = ref(true);
const messages = ref<DialogMessage[]>([]);

const closeModal = () => {
  resetForm();
  emit('close');
};

const saveJob = async () => {
  if (Utils.isBlank(filePattern.value) ||
      !Utils.isPositiveNumber(maxRecordings.value)) {
    messages.value = [{severity: 'error', content: 'All fields are required and max recordings must be a non-negative number'}];
    return;
  }
  messages.value = [];

  const jobParams = new Map<string, string>();
  jobParams.set('targetFolder', filePattern.value);
  jobParams.set('maxRecordings', maxRecordings.value.toString());
  jobParams.set('removeDownloadedFiles', removeDownloadedFiles.value.toString());
  jobParams.set('initializeRecordings', initializeRecordings.value.toString());

  try {
    await props.schedulerService.create(JobType.COPY_RECORDING_GENERATOR, jobParams);
    ToastService.success('Download Recording Generator Job', 'Generator Job has been created');
    emit('saved');
    closeModal();
  } catch (error: any) {
    console.error('Failed to create copy generator job:', error);
    messages.value = [{
      severity: 'error',
      content: error.response?.data || 'Failed to create job.'
    }];
  }
};

const resetForm = () => {
  filePattern.value = 'copied';
  maxRecordings.value = 10;
  removeDownloadedFiles.value = true;
  initializeRecordings.value = true;
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

  <div class="modal fade" :class="{ show: props.show }" id="copyRecordingGeneratorModal" tabindex="-1"
       aria-labelledby="copyRecordingGeneratorModalLabel" :aria-hidden="!props.show"
       :style="{ display: props.show ? 'block' : 'none' }">
    <div class="modal-dialog modal-lg">
      <div class="modal-content rounded-1 shadow">
        <div class="modal-header bg-blue-soft border-bottom-0">
          <div class="d-flex align-items-center">
            <i class="bi bi-clock-history fs-4 me-2 text-blue"></i>
            <h5 class="modal-title mb-0 text-dark" id="copyRecordingGeneratorModalLabel">Create a Download Recording Generator Job</h5>
          </div>
          <button type="button" class="btn-close" @click="closeModal" aria-label="Close"></button>
        </div>
        <div class="modal-body pt-4">
          <p class="text-muted mb-3">
            Target Folder specifies where the downloaded Raw Recordings will be stored in the
            Recordings section.
          </p>

          <div class="mb-4 row">
            <label for="copyFilepattern" class="col-sm-3 col-form-label fw-medium">Target Folder</label>
            <div class="col-sm-9">
              <div class="input-group search-container">
                <span class="input-group-text"><i class="bi bi-file-earmark-text"></i></span>
                <input type="text" id="copyFilepattern" v-model="filePattern"
                       class="form-control search-input" autocomplete="off"/>
              </div>
            </div>
          </div>

          <div class="mb-2 row">
            <label for="copyMaxRecordings" class="col-sm-3 col-form-label fw-medium">Max # of Recordings</label>
            <div class="col-sm-9">
              <div class="input-group search-container">
                <span class="input-group-text"><i class="bi bi-list-ol"></i></span>
                <input type="number" id="copyMaxRecordings" v-model="maxRecordings"
                       class="form-control search-input" autocomplete="off" min="0"/>
              </div>
              <div class="text-muted small mt-1">
                <i class="bi bi-info-circle me-1"></i>Set to 0 for unlimited recordings
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="removeDownloadedFiles" class="col-sm-3 col-form-label fw-medium">Remove downloaded files</label>
            <div class="col-sm-9">
              <div class="form-check form-switch mt-2">
                <input class="form-check-input" type="checkbox" id="removeDownloadedFiles"
                       v-model="removeDownloadedFiles">
                <label class="form-check-label" for="removeDownloadedFiles"></label>
              </div>
            </div>
          </div>

          <div class="mb-4 row">
            <label for="initializeRecordings" class="col-sm-3 col-form-label fw-medium">Initialize recordings</label>
            <div class="col-sm-9">
              <div class="form-check form-switch mt-2">
                <input class="form-check-input" type="checkbox" id="initializeRecordings"
                       v-model="initializeRecordings">
                <label class="form-check-label" for="initializeRecordings"></label>
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

/* Form styling */
.form-check-input[id="removeDownloadedFiles"],
.form-check-input[id="initializeRecordings"] {
  width: 2.5em;
  height: 1.25em;
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