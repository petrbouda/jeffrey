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
  Where a downloaded recording or bundle came from: remote › workspace › project. Shared by the recording
  card and the bundle card, so a bundle and the recordings in it name their origin the same way.
-->
<script lang="ts">
export interface RecordingOrigin {
  remote: string;
  workspace: string;
  project: string;
}
</script>

<script setup lang="ts">
defineProps<{
  origin: RecordingOrigin;
}>();
</script>

<template>
  <span class="origin-chip" :title="`From ${origin.remote} › ${origin.workspace} › ${origin.project}`">
    <i class="bi bi-arrow-down-circle-fill"></i>
    <span class="origin-chip__part">{{ origin.remote }}</span>
    <span class="origin-chip__sep">›</span>
    <span class="origin-chip__part">{{ origin.workspace }}</span>
    <span class="origin-chip__sep">›</span>
    <span class="origin-chip__part origin-chip__part--project">{{ origin.project }}</span>
  </span>
</template>

<style scoped>
.origin-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  font-size: 0.7rem;
  color: var(--color-text-muted);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 2px 8px;
  font-weight: 500;
  max-width: 50%;
  overflow: hidden;
}

.origin-chip > i {
  color: var(--color-success-hover);
  font-size: 0.7rem;
}

.origin-chip__part {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.origin-chip__part--project {
  color: var(--color-dark);
  font-weight: 600;
}

.origin-chip__sep {
  color: var(--color-muted-separator);
  flex-shrink: 0;
}
</style>
