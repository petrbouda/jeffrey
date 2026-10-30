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
  An item of an expanded bundle: the same folder row a remote session's detail shows, named after
  the item's file type. Clicking the row opens or closes the folder, listing the files under their
  own names, each with its download button. Everything the item can do sits in its split button
  (RecordingActions); a run's stages and a failure show under the row.
-->
<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import type Recording from '../services/api/model/Recording';
import { fromRecordingFile, sortSessionFiles } from '../services/sessionFiles';
import RecordingActions from './RecordingActions.vue';
import RecordingFileList from './RecordingFileList.vue';
import RecordingFileRow from './RecordingFileRow.vue';
import RecordingInitStatus from './RecordingInitStatus.vue';
import { memberRowLook } from './bundleMemberRow';

const props = withDefaults(
  defineProps<{
    member: Recording;
    /** Whether the item's Analyze request is still in flight. */
    analyzing?: boolean;
    /** Epoch millis the parent ticks while an initialization is running. */
    tickNow?: number;
    /** Whether the folder is open, listing the item's files. */
    expanded?: boolean;
  }>(),
  {
    analyzing: false,
    tickNow: 0,
    expanded: false
  }
);

const emit = defineEmits<{
  (e: 'create-profile'): void;
  (e: 'open-profile', profileId: string): void;
  (e: 'initialize-again'): void;
  (e: 'delete-recording'): void;
  (e: 'toggle-expand'): void;
  (e: 'download', fileId: string): void;
}>();

const look = computed(() => memberRowLook(props.member));
const files = computed(() => sortSessionFiles((props.member.files ?? []).map(fromRecordingFile)));

const toggleFolder = () => {
  if (look.value.fileCount > 0) {
    emit('toggle-expand');
  }
};
</script>

<template>
  <div class="bundle-member">
    <RecordingFileRow
      class="bundle-member__row"
      :filename="look.title"
      :file-type="look.fileType"
      :size-in-bytes="member.sizeInBytes"
      @click="toggleFolder"
    >
      <template #before>
        <button
          class="recording-file-chevron"
          type="button"
          :disabled="look.fileCount === 0"
          :aria-expanded="expanded"
          :aria-label="expanded ? `Close ${look.title}` : `Open ${look.title}`"
          @click.stop="toggleFolder"
        >
          <i class="bi" :class="expanded ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
        </button>
      </template>

      <template #extra-badges>
        <span class="recording-file-size ms-2">
          <i class="bi bi-files me-1"></i>{{ look.fileCount }} file{{
            look.fileCount === 1 ? '' : 's'
          }}
        </span>
        <span v-if="member.durationInMillis > 0" class="recording-file-size ms-2">
          <i class="bi bi-stopwatch me-1"></i
          >{{ FormattingService.formatDurationInMillis2Units(member.durationInMillis) }}
        </span>
      </template>

      <template #actions>
        <RecordingActions
          class="bundle-member__action"
          :recording="member"
          :analyzing="analyzing"
          :menu-label="`More actions for ${look.title}`"
          @create-profile="emit('create-profile')"
          @open-profile="profileId => emit('open-profile', profileId)"
          @initialize-again="emit('initialize-again')"
          @delete-recording="emit('delete-recording')"
        />
      </template>
    </RecordingFileRow>

    <RecordingInitStatus :recording="member" :analyzing="analyzing" :tick-now="tickNow" />

    <div v-if="expanded && files.length > 0" class="recording-file-children">
      <RecordingFileList :files="files" @download="fileId => emit('download', fileId)" />
    </div>
  </div>
</template>

<style scoped>
.bundle-member {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
}

.bundle-member__row {
  cursor: pointer;
  user-select: none;
}

.bundle-member__action {
  margin-left: var(--spacing-3);
}
</style>
