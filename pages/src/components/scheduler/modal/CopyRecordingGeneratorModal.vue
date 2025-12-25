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
import { ref } from 'vue';
import Utils from "@/services/Utils";
import ProjectSchedulerClient from "@/services/api/ProjectSchedulerClient";
import { JobType } from "@/services/api/model/JobType";
import ToastService from "@/services/ToastService";
import BaseModal from "@/components/BaseModal.vue";

const props = defineProps<{
  schedulerService: ProjectSchedulerClient;
}>();

const emit = defineEmits<{
  saved: [];
}>();

const baseModalRef = ref<InstanceType<typeof BaseModal> | null>(null);

const filePattern = ref('downloaded');
const maxRecordings = ref(10);
const removeDownloadedFiles = ref(true);
const initializeRecordings = ref(true);
const loading = ref(false);

const showModal = () => {
  resetForm();
  baseModalRef.value?.showModal();
};

const closeModal = () => {
  baseModalRef.value?.hideModal();
};

const handleSubmit = async () => {
  if (Utils.isBlank(filePattern.value) ||
      !Utils.isPositiveNumber(maxRecordings.value)) {
    baseModalRef.value?.setValidationErrors(['All fields are required and max recordings must be a non-negative number']);
    return;
  }
  baseModalRef.value?.clearValidationErrors();

  const jobParams = new Map<string, string>();
  jobParams.set('targetFolder', filePattern.value);
  jobParams.set('maxRecordings', maxRecordings.value.toString());
  jobParams.set('removeDownloadedFiles', removeDownloadedFiles.value.toString());
  jobParams.set('initializeRecordings', initializeRecordings.value.toString());

  loading.value = true;
  try {
    await props.schedulerService.create(JobType.COPY_RECORDING_GENERATOR, jobParams);
    ToastService.success('Download Recording Generator Job', 'Generator Job has been created');
    emit('saved');
    closeModal();
  } catch (error: any) {
    console.error('Failed to create copy generator job:', error);
    baseModalRef.value?.setValidationErrors([error.response?.data || 'Failed to create job.']);
  } finally {
    loading.value = false;
  }
};

const handleCancel = () => {
  closeModal();
};

const resetForm = () => {
  filePattern.value = 'copied';
  maxRecordings.value = 10;
  removeDownloadedFiles.value = true;
  initializeRecordings.value = true;
  baseModalRef.value?.clearValidationErrors();
};

defineExpose({
  showModal,
  closeModal
});
</script>

<template>
  <BaseModal
    ref="baseModalRef"
    modal-id="copyRecordingGeneratorModal"
    title="Create a Download Recording Generator Job"
    icon="bi-clock-history"
    icon-color="text-blue"
    primary-button-text="Save Job"
    primary-button-icon="bi-save"
    :loading="loading"
    @submit="handleSubmit"
    @cancel="handleCancel"
  >
    <template #body>
      <p class="text-muted mb-3">
        Target Folder specifies where the downloaded Raw Recordings will be stored in the
        Recordings section.
      </p>

      <div class="mb-4 row">
        <label for="copyFilepattern" class="col-sm-3 col-form-label fw-medium">Target Folder</label>
        <div class="col-sm-9">
          <input
            id="copyFilepattern"
            type="text"
            class="form-control"
            v-model="filePattern"
            placeholder="e.g., downloaded"
            autocomplete="off"
          />
        </div>
      </div>

      <div class="mb-4 row">
        <label for="copyMaxRecordings" class="col-sm-3 col-form-label fw-medium">Max # of Recordings</label>
        <div class="col-sm-9">
          <input
            id="copyMaxRecordings"
            type="number"
            class="form-control"
            v-model.number="maxRecordings"
            :min="0"
            placeholder="Enter max recordings"
            autocomplete="off"
          />
          <div class="text-muted small mt-1">
            <i class="bi bi-info-circle me-1"></i>Set to 0 for unlimited recordings
          </div>
        </div>
      </div>

      <div class="mb-4 row">
        <label for="removeDownloadedFiles" class="col-sm-3 col-form-label fw-medium">Remove downloaded files</label>
        <div class="col-sm-9">
          <div class="form-check form-switch mt-2">
            <input class="form-check-input switch-lg" type="checkbox" id="removeDownloadedFiles"
                   v-model="removeDownloadedFiles">
          </div>
        </div>
      </div>

      <div class="mb-4 row">
        <label for="initializeRecordings" class="col-sm-3 col-form-label fw-medium">Initialize recordings</label>
        <div class="col-sm-9">
          <div class="form-check form-switch mt-2">
            <input class="form-check-input switch-lg" type="checkbox" id="initializeRecordings"
                   v-model="initializeRecordings">
          </div>
        </div>
      </div>
    </template>
  </BaseModal>
</template>

<style scoped>
.fw-medium {
  font-weight: 500;
}

.form-control {
  border: 1px solid #ced4da;
  height: 38px;
}

.form-control:focus {
  box-shadow: none;
  border-color: #ced4da;
}

/* Large switch */
.switch-lg {
  width: 2.5em;
  height: 1.25em;
}
</style>
