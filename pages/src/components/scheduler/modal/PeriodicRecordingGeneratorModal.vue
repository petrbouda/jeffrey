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

const filePattern = ref('periodic-15min/recording-%t.jfr');
const periodPresets = ['1 min', '5 min', '15 min', '1 hour', 'Custom'];
const selectedPeriod = ref('15 min');
const customPeriod = ref(15);
const customTimeUnit = ref('Minutes');
const timeUnits = ['Minutes', 'Hours'];
const maxRecordings = ref(10);
const loading = ref(false);

const showModal = () => {
  resetForm();
  baseModalRef.value?.showModal();
};

const closeModal = () => {
  baseModalRef.value?.hideModal();
};

// Function to calculate the duration string based on period
const calculateDurationString = () => {
  let periodInMinutes = 15;

  if (selectedPeriod.value === 'Custom') {
    if (customTimeUnit.value === 'Hours') {
      periodInMinutes = customPeriod.value * 60;
    } else {
      periodInMinutes = customPeriod.value;
    }
  } else {
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

const handleSubmit = async () => {
  if (Utils.isBlank(filePattern.value) ||
      (selectedPeriod.value === 'Custom' && !Utils.isPositiveNumber(customPeriod.value))) {
    baseModalRef.value?.setValidationErrors(['All fields are required and custom period must be a positive number']);
    return;
  }
  baseModalRef.value?.clearValidationErrors();

  // Calculate period in minutes
  let periodInMinutes = 15;

  if (selectedPeriod.value === 'Custom') {
    if (customTimeUnit.value === 'Hours') {
      periodInMinutes = customPeriod.value * 60;
    } else {
      periodInMinutes = customPeriod.value;
    }
  } else {
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

  loading.value = true;
  try {
    await props.schedulerService.create(JobType.PERIODIC_RECORDING_GENERATOR, jobParams);
    ToastService.success('Periodic Recording Generator Job', 'Generator Job has been created');
    emit('saved');
    closeModal();
  } catch (error: any) {
    console.error('Failed to create periodic generator job:', error);
    baseModalRef.value?.setValidationErrors([error.response?.data || 'Failed to create job.']);
  } finally {
    loading.value = false;
  }
};

const handleCancel = () => {
  closeModal();
};

const resetForm = () => {
  selectedPeriod.value = '15 min';
  customPeriod.value = 15;
  customTimeUnit.value = 'Minutes';
  maxRecordings.value = 10;
  updateFilePattern();
  baseModalRef.value?.clearValidationErrors();
};

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

defineExpose({
  showModal,
  closeModal
});
</script>

<template>
  <BaseModal
    ref="baseModalRef"
    modal-id="periodicRecordingGeneratorModal"
    title="Create a Periodic Recording Generator Job"
    icon="bi-arrow-repeat"
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
        Recordings section and <span class="fw-bold">%t</span> for replacing timestamps,
        e.g. <code>periodic-15m/recording-%t.jfr</code>
      </p>

      <div class="mb-4 row">
        <label for="periodicFilepattern" class="col-sm-3 col-form-label fw-medium">File Pattern</label>
        <div class="col-sm-9">
          <input
            id="periodicFilepattern"
            type="text"
            class="form-control"
            v-model="filePattern"
            placeholder="e.g., periodic-15m/recording-%t.jfr"
            autocomplete="off"
          />
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
            <input
              id="customPeriod"
              type="number"
              class="form-control flex-grow-1"
              v-model.number="customPeriod"
              :min="1"
              placeholder="Period"
              autocomplete="off"
            />
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
          <input
            id="maxRecordings"
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

/* Code styling */
code {
  background-color: #f8f9fa;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.875rem;
  word-break: break-all;
  color: #212529;
}
</style>
