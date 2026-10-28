<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div class="project-card-compact border-default" @click="handleClick">
    <!-- Project Name Row -->
    <div class="name-row">
      <span class="project-name">{{ project.name }}</span>
    </div>

    <!-- Footer Row: when its directory was created, where the share lists it; nothing below the project is read -->
    <div class="footer-row">
      <span v-if="project.createdAt" class="date"
        ><i class="bi bi-clock"></i>{{ FormattingService.formatDateFromMillis(project.createdAt) }}</span
      >
    </div>
  </div>
</template>

<script setup lang="ts">
import type Project from '@remote/services/api/model/Project.ts';
import FormattingService from '@shared/services/FormattingService';

const props = defineProps<{
  project: Project;
  workspaceId: string;
}>();

// Navigation is delegated to the host app (route shapes differ per deployment): clicking a
// project emits `open` with the identifiers, and the parent decides how to navigate.
const emit = defineEmits<{
  open: [target: { projectId: string; workspaceId: string }];
}>();

const handleClick = () => {
  emit('open', { projectId: props.project.id, workspaceId: props.workspaceId });
};
</script>

<style scoped>
/* Compact Project Card */
.project-card-compact {
  background: var(--color-white);
  border-radius: 8px;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-primary);
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  gap: 6px;
  height: 100%;
}

.project-card-compact:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(94, 100, 255, 0.12);
  border-color: rgba(94, 100, 255, 0.25);
  border-left-color: var(--color-primary-hover);
}

/* Border Color Classes */
.border-default {
  border-left-color: var(--color-primary);
}

/* Name Row */
.name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

/* Project Name */
.project-name {
  font-weight: 600;
  font-size: 0.95rem;
  color: var(--color-dark);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

/* Footer Row */
.footer-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 2px;
}

.footer-row .date {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.footer-row .date i {
  font-size: 0.7rem;
}
</style>
