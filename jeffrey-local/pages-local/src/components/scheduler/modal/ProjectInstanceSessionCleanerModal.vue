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
import { ref, computed, watch } from 'vue';
import Utils from "@/services/Utils";
import ProjectSchedulerClient from "@/services/api/ProjectSchedulerClient";
import { JobType } from "@/services/api/model/JobType";
import ToastService from "@/services/ToastService";
import GenericModal from "@/components/GenericModal.vue";

const props = defineProps<{
  show: boolean;
  jobType: string;
  schedulerService: ProjectSchedulerClient;
}>();

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
  (e: 'saved'): void;
}>();

const duration = ref(1);
const timeUnits = ['Minutes', 'Hours', 'Days'];
const selectedTimeUnit = ref('Days');
const loading = ref(false);
const validationErrors = ref<string[]>([]);

const modalTitle = computed(() => {
  switch (props.jobType) {
    case JobType.PROJECT_INSTANCE_SESSION_CLEANER:
      return 'Create a Project Instance Session Cleaner Job';
    case JobType.EXPIRED_INSTANCE_CLEANER:
      return 'Create an Expired Instance Cleaner Job';
    default:
      return 'Create a Project Instance Recording Cleaner Job';
  }
});

const jobName = computed(() => {
  switch (props.jobType) {
    case JobType.PROJECT_INSTANCE_SESSION_CLEANER:
      return 'Project Instance Session Cleaner';
    case JobType.EXPIRED_INSTANCE_CLEANER:
      return 'Expired Instance Cleaner';
    default:
      return 'Project Instance Recording Cleaner';
  }
});

const jobDescription = computed(() => {
  switch (props.jobType) {
    case JobType.PROJECT_INSTANCE_SESSION_CLEANER:
      return 'Fill in a duration for how long to keep sessions in the repository. Sessions with the last modification date older than the given duration will be removed along with all their recordings and additional files.';
    case JobType.EXPIRED_INSTANCE_CLEANER:
      return 'Fill in a retention duration for expired instance metadata. Instances that have been in EXPIRED status longer than this duration will be permanently deleted.';
    default:
      return 'Fill in a duration for how long to keep recordings in the active (latest) session. Only recordings older than the given duration will be removed. Older sessions are not affected.';
  }
});

const resetForm = () => {
  duration.value = 1;
  selectedTimeUnit.value = 'Days';
  validationErrors.value = [];
  loading.value = false;
};

const handleSubmit = async () => {
  if (!Utils.isPositiveNumber(duration.value)) {
    validationErrors.value = ['`Max Age` is not a positive number'];
    return;
  }
  validationErrors.value = [];

  const jobParams = new Map<string, string>();
  jobParams.set('duration', duration.value.toString());
  jobParams.set('timeUnit', selectedTimeUnit.value);

  loading.value = true;
  try {
    await props.schedulerService.create(props.jobType, jobParams);
    ToastService.success('Job Created', `${jobName.value} has been created successfully`);
    emit('saved');
    emit('update:show', false);
    resetForm();
  } catch (error: any) {
    console.error('Failed to create cleaner job:', error);
    validationErrors.value = [error.response?.data || 'Failed to create job.'];
  } finally {
    loading.value = false;
  }
};

// Reset form when modal opens
watch(() => props.show, (show) => {
  if (show) {
    resetForm();
  }
});
</script>

<template>
  <GenericModal
      modal-id="projectInstanceSessionCleanerModal"
      :show="show"
      :title="modalTitle"
      icon="bi-trash"
      size="lg"
      :show-footer="true"
      @update:show="$emit('update:show', $event)"
  >
    <template #footer>
      <button type="button" class="btn btn-light" @click="$emit('update:show', false)">
        Cancel
      </button>
      <button
          type="button"
          class="btn btn-primary"
          @click="handleSubmit"
          :disabled="loading"
      >
        <span v-if="loading" class="spinner-border spinner-border-sm me-2" role="status"></span>
        <i v-if="!loading" class="bi bi-save me-1"></i>
        Save Job
      </button>
    </template>

    <!-- Description Card -->
    <div class="modal-description-card mb-4">
      <div class="description-content">
        <h6 class="fw-bold mb-1">{{ jobName }}</h6>
        <p class="mb-0">{{ jobDescription }}</p>
      </div>
    </div>

    <!-- Form -->
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

    <!-- Validation Errors -->
    <div v-if="validationErrors.length > 0" class="alert alert-danger">
      <div v-for="(error, idx) in validationErrors" :key="idx">
        <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ error }}
      </div>
    </div>
  </GenericModal>
</template>

<style scoped>
.modal-description-card {
  background: linear-gradient(135deg, var(--color-light), var(--card-bg));
  border: 1px solid var(--card-border-color);
  border-radius: var(--card-border-radius);
  padding: 0;
}

.description-content {
  padding: 20px 24px;
}

.description-content p {
  font-size: 0.9rem;
  line-height: 1.5;
  color: var(--color-text);
}
</style>
