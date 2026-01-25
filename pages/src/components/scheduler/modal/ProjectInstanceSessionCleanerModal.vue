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
import { ref, computed } from 'vue';
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

const duration = ref(1);
const timeUnits = ['Minutes', 'Hours', 'Days'];
const selectedTimeUnit = ref('Days');
const loading = ref(false);
const currentJobType = ref<string>(JobType.PROJECT_INSTANCE_SESSION_CLEANER);

const isSessionCleaner = computed(() => currentJobType.value === JobType.PROJECT_INSTANCE_SESSION_CLEANER);

const modalTitle = computed(() =>
  isSessionCleaner.value
    ? 'Create a Project Instance Session Cleaner Job'
    : 'Create a Project Instance Recording Cleaner Job'
);

const jobName = computed(() =>
  isSessionCleaner.value
    ? 'Project Instance Session Cleaner'
    : 'Project Instance Recording Cleaner'
);

const jobDescription = computed(() =>
  isSessionCleaner.value
    ? 'Fill in a duration for how long to keep sessions in the repository. Sessions with the last modification date older than the given duration will be removed along with all their recordings and additional files.'
    : 'Fill in a duration for how long to keep recordings in the active (latest) session. Only recordings older than the given duration will be removed. Older sessions are not affected.'
);

const showModal = (jobType: string = JobType.PROJECT_INSTANCE_SESSION_CLEANER) => {
  currentJobType.value = jobType;
  resetForm();
  baseModalRef.value?.showModal();
};

const closeModal = () => {
  baseModalRef.value?.hideModal();
};

const handleSubmit = async () => {
  if (!Utils.isPositiveNumber(duration.value)) {
    baseModalRef.value?.setValidationErrors(['`Max Age` is not a positive number']);
    return;
  }
  baseModalRef.value?.clearValidationErrors();

  const jobParams = new Map<string, string>();
  jobParams.set('duration', duration.value.toString());
  jobParams.set('timeUnit', selectedTimeUnit.value);

  loading.value = true;
  try {
    await props.schedulerService.create(currentJobType.value, jobParams);
    ToastService.success('Job Created', `${jobName.value} has been created successfully`);
    emit('saved');
    closeModal();
  } catch (error: any) {
    console.error('Failed to create cleaner job:', error);
    baseModalRef.value?.setValidationErrors([error.response?.data || 'Failed to create job.']);
  } finally {
    loading.value = false;
  }
};

const handleCancel = () => {
  closeModal();
};

const resetForm = () => {
  duration.value = 1;
  selectedTimeUnit.value = 'Days';
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
    modal-id="projectInstanceSessionCleanerModal"
    :title="modalTitle"
    icon="bi-trash"
    icon-color="text-teal"
    primary-button-text="Save Job"
    primary-button-icon="bi-save"
    :loading="loading"
    :show-description-card="true"
    @submit="handleSubmit"
    @cancel="handleCancel"
  >
    <template #description>
      <h6 class="fw-bold mb-1">{{ jobName }}</h6>
      <p class="mb-0">{{ jobDescription }}</p>
    </template>

    <template #body>
      <div class="mb-4 row">
        <label for="duration" class="col-sm-3 col-form-label fw-medium">Max Age</label>
        <div class="col-sm-9">
          <input
            id="duration"
            type="number"
            class="form-control"
            v-model.number="duration"
            :min="1"
            placeholder="Enter duration"
            autocomplete="off"
          />
        </div>
      </div>

      <div class="mb-4 row">
        <label class="col-sm-3 col-form-label fw-medium">Time Unit</label>
        <div class="col-sm-9">
          <div class="btn-group" role="group" aria-label="Time units">
            <button type="button" class="btn"
                    v-for="unit in timeUnits" :key="unit"
                    :class="selectedTimeUnit === unit ? 'btn-primary' : 'btn-outline-primary'"
                    @click="selectedTimeUnit = unit">
              {{ unit }}
            </button>
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

/* Teal color for icon */
.text-teal {
  color: #20C997;
}
</style>
