<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<script setup lang="ts">
import FormattingService from '@/services/FormattingService';

interface RecordingOrigin {
  server: string;
  workspace: string;
  project: string;
}

interface Props {
  recordingId: string;
  name: string;
  sizeInBytes: number;
  durationInMillis: number;
  uploadedAt: number;
  sourceType?: string;
  fileCount?: number;
  hasProfile: boolean;
  profileId?: string | null;
  profileEnabled?: boolean;
  profileSizeInBytes?: number;
  profileModified?: boolean;
  analyzing?: boolean;
  creatingProfile?: boolean;
  deletingProfile?: boolean;
  expandable?: boolean;
  expanded?: boolean;
  draggable?: boolean;
  origin?: RecordingOrigin;
}

const props = withDefaults(defineProps<Props>(), {
  profileEnabled: true,
  profileModified: false,
  analyzing: false,
  creatingProfile: false,
  deletingProfile: false,
  expandable: false,
  expanded: false,
  draggable: false
});

const emit = defineEmits<{
  (e: 'click'): void;
  (e: 'create-profile'): void;
  (e: 'open-profile'): void;
  (e: 'edit-profile'): void;
  (e: 'delete-profile'): void;
  (e: 'delete-recording'): void;
  (e: 'toggle-expand'): void;
  (e: 'dragend'): void;
}>();

const isTransitional = () =>
  props.analyzing ||
  props.creatingProfile ||
  props.deletingProfile ||
  (props.hasProfile && !props.profileEnabled);

const handleClick = () => {
  if (!isTransitional()) {
    emit('click');
  }
};

const onDragStart = (event: DragEvent) => {
  if (!props.draggable || !event.dataTransfer) return;
  event.dataTransfer.effectAllowed = 'move';
  event.dataTransfer.setData('text/plain', props.recordingId);
};

const formatRelativeTime = (timestamp: number) => {
  return FormattingService.formatRelativeTime(timestamp);
};
</script>

