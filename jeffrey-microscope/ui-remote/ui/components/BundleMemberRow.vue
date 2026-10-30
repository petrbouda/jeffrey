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

<!--
  An item of an expanded bundle: the same file row a remote session's detail shows (named after its
  type when read from several files, after its file otherwise), with the item's state on the right —
  Initialize, the stages of a run, Initialized, or Failed with Retry — and its ⋯ actions. Clicking
  the row does what clicking a recording does: initialize it, or offer its profiles.
-->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import Badge from '@shared/components/Badge.vue';
import FormattingService from '@shared/services/FormattingService';
import StageChips from '@shared/components/StageChips.vue';
import type Recording from '../services/api/model/Recording';
import { fromRecordingFile, sortSessionFiles } from '../services/sessionFiles';
import RecordingFileList from './RecordingFileList.vue';
import RecordingFileRow from './RecordingFileRow.vue';
import { memberRowLook } from './bundleMemberRow';
import { memberState } from './bundleSummary';
import { failedStageLabel, toTimelineSteps } from './profileInitChips';
import { builtProfiles } from './recordingColumns';
import { recordingKindOf } from './recordingKinds';
import InitFailedNote from './InitFailedNote.vue';

const props = withDefaults(
  defineProps<{
    member: Recording;
    /** Whether the item's Analyze request is still in flight. */
    analyzing?: boolean;
    /** Epoch millis the parent ticks while an initialization is running. */
    tickNow?: number;
    /** Whether the files of an item read from several files are listed. */
    expanded?: boolean;
  }>(),
  {
    analyzing: false,
    tickNow: 0,
    expanded: false
  }
);

const emit = defineEmits<{
  (e: 'click'): void;
  (e: 'create-profile'): void;
  (e: 'initialize-again'): void;
  (e: 'delete-recording'): void;
  (e: 'toggle-expand'): void;
  (e: 'download', fileId: string): void;
}>();

const look = computed(() => memberRowLook(props.member));
const state = computed(() => memberState(props.member, props.analyzing));
const kind = computed(() => recordingKindOf(props.member.eventSource));

const profileCount = computed(() => builtProfiles(props.member).length);
const initializedLabel = computed(() =>
  profileCount.value > 1 ? `Initialized ×${profileCount.value}` : 'Initialized'
);

const steps = computed(() => toTimelineSteps(props.member.initProgress, props.tickNow));
const showsStages = computed(
  () => (state.value === 'running' || state.value === 'failed') && kind.value.chips.length > 0
);
const failedStage = computed(() => failedStageLabel(props.member.initProgress, kind.value.chips));

const files = computed(() => sortSessionFiles((props.member.files ?? []).map(fromRecordingFile)));

const rootRef = ref<HTMLElement | null>(null);
const menuOpen = ref(false);

const onRowClick = () => {
  if (state.value === 'running') {
    return;
  }
  if (menuOpen.value) {
    menuOpen.value = false;
    return;
  }
  emit('click');
};

const runMenuAction = (action: 'initialize-again' | 'delete-recording') => {
  menuOpen.value = false;
  if (action === 'initialize-again') {
    emit('initialize-again');
  } else {
    emit('delete-recording');
  }
};

const closeMenuOnOutsideClick = (event: MouseEvent) => {
  const target = event.target as Node | null;
  if (menuOpen.value && rootRef.value && target && !rootRef.value.contains(target)) {
    menuOpen.value = false;
  }
};

onMounted(() => {
  document.addEventListener('mousedown', closeMenuOnOutsideClick);
});

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', closeMenuOnOutsideClick);
});
</script>

