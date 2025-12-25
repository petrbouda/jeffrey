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

const from = ref('00:00');
const filePattern = ref('intervals/recording-%t.jfr');
const to = ref('00:00');
const at = ref('00:00');
const atEnabled = ref(false);
const loading = ref(false);

const showModal = () => {
  resetForm();
  baseModalRef.value?.showModal();
};

const closeModal = () => {
  baseModalRef.value?.hideModal();
};

// Function to add one minute to a time string "HH:MM"
const addOneMinuteToTime = (timeStr: string) => {
  const [hours, minutes] = timeStr.split(':').map(Number);

  let newMinutes = minutes + 1;
  let newHours = hours;

  if (newMinutes >= 60) {
    newMinutes = 0;
    newHours += 1;
  }

  if (newHours >= 24) {
    newHours = 0;
  }

  return `${newHours.toString().padStart(2, '0')}:${newMinutes.toString().padStart(2, '0')}`;
};

const getTime = (timeValue: any) => {
  if (typeof timeValue === 'string' && timeValue.includes(':')) {
    return timeValue;
  }

  if (timeValue instanceof Date) {
    let hour = timeValue.getHours() < 10 ? '0' + timeValue.getHours() : timeValue.getHours();
    let minute = timeValue.getMinutes() < 10 ? '0' + timeValue.getMinutes() : timeValue.getMinutes();
    return hour + ":" + minute;
  }

  return "00:00";
};

const handleSubmit = async () => {
  if (Utils.isBlank(to.value)
      || Utils.isBlank(from.value)
      || Utils.isBlank(filePattern.value)
      || (atEnabled.value && Utils.isBlank(at.value))) {
    baseModalRef.value?.setValidationErrors(['All enabled fields are required']);
    return;
  }
  baseModalRef.value?.clearValidationErrors();

  const atTime = atEnabled.value
    ? getTime(at.value)
    : addOneMinuteToTime(getTime(to.value));

  const jobParams = new Map<string, string>();
  jobParams.set('from', getTime(from.value));
  jobParams.set('to', getTime(to.value));
  jobParams.set('filePattern', filePattern.value);
  jobParams.set('at', atTime);

  loading.value = true;
  try {
    await props.schedulerService.create(JobType.INTERVAL_RECORDING_GENERATOR, jobParams);
    ToastService.success('Interval Recording Generator Job', 'Generator Job has been created');
    emit('saved');
    closeModal();
  } catch (error: any) {
    console.error('Failed to create generator job:', error);
    baseModalRef.value?.setValidationErrors([error.response?.data || 'Failed to create job.']);
  } finally {
    loading.value = false;
  }
};

const handleCancel = () => {
  closeModal();
};

const resetForm = () => {
  from.value = '00:00';
  to.value = '00:00';
  at.value = '00:00';
  atEnabled.value = false;
  filePattern.value = 'generated-intervals/recording-%t.jfr';
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
    modal-id="intervalRecordingGeneratorModal"
    title="Create an Interval Recording Generator Job"
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
        File-Pattern can contain a prefix with a slash indicating a "folder" in the
        Recordings section and <span class="fw-bold">%t</span> for replacing timestamps.
      </p>

      <div class="mb-4 row">
        <label for="filepattern" class="col-sm-3 col-form-label fw-medium">File Pattern</label>
        <div class="col-sm-9">
          <input
            id="filepattern"
            type="text"
            class="form-control"
            v-model="filePattern"
            placeholder="e.g., intervals/recording-%t.jfr"
            autocomplete="off"
          />
        </div>
      </div>

      <div class="mb-4 row">
        <label for="from" class="col-sm-3 col-form-label fw-medium">Time Range</label>
        <div class="col-sm-9">
          <div class="d-flex gap-3 align-items-center">
            <div class="flex-grow-1">
              <label for="from" class="form-label small text-muted mb-1">From</label>
              <input
                id="from"
                type="time"
                class="form-control"
                v-model="from"
                autocomplete="off"
              />
            </div>
            <div class="flex-grow-1">
              <label for="to" class="form-label small text-muted mb-1">To</label>
              <input
                id="to"
                type="time"
                class="form-control"
                v-model="to"
                autocomplete="off"
              />
            </div>
          </div>
        </div>
      </div>

      <div class="mb-4 row">
        <label for="generateAt" class="col-sm-3 col-form-label fw-medium">Generate At</label>
        <div class="col-sm-9">
          <div class="d-flex gap-3 align-items-center">
            <input
              id="generateAt"
              type="time"
              class="form-control flex-grow-1"
              v-model="at"
              autocomplete="off"
              :disabled="!atEnabled"
            />
            <div class="form-check form-switch mb-0">
              <input class="form-check-input" type="checkbox" id="enableGenerateAt"
                     v-model="atEnabled">
              <label class="form-check-label small" for="enableGenerateAt">Custom</label>
            </div>
          </div>
          <div class="text-muted small mt-1" v-if="!atEnabled">
            <i class="bi bi-info-circle me-1"></i>Generate automatically 1 minute after the "To" time
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

.form-control:disabled {
  opacity: 0.65;
  background-color: #e9ecef;
}
</style>