<template>
  <div
    class="rec-card"
    :class="{
      'rec-card--analyzed': hasProfile && profileEnabled && !deletingProfile,
      'rec-card--analyzing':
        analyzing || creatingProfile || (hasProfile && !profileEnabled && !deletingProfile),
      'rec-card--deleting': deletingProfile,
      'rec-card--heap-dump': sourceType === 'HEAP_DUMP'
    }"
    @click="handleClick"
  >
    <!-- Two-column layout: left (name + metadata), right (actions) -->
    <div class="rec-card__body">
      <!-- Left: info -->
      <div class="rec-card__info">
        <div class="rec-card__line1">
          <i
            class="rec-card__icon"
            :class="sourceType === 'HEAP_DUMP' ? 'bi bi-database' : 'bi bi-file-earmark-binary'"
          ></i>
          <span class="rec-card__name">{{ name }}</span>
          <span v-if="origin" class="rec-card__origin" :title="`From ${origin.server} › ${origin.workspace} › ${origin.project}`">
            <i class="bi bi-arrow-down-circle-fill"></i>
            <span class="rec-card__origin-part">{{ origin.server }}</span>
            <span class="rec-card__origin-sep">›</span>
            <span class="rec-card__origin-part">{{ origin.workspace }}</span>
            <span class="rec-card__origin-sep">›</span>
            <span class="rec-card__origin-part rec-card__origin-part--project">{{ origin.project }}</span>
          </span>
        </div>
        <div class="rec-card__line2">
          <span class="rec-card__meta">{{ FormattingService.formatBytes(sizeInBytes) }}</span>
          <template v-if="durationInMillis > 0">
            <span class="rec-card__sep">&middot;</span>
            <span class="rec-card__meta">{{
              FormattingService.formatDurationInMillis2Units(durationInMillis)
            }}</span>
          </template>
          <span class="rec-card__sep">&middot;</span>
          <span class="rec-card__meta">{{ formatRelativeTime(uploadedAt) }}</span>
          <template v-if="fileCount != null && fileCount > 0 && !expandable">
            <span class="rec-card__sep">&middot;</span>
            <span class="rec-card__meta">{{ fileCount }} file{{ fileCount !== 1 ? 's' : '' }}</span>
          </template>
          <template v-if="hasProfile && profileSizeInBytes && profileSizeInBytes > 0">
            <span class="rec-card__sep">&middot;</span>
            <span class="rec-card__profile-info">
              <i class="bi bi-check-circle-fill"></i>
              Profile: {{ FormattingService.formatBytes(profileSizeInBytes) }}
            </span>
          </template>
          <template v-if="hasProfile && profileModified">
            <span class="rec-card__sep">&middot;</span>
            <span class="rec-card__modified">
              <i class="bi bi-exclamation-triangle-fill"></i>
              Modified
            </span>
          </template>
        </div>
      </div>

      <!-- Right: actions spanning both lines -->
      <div class="rec-card__actions">
        <!-- Files toggle -->
        <button
          v-if="expandable && fileCount != null && fileCount > 0"
          class="rec-card__files-toggle"
          :class="{ 'rec-card__files-toggle--active': expanded }"
          @click.stop="emit('toggle-expand')"
        >
          <i class="bi bi-files"></i>
          {{ fileCount }} file{{ fileCount !== 1 ? 's' : '' }}
          <i class="bi" :class="expanded ? 'bi-chevron-up' : 'bi-chevron-down'"></i>
        </button>

        <!-- Not analyzed: Analyze button -->
        <button
          v-if="!hasProfile && !analyzing && !creatingProfile"
          class="rec-card__btn rec-card__btn--analyze"
          @click.stop="emit('create-profile')"
        >
          <i class="bi bi-play-fill"></i>
          Analyze
        </button>

        <!-- Analyzing / Creating spinner -->
        <span v-else-if="analyzing || creatingProfile" class="rec-card__btn rec-card__btn--spinner">
          <div class="rec-card__spinner"></div>
          {{ analyzing ? 'Analyzing...' : 'Creating...' }}
        </span>

        <!-- Deleting spinner -->
        <span
          v-else-if="deletingProfile"
          class="rec-card__btn rec-card__btn--spinner rec-card__btn--spinner-danger"
        >
          <div class="rec-card__spinner rec-card__spinner--danger"></div>
          Deleting...
        </span>

        <!-- Initializing (has profile but not enabled yet) -->
        <span
          v-else-if="hasProfile && !profileEnabled"
          class="rec-card__btn rec-card__btn--spinner"
        >
          <div class="rec-card__spinner"></div>
          Initializing...
        </span>

        <!-- Analyzed + enabled: Open Profile -->
        <button
          v-else-if="hasProfile && profileEnabled"
          class="rec-card__btn rec-card__btn--open"
          @click.stop="emit('open-profile')"
        >
          Open Profile
          <i class="bi bi-arrow-right"></i>
        </button>

        <!-- Action icon buttons -->
        <button
          v-if="hasProfile && profileEnabled && !deletingProfile"
          class="rec-card__action-btn"
          @click.stop="emit('edit-profile')"
          title="Edit Profile"
        >
          <i class="bi bi-pencil"></i>
        </button>
        <button
          v-if="hasProfile && !deletingProfile"
          class="rec-card__action-btn rec-card__action-btn--danger"
          @click.stop="emit('delete-profile')"
          title="Delete Profile"
        >
          <i class="bi bi-person-x"></i>
        </button>
        <button
          class="rec-card__action-btn rec-card__action-btn--danger"
          @click.stop="emit('delete-recording')"
          title="Delete"
        >
          <i class="bi bi-trash"></i>
        </button>
        <div
          v-if="draggable"
          class="rec-card__drag-handle"
          draggable="true"
          title="Drag to move to another group"
          @dragstart="onDragStart"
          @dragend="emit('dragend')"
          @click.stop
        >
          <i class="bi bi-grip-vertical"></i>
        </div>
      </div>
    </div>

    <!-- Expanded content slot -->
    <div v-if="expanded" class="rec-card__expanded" @click.stop>
      <slot name="expanded-content"></slot>
    </div>
  </div>
