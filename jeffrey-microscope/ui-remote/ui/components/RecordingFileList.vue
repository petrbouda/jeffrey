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
  The files of an opened folder, one row each under its own name, each with its download button.
-->
<script setup lang="ts">
import RecordingFileRow from '@remote/components/RecordingFileRow.vue';
import type { SessionFile } from '@remote/services/sessionFiles';

withDefaults(
  defineProps<{
    files: SessionFile[];
    downloadOnly?: boolean;
  }>(),
  {
    downloadOnly: false
  }
);

const emit = defineEmits<{
  (e: 'download', fileId: string): void;
}>();
</script>

<template>
  <RecordingFileRow
    v-for="file in files"
    :key="file.id"
    :filename="file.name"
    :file-type="file.fileType"
    :size-in-bytes="file.size"
    :download-only="downloadOnly"
  >
    <template #actions>
      <button
        class="btn btn-sm btn-outline-secondary file-download-btn"
        type="button"
        title="Download file"
        :aria-label="`Download ${file.name}`"
        @click.stop="emit('download', file.id)"
      >
        <i class="bi bi-download"></i>
      </button>
    </template>
  </RecordingFileRow>
</template>

<style scoped>
.file-download-btn {
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  opacity: 0.6;
  transition: opacity 0.15s ease;
}

.file-download-btn:hover {
  opacity: 1;
}
</style>
