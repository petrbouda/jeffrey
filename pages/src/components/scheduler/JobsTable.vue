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

<template>
  <div class="table-responsive">
    <table class="table table-hover mb-0">
      <thead class="table-light">
        <tr>
          <th scope="col" style="width: 30%">Job Type</th>
          <th scope="col" style="width: 60%">Parameters</th>
          <th scope="col" style="width: 10%" class="text-end">Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job in jobs" :key="job.id" :class="{ 'disabled-job': !job.enabled }">
          <td>
            <div class="d-flex align-items-center">
              <template v-if="getJobDisplayInfo(job)">
                <div
                  class="job-icon-sm me-2 d-flex align-items-center justify-content-center"
                  :class="getJobDisplayInfo(job)?.iconBg"
                >
                  <i class="bi" :class="[getJobDisplayInfo(job)?.icon, getJobDisplayInfo(job)?.iconColor]"></i>
                </div>
                <div>
                  <div class="fw-medium">
                    {{ getJobDisplayInfo(job)?.title }}
                    <span v-if="!job.enabled" class="badge bg-warning text-dark ms-2 small">Disabled</span>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="job-icon-sm me-2 d-flex align-items-center justify-content-center bg-secondary-soft">
                  <i class="bi bi-gear text-secondary"></i>
                </div>
                <div>
                  <div class="fw-medium">
                    {{ job.jobType }}
                    <span v-if="!job.enabled" class="badge bg-warning text-dark ms-2 small">Disabled</span>
                  </div>
                </div>
              </template>
            </div>
          </td>
          <td>
            <div class="inline-params">
              <span v-for="(value, key) in job.params" :key="key" class="param-badge">
                <span class="param-key">{{ key }}:</span>
                <span class="param-value">{{ value }}</span>
              </span>
            </div>
          </td>
          <td class="text-end">
            <div class="d-flex justify-content-end gap-2">
              <button
                class="btn btn-sm"
                :class="job.enabled ? 'btn-outline-warning' : 'btn-outline-success'"
                @click="$emit('toggle-enabled', job)"
                :title="job.enabled ? 'Disable job' : 'Enable job'"
              >
                <i class="bi" :class="job.enabled ? 'bi-pause-fill' : 'bi-play-fill'"></i>
              </button>
              <button
                class="btn btn-sm btn-outline-danger"
                @click="$emit('delete', job.id)"
                title="Delete job"
              >
                <i class="bi bi-trash"></i>
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import JobInfo from '@/services/model/JobInfo';
import '@/styles/shared-components.css';

export interface JobDisplayInfo {
  title: string;
  icon: string;
  iconColor: string;
  iconBg: string;
}

interface Props {
  jobs: JobInfo[];
  getJobDisplayInfo: (job: JobInfo) => JobDisplayInfo | null;
}

interface Emits {
  (e: 'toggle-enabled', job: JobInfo): void;
  (e: 'delete', jobId: string): void;
}

defineProps<Props>();
defineEmits<Emits>();
</script>

<style scoped>
.fw-medium {
  font-weight: 500;
}

.bg-secondary-soft {
  background-color: rgba(108, 117, 125, 0.15);
}

/* Import shared styles for job-icon-sm, inline-params, param-badge, etc. */
</style>