</template>

<style scoped>
/* Card base */
.rec-card {
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid var(--color-border);
  border-left: 3px dashed var(--color-blue-border-light);
  background: var(--color-light);
  transition: all 0.2s ease;
  cursor: pointer;
}

.rec-card:hover {
  border-color: rgba(59, 130, 246, 0.3);
  border-left-color: rgba(59, 130, 246, 1);
  border-left-style: solid;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.04), rgba(59, 130, 246, 0.03));
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.12);
}

/* Analyzed state */
.rec-card--analyzed {
  border-left: 3px solid var(--color-success);
  background: linear-gradient(135deg, rgba(0, 210, 122, 0.03), var(--color-white));
}

.rec-card--analyzed:hover {
  border-color: rgba(0, 210, 122, 0.3);
  border-left-color: var(--color-success-hover);
  background: linear-gradient(135deg, rgba(0, 210, 122, 0.06), rgba(0, 210, 122, 0.04));
  box-shadow: 0 2px 8px rgba(0, 210, 122, 0.15);
}

/* Analyzing / Creating state */
.rec-card--analyzing {
  border-left: 3px solid var(--color-primary);
  animation: rec-card-pulse 2s ease-in-out infinite;
  cursor: default;
}

.rec-card--analyzing:hover {
  transform: none;
  box-shadow: none;
}

/* Deleting state */
.rec-card--deleting {
  border-left: 3px dashed var(--color-danger-hover);
  opacity: 0.6;
  cursor: default;
}

.rec-card--deleting:hover {
  border-left-color: var(--color-danger-hover);
  background: var(--color-light);
  box-shadow: none;
}

/* Heap dump: not analyzed */
.rec-card--heap-dump {
  border-left: 3px dashed var(--color-violet-border-light);
}

.rec-card--heap-dump:hover {
  border-color: rgba(139, 92, 246, 0.3);
  border-left-color: var(--color-violet);
  border-left-style: solid;
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.04), rgba(139, 92, 246, 0.02));
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.12);
}

/* Heap dump: analyzed */
.rec-card--heap-dump.rec-card--analyzed {
  border-left: 3px solid var(--color-violet);
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.03), var(--color-white));
}

.rec-card--heap-dump.rec-card--analyzed:hover {
  border-color: rgba(139, 92, 246, 0.3);
  border-left-color: var(--color-violet-dark);
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.08), rgba(139, 92, 246, 0.04));
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.15);
}

.rec-card--heap-dump.rec-card--analyzed .rec-card__icon {
  color: var(--color-violet);
}

.rec-card--heap-dump.rec-card--analyzed .rec-card__btn--open {
  color: var(--color-violet);
  border-color: rgba(139, 92, 246, 0.3);
}

.rec-card--heap-dump:hover .rec-card__btn--open {
  background: linear-gradient(135deg, var(--color-violet), var(--color-violet-dark));
  color: var(--color-white);
  border-color: transparent;
}

.rec-card--heap-dump.rec-card--analyzed .rec-card__profile-info {
  color: var(--color-violet);
}

@keyframes rec-card-pulse {
  0%,
  100% {
    background: var(--color-light);
  }
  50% {
    background: rgba(94, 100, 255, 0.06);
  }
}