<template>
  <div ref="rootRef" class="bundle-member">
    <RecordingFileRow
      class="bundle-member__row"
      :class="{ 'bundle-member__row--busy': state === 'running' }"
      :filename="look.title"
      :file-type="look.fileType"
      :size-in-bytes="member.sizeInBytes"
      @click="onRowClick"
    >
      <template #before>
        <button
          v-if="look.grouped"
          class="recording-file-chevron"
          type="button"
          :aria-expanded="expanded"
          :aria-label="expanded ? 'Hide the files' : 'Show the files'"
          @click.stop="emit('toggle-expand')"
        >
          <i class="bi" :class="expanded ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
        </button>
        <span v-else class="recording-file-chevron" aria-hidden="true"></span>
      </template>

      <template #extra-badges>
        <span v-if="look.grouped" class="recording-file-size ms-2">
          <i class="bi bi-files me-1"></i>{{ look.fileCount }} files
        </span>
        <span v-if="member.durationInMillis > 0" class="recording-file-size ms-2">
          <i class="bi bi-stopwatch me-1"></i
          >{{ FormattingService.formatDurationInMillis2Units(member.durationInMillis) }}
        </span>
      </template>

      <template #actions>
        <div class="bundle-member__actions" @click.stop>
          <Badge
            v-if="state === 'running'"
            value="Initializing…"
            variant="primary"
            size="xs"
            icon="bi bi-hourglass-split"
            :uppercase="false"
          />
          <Badge
            v-else-if="state === 'built'"
            :value="initializedLabel"
            variant="success"
            size="xs"
            icon="bi bi-check-circle-fill"
            :uppercase="false"
          />
          <template v-else-if="state === 'failed'">
            <Badge
              value="Failed"
              variant="danger"
              size="xs"
              icon="bi bi-exclamation-octagon-fill"
              :uppercase="false"
            />
            <button
              class="btn btn-sm btn-outline-primary bundle-member__action-btn"
              type="button"
              title="Start initialization again"
              @click="emit('create-profile')"
            >
              <i class="bi bi-arrow-clockwise me-1"></i>Retry
            </button>
          </template>
          <button
            v-else
            class="btn btn-sm btn-outline-primary bundle-member__action-btn"
            type="button"
            title="Initialize this item into a profile"
            @click="emit('create-profile')"
          >
            <i class="bi bi-play-fill me-1"></i>Initialize
          </button>

          <button
            class="bundle-member__menu-btn"
            :class="{ 'bundle-member__menu-btn--active': menuOpen }"
            type="button"
            :disabled="state === 'running'"
            :title="menuOpen ? 'Close actions' : 'More actions'"
            :aria-label="menuOpen ? 'Close actions' : 'More actions'"
            :aria-expanded="menuOpen"
            @click="menuOpen = !menuOpen"
          >
            <i class="bi" :class="menuOpen ? 'bi-chevron-up' : 'bi-three-dots'"></i>
          </button>
        </div>
      </template>
    </RecordingFileRow>

    <StageChips
      v-if="showsStages"
      class="bundle-member__stages"
      :groups="kind.chips"
      :steps="steps"
      :tick-now="tickNow"
    />
    <InitFailedNote v-if="state === 'failed'" class="bundle-member__note" :stage="failedStage" />

    <div v-if="menuOpen" class="bundle-member__action-bar">
      <button
        v-if="state === 'built'"
        class="btn btn-sm btn-outline-secondary"
        type="button"
        @click="runMenuAction('initialize-again')"
      >
        <i class="bi bi-arrow-repeat me-1"></i>Initialize again
      </button>
      <button
        class="btn btn-sm btn-outline-danger"
        type="button"
        @click="runMenuAction('delete-recording')"
      >
        <i class="bi bi-trash me-1"></i>Delete recording
      </button>
    </div>

    <div v-if="look.grouped && expanded" class="recording-file-children">
      <RecordingFileList :files="files" @download="fileId => emit('download', fileId)" />
    </div>
  </div>
</template>

<style scoped>
.bundle-member {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-1);
}

.bundle-member__row {
  cursor: pointer;
}

.bundle-member__row--busy {
  cursor: progress;
}

.bundle-member__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  flex-shrink: 0;
  margin-left: var(--spacing-3);
  cursor: default;
}

.bundle-member__action-btn {
  padding: 0.2rem 0.6rem;
  font-size: 0.75rem;
}

.bundle-member__menu-btn {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: var(--radius-base);
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.bundle-member__menu-btn:hover:not(:disabled),
.bundle-member__menu-btn--active {
  background: var(--color-bg-hover-alt);
  color: var(--color-dark);
}

.bundle-member__menu-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.bundle-member__stages,
.bundle-member__note,
.bundle-member__action-bar {
  margin-left: var(--spacing-8);
}

.bundle-member__action-bar {
  display: flex;
  gap: var(--spacing-2);
}
</style>
