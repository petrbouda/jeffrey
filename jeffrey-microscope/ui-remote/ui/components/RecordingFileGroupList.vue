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
  A recording's or a bundle's files, laid out by the same rules as a remote session's detail
  (sessionFiles.ts): a type with several files, and the logs always, fold into one row named after
  the type, opened with its chevron; a type with a single file is a row under the file's own name.
-->
<script setup lang="ts">
import { computed, ref } from 'vue';
import RecordingFileList from '@remote/components/RecordingFileList.vue';
import RecordingFileRow from '@remote/components/RecordingFileRow.vue';
import type RecordingFile from '@remote/services/api/model/RecordingFile';
import {
  type ArtifactTypeGroup,
  fileGroupPanels,
  fromRecordingFile,
  standaloneFiles
} from '@remote/services/sessionFiles';

const props = withDefaults(
  defineProps<{
    recordingId: string;
    files: RecordingFile[];
    /** Files Jeffrey never initializes, such as a bundle's logs and crash files. */
    downloadOnly?: boolean;
  }>(),
  {
    downloadOnly: false
  }
);

const emit = defineEmits<{
  (e: 'download', recordingId: string, fileId: string): void;
}>();

const sessionFiles = computed(() => props.files.map(fromRecordingFile));
const panels = computed(() => fileGroupPanels(sessionFiles.value));
const standalone = computed(() => standaloneFiles(sessionFiles.value));

const openPanels = ref<Set<ArtifactTypeGroup>>(new Set());

const togglePanel = (groupKey: ArtifactTypeGroup) => {
  const next = new Set(openPanels.value);
  if (next.has(groupKey)) {
    next.delete(groupKey);
  } else {
    next.add(groupKey);
  }
  openPanels.value = next;
};

const download = (fileId: string) => emit('download', props.recordingId, fileId);
</script>

<template>
  <div class="file-group-list">
    <div v-for="panel in panels" :key="panel.groupKey" class="file-group">
      <RecordingFileRow
        class="file-group__header"
        :filename="panel.display.name"
        :file-type="panel.display.fileType"
        :size-in-bytes="panel.totalSize"
        :download-only="downloadOnly"
        @click="togglePanel(panel.groupKey)"
      >
        <template #before>
          <button
            class="recording-file-chevron"
            type="button"
            :aria-expanded="openPanels.has(panel.groupKey)"
            :aria-label="`Show the ${panel.display.name}`"
            @click.stop="togglePanel(panel.groupKey)"
          >
            <i
              class="bi"
              :class="openPanels.has(panel.groupKey) ? 'bi-chevron-down' : 'bi-chevron-right'"
            ></i>
          </button>
        </template>
        <template #extra-badges>
          <span class="recording-file-size ms-2">
            <i class="bi bi-files me-1"></i>{{ panel.files.length }} file{{
              panel.files.length === 1 ? '' : 's'
            }}
          </span>
        </template>
      </RecordingFileRow>

      <div v-if="openPanels.has(panel.groupKey)" class="recording-file-children">
        <RecordingFileList
          :files="panel.files"
          :download-only="downloadOnly"
          @download="download"
        />
      </div>
    </div>

    <RecordingFileList
      :files="standalone"
      :download-only="downloadOnly"
      align-with-groups
      @download="download"
    />
  </div>
</template>

<style scoped>
.file-group-list,
.file-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
}

.file-group__header {
  cursor: pointer;
  user-select: none;
}
</style>