/* Two-column body */
.rec-card__body {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rec-card__info {
  flex: 1;
  min-width: 0;
}

/* Line 1: icon + name */
.rec-card__line1 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.rec-card__icon {
  font-size: 1.05rem;
  color: var(--color-primary);
  flex-shrink: 0;
}

.rec-card--analyzed .rec-card__icon {
  color: var(--color-success-hover);
}

.rec-card__name {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

/* Origin breadcrumb (auto-downloaded recordings) */
.rec-card__origin {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  font-size: 0.7rem;
  color: var(--color-text-muted);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  padding: 2px 8px;
  font-weight: 500;
  max-width: 50%;
  overflow: hidden;
}

.rec-card__origin > i {
  color: var(--color-success-hover);
  font-size: 0.7rem;
}

.rec-card__origin-part {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.rec-card__origin-part--project {
  color: var(--color-dark);
  font-weight: 600;
}

.rec-card__origin-sep {
  color: var(--color-muted-separator);
  flex-shrink: 0;
}

/* Line 2: metadata */
.rec-card__line2 {
  display: flex;
  align-items: center;
  gap: 7px;
  padding-left: 26px;
}

/* Right: actions */
.rec-card__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.rec-card__meta {
  font-size: 0.78rem;
  color: var(--color-text-light);
}

.rec-card__sep {
  color: var(--color-muted-separator);
  font-size: 0.65rem;
}

.rec-card__profile-info {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--color-success-hover);
}

.rec-card__profile-info i {
  font-size: 0.7rem;
}

.rec-card__modified {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--color-warning);
}

.rec-card__modified i {
  font-size: 0.65rem;
}

/* Files toggle button (line 2) */
.rec-card__files-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border-radius: 6px;
  border: 1px solid var(--color-border);
  background: var(--color-light);
  color: var(--color-text-muted);
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.rec-card__files-toggle:hover {
  background: rgba(94, 100, 255, 0.06);
  border-color: rgba(94, 100, 255, 0.3);
  color: var(--color-primary);
}

.rec-card__files-toggle--active {
  background: rgba(94, 100, 255, 0.06);
  border-color: rgba(94, 100, 255, 0.25);
  color: var(--color-primary);
}

.rec-card__files-toggle i {
  font-size: 0.75rem;
}

/* Primary action buttons */
.rec-card__btn {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.84rem;
  font-weight: 500;
  padding: 7px 16px;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.rec-card__btn--analyze {
  background: transparent;
  color: var(--color-primary);
  border: 1px solid rgba(94, 100, 255, 0.3);
}

.rec-card:hover .rec-card__btn--analyze {
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-hover));
  color: var(--color-white);
  border-color: transparent;
}

.rec-card__btn--open {
  background: transparent;
  color: var(--color-success-hover);
  border: 1px solid rgba(0, 210, 122, 0.3);
}

.rec-card:not(.rec-card--heap-dump):hover .rec-card__btn--open {
  background: linear-gradient(135deg, var(--color-success), var(--color-success-hover));
  color: var(--color-white);
  border-color: transparent;
}

.rec-card__btn--spinner {
  color: var(--color-primary);
  padding: 7px 16px;
  cursor: default;
}

.rec-card__btn--spinner-danger {
  color: var(--color-danger-hover);
}

/* Spinner */
.rec-card__spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(94, 100, 255, 0.2);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: rec-card-spin 0.8s linear infinite;
  flex-shrink: 0;
}

.rec-card__spinner--danger {
  border-color: rgba(220, 38, 38, 0.2);
  border-top-color: var(--color-danger-hover);
}

@keyframes rec-card-spin {
  to {
    transform: rotate(360deg);
  }
}

/* Hover-revealed action buttons */
.rec-card__action-btn {
  background: transparent;
  border: none;
  color: var(--color-slate-light);
  width: 34px;
  height: 34px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.88rem;
  transition: all 0.15s ease;
}

.rec-card__action-btn:hover {
  background: rgba(94, 100, 255, 0.1);
  color: var(--color-primary);
}

.rec-card__action-btn--danger:hover {
  background: rgba(220, 38, 38, 0.1);
  color: var(--color-danger-hover);
}

.rec-card__action-btn:active {
  transform: scale(0.92);
}

/* Drag handle */
.rec-card__drag-handle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 6px;
  color: var(--color-slate-light);
  cursor: grab;
  transition: all 0.15s ease;
  font-size: 1rem;
}

.rec-card__drag-handle:hover {
  background: rgba(94, 100, 255, 0.1);
  color: var(--color-primary);
}

.rec-card__drag-handle:active {
  cursor: grabbing;
}

/* Expanded content */
.rec-card__expanded {
  margin-top: 8px;
  padding-top: 8px;
  padding-left: 12px;
  border-left: 2px solid var(--color-border);
  margin-left: 10px;
}
</style>
