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
import { onBeforeUnmount, onMounted, ref } from 'vue';
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
  if (isTransitional()) {
    return;
  }
  if (menuOpen.value) {
    menuOpen.value = false;
    return;
  }
  emit('click');
};

const onDragStart = (event: DragEvent) => {
  if (!props.draggable || !event.dataTransfer) return;
  event.dataTransfer.effectAllowed = 'move';
  event.dataTransfer.setData('text/plain', props.recordingId);
};

const formatRelativeTime = (timestamp: number) => {
  return FormattingService.formatRelativeTime(timestamp);
};

// In-card action bar (no popover — sidesteps overflow/positioning issues)
type MenuAction = 'edit-profile' | 'delete-profile' | 'delete-recording';
const cardRef = ref<HTMLElement | null>(null);
const menuOpen = ref(false);

const toggleMenu = () => {
  menuOpen.value = !menuOpen.value;
};

const menuAction = (action: MenuAction) => {
  menuOpen.value = false;
  if (action === 'edit-profile') {
    emit('edit-profile');
  } else if (action === 'delete-profile') {
    emit('delete-profile');
  } else {
    emit('delete-recording');
  }
};

const handleDocumentClick = (event: MouseEvent) => {
  if (!menuOpen.value || !cardRef.value) {
    return;
  }
  const target = event.target as Node | null;
  if (target && !cardRef.value.contains(target)) {
    menuOpen.value = false;
  }
};

onMounted(() => {
  document.addEventListener('mousedown', handleDocumentClick);
});

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleDocumentClick);
});
</script>

<template>
  <div
    ref="cardRef"
    class="rec-card"
    :class="{
      'rec-card--analyzed': hasProfile && profileEnabled && !deletingProfile,
      'rec-card--analyzing':
        analyzing || creatingProfile || (hasProfile && !profileEnabled && !deletingProfile),
      'rec-card--deleting': deletingProfile,
      'rec-card--heap-dump': sourceType === 'HEAP_DUMP',
      'rec-card--menu-open': menuOpen
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
            :class="sourceType === 'HEAP_DUMP' ? 'bi bi-pie-chart-fill' : 'bi bi-activity'"
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
          <span
            v-if="!isTransitional()"
            class="rec-card__hint"
            :class="{
              'rec-card__hint--analyze': !hasProfile,
              'rec-card__hint--open': hasProfile && profileEnabled && sourceType !== 'HEAP_DUMP',
              'rec-card__hint--open-heap':
                hasProfile && profileEnabled && sourceType === 'HEAP_DUMP'
            }"
          >
            <template v-if="!hasProfile">Click to analyze</template>
            <template v-else>Open profile</template>
          </span>
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

        <!-- Transitional spinner pills -->
        <span v-if="analyzing || creatingProfile" class="rec-card__spinner-pill">
          <span class="rec-card__spinner"></span>
          {{ analyzing ? 'Analyzing…' : 'Creating…' }}
        </span>
        <span
          v-else-if="deletingProfile"
          class="rec-card__spinner-pill rec-card__spinner-pill--danger"
        >
          <span class="rec-card__spinner rec-card__spinner--danger"></span>
          Deleting…
        </span>
        <span v-else-if="hasProfile && !profileEnabled" class="rec-card__spinner-pill">
          <span class="rec-card__spinner"></span>
          Initializing…
        </span>

        <!-- Actions toggle (⋯ / chevron) -->
        <button
          v-else
          class="rec-card__menu-btn"
          :class="{ 'rec-card__menu-btn--active': menuOpen }"
          :title="menuOpen ? 'Close actions' : 'More actions'"
          @click.stop="toggleMenu"
        >
          <i class="bi" :class="menuOpen ? 'bi-chevron-up' : 'bi-three-dots'"></i>
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

    <!-- In-card action bar -->
    <div v-if="menuOpen" class="rec-card__action-bar" @click.stop>
      <template v-if="hasProfile">
        <button class="rec-card__action" @click="menuAction('edit-profile')">
          <i class="bi bi-pencil"></i>
          Edit profile name
        </button>
        <button
          class="rec-card__action rec-card__action--danger"
          @click="menuAction('delete-profile')"
        >
          <i class="bi bi-person-x"></i>
          Delete profile
        </button>
      </template>
      <button
        class="rec-card__action rec-card__action--danger"
        @click="menuAction('delete-recording')"
      >
        <i class="bi bi-trash"></i>
        Delete recording
      </button>
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
  border-left: 3px dashed var(--color-primary-border);
  background: var(--color-light);
  transition: all 0.2s ease;
  cursor: pointer;
}

