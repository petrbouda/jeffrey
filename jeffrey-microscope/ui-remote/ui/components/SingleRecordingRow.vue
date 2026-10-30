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
  A single recording on the Recordings page: a file uploaded on its own (a remote download is always
  a bundle). The same row a bundle's item has, but it opens nothing: it stands for one file, named
  after it, and its file is downloaded from the split button's menu, next to the recording's other
  actions.
-->
<script setup lang="ts">
import '@shared/styles/shared-components.css';
import { computed } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import type Recording from '../services/api/model/Recording';
import RecordingActions from './RecordingActions.vue';
import RecordingFileRow from './RecordingFileRow.vue';
import RecordingInitStatus from './RecordingInitStatus.vue';
import RecordingOriginChip, { type RecordingOrigin } from './RecordingOriginChip.vue';
import { recordingKindOf } from './recordingKinds';

const props = withDefaults(
  defineProps<{
    recording: Recording;
    /** Whether the recording's Analyze request is still in flight. */
    analyzing?: boolean;
    /** Epoch millis the parent ticks while an initialization is running. */
    tickNow?: number;
    origin?: RecordingOrigin;
    draggable?: boolean;
  }>(),
  {
    analyzing: false,
    tickNow: 0,
    origin: undefined,
    draggable: false
  }
);

const emit = defineEmits<{
  (e: 'create-profile'): void;
  (e: 'open-profile', profileId: string): void;
  (e: 'initialize-again'): void;
  (e: 'delete-recording'): void;
  (e: 'download', fileId: string): void;
  (e: 'dragend'): void;
}>();

const fileType = computed(
  () => props.recording.files?.[0]?.type ?? recordingKindOf(props.recording.eventSource).fileType
);

const onDragStart = (event: DragEvent) => {
  if (!props.draggable || !event.dataTransfer) {
    return;
  }
  event.dataTransfer.effectAllowed = 'move';
  event.dataTransfer.setData('text/plain', props.recording.id);
};
</script>

<template>
  <div class="single-recording">
    <RecordingFileRow
      :filename="recording.filename"
      :file-type="fileType"
      :size-in-bytes="recording.sizeInBytes"
    >
      <template #extra-badges>
        <span v-if="recording.durationInMillis > 0" class="recording-file-size ms-2">
          <i class="bi bi-stopwatch me-1"></i
          >{{ FormattingService.formatDurationInMillis2Units(recording.durationInMillis) }}
        </span>
        <span
          class="recording-file-size ms-2"
          :title="`Uploaded ${FormattingService.formatRelativeTime(recording.uploadedAt)}`"
        >
          <i class="bi bi-upload me-1"></i
          >{{ FormattingService.formatRelativeTime(recording.uploadedAt) }}
        </span>
        <RecordingOriginChip v-if="origin" class="ms-2" :origin="origin" />
      </template>

      <template #actions>
        <div class="single-recording__actions">
          <RecordingActions
            :recording="recording"
            :analyzing="analyzing"
            downloads
            :menu-label="`More actions for ${recording.filename}`"
            @create-profile="emit('create-profile')"
            @open-profile="profileId => emit('open-profile', profileId)"
            @initialize-again="emit('initialize-again')"
            @delete-recording="emit('delete-recording')"
            @download="fileId => emit('download', fileId)"
          />
          <div
            v-if="draggable"
            class="recording-drag-handle"
            draggable="true"
            title="Drag to move to another group"
            @dragstart="onDragStart"
            @dragend="emit('dragend')"
          >
            <i class="bi bi-grip-vertical"></i>
          </div>
        </div>
      </template>
    </RecordingFileRow>

    <RecordingInitStatus :recording="recording" :analyzing="analyzing" :tick-now="tickNow" />
  </div>
</template>

<style scoped>
.single-recording {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
}

.single-recording__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  margin-left: var(--spacing-3);
}
</style>