.rec-card:hover {
  border-color: rgba(94, 100, 255, 0.3);
  border-left-color: var(--color-primary);
  border-left-style: solid;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.04), rgba(94, 100, 255, 0.03));
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.12);
}

/* Analyzed state */
.rec-card--analyzed {
  border-left: 3px solid var(--color-primary);
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.03), var(--color-white));
}

.rec-card--analyzed:hover {
  border-color: rgba(94, 100, 255, 0.3);
  border-left-color: var(--color-primary-hover);
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.06), rgba(94, 100, 255, 0.04));
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.15);
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
  border-left: 3px dashed rgba(111, 66, 193, 0.4);
}

.rec-card--heap-dump:hover {
  border-color: rgba(111, 66, 193, 0.3);
  border-left-color: var(--color-purple);
  border-left-style: solid;
  background: linear-gradient(135deg, rgba(111, 66, 193, 0.04), rgba(111, 66, 193, 0.02));
  box-shadow: 0 2px 8px rgba(111, 66, 193, 0.12);
}

/* Heap dump: analyzed */
.rec-card--heap-dump.rec-card--analyzed {
  border-left: 3px solid var(--color-purple);
  background: linear-gradient(135deg, rgba(111, 66, 193, 0.03), var(--color-white));
}

.rec-card--heap-dump.rec-card--analyzed:hover {
  border-color: rgba(111, 66, 193, 0.3);
  border-left-color: var(--color-purple-hover);
  background: linear-gradient(135deg, rgba(111, 66, 193, 0.08), rgba(111, 66, 193, 0.04));
  box-shadow: 0 2px 8px rgba(111, 66, 193, 0.15);
}

.rec-card--heap-dump .rec-card__icon,
.rec-card--heap-dump.rec-card--analyzed .rec-card__icon {
  color: var(--color-purple);
}

.rec-card--heap-dump.rec-card--analyzed .rec-card__profile-info {
  color: var(--color-purple);
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
  color: var(--color-primary);
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
  color: var(--color-primary);
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

/* Action hint — pushed to the right edge of meta line, hidden until row hover */
.rec-card__hint {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-left: auto;
  padding-left: 12px;
  font-size: var(--font-size-sm);
  font-weight: 500;
  opacity: 0;
  transform: translateX(4px);
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
  pointer-events: none;
  user-select: none;
  white-space: nowrap;
}

.rec-card:hover .rec-card__hint {
  opacity: 1;
  transform: translateX(0);
}

.rec-card__hint i {
  font-size: 0.72rem;
  transition: transform 0.15s ease;
}

.rec-card:hover .rec-card__hint i.bi-arrow-right {
  transform: translateX(2px);
}

.rec-card__hint--analyze {
  color: var(--color-success);
}

.rec-card__hint--open {
  color: var(--color-primary);
}

.rec-card__hint--open-heap {
  color: var(--color-purple);
}

/* ⋯ / chevron toggle button */
.rec-card__menu-btn {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-base);
  color: var(--color-text-muted);
  cursor: pointer;
  font-size: 1rem;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.rec-card__menu-btn:hover,
.rec-card__menu-btn--active {
  background: var(--color-bg-hover-alt);
  color: var(--color-dark);
}

/* In-card action bar (slides in below the meta line when ⋯ is open) */
.rec-card__action-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--color-border-light);
  flex-wrap: wrap;
}

.rec-card__action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: var(--radius-base);
  background: transparent;
  border: 1px solid var(--color-border);
  font: inherit;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-text);
  cursor: pointer;
  transition:
    background 0.12s ease,
    border-color 0.12s ease,
    color 0.12s ease;
}

.rec-card__action i {
  font-size: 0.78rem;
}

.rec-card__action:hover {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.rec-card__action--danger {
  color: var(--color-danger);
  border-color: rgba(230, 55, 87, 0.25);
}

.rec-card__action--danger:hover {
  background: var(--color-danger-light);
  border-color: var(--color-danger);
  color: var(--color-danger);
}

/* Subtle elevation when the action bar is open */
.rec-card--menu-open {
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.1);
}

/* Spinner pill (transitional states) */
.rec-card__spinner-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 14px;
  border-radius: 6px;
  background: var(--color-lighter);
  color: var(--color-text-muted);
  font-size: 0.82rem;
  font-weight: 500;
  cursor: default;
}

.rec-card__spinner-pill--danger {
  color: var(--color-danger-hover);
  background: var(--color-danger-light);
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
