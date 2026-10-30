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
  Everything a recording can do, in one split button: the main part initializes it, opens its newest
  profile once built, or retries a failed run; the ▾ lists its profiles, Initialize again, the
  download of its files where the row has no folder to download them from, and Delete recording.
-->
<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import SplitButton from '@shared/components/SplitButton.vue';
import type Recording from '../services/api/model/Recording';
import { type MemberActionKind, memberAction } from './bundleMemberRow';
import { memberState } from './bundleSummary';
import { builtProfiles } from './recordingColumns';

const props = withDefaults(
  defineProps<{
    recording: Recording;
    /** Whether the recording's Analyze request is still in flight. */
    analyzing?: boolean;
    /** Lists a download of each of the recording's files in the menu. */
    downloads?: boolean;
    /** What the ▾ toggle is called for a screen reader. */
    menuLabel?: string;
  }>(),
  {
    analyzing: false,
    downloads: false,
    menuLabel: 'More actions'
  }
);

const emit = defineEmits<{
  (e: 'create-profile'): void;
  (e: 'open-profile', profileId: string): void;
  (e: 'initialize-again'): void;
  (e: 'delete-recording'): void;
  (e: 'download', fileId: string): void;
}>();

const state = computed(() => memberState(props.recording, props.analyzing));
const action = computed(() => memberAction(state.value));
const profiles = computed(() => builtProfiles(props.recording));
const files = computed(() => (props.downloads ? (props.recording.files ?? []) : []));

const openNewestProfile = () => {
  const newest = profiles.value[0];
  if (newest) {
    emit('open-profile', newest.profileId);
  }
};

const ACTION_HANDLERS: Record<MemberActionKind, () => void> = {
  initialize: () => emit('create-profile'),
  retry: () => emit('create-profile'),
  open: openNewestProfile,
  running: () => {}
};

const runAction = () => ACTION_HANDLERS[action.value.kind]();
</script>

<template>
  <SplitButton
    :label="action.label"
    :icon="action.icon"
    :tone="action.tone"
    :disabled="action.disabled"
    :menu-label="menuLabel"
    @click="runAction"
  >
    <template #default="{ close }">
      <template v-if="profiles.length > 0">
        <div class="split-button__label">Profiles</div>
        <button
          v-for="profile in profiles"
          :key="profile.profileId"
          class="split-button__item"
          type="button"
          role="menuitem"
          @click="
            close();
            emit('open-profile', profile.profileId);
          "
        >
          <i class="bi bi-box-arrow-up-right"></i>
          <span class="recording-actions__profile-name">{{ profile.profileName }}</span>
          <span class="recording-actions__profile-time">{{
            FormattingService.formatRelativeTime(profile.createdAt)
          }}</span>
        </button>
        <div class="split-button__divider"></div>
      </template>
      <button
        v-if="state === 'built'"
        class="split-button__item"
        type="button"
        role="menuitem"
        @click="
          close();
          emit('initialize-again');
        "
      >
        <i class="bi bi-arrow-repeat"></i>Initialize again
      </button>
      <button
        v-for="file in files"
        :key="file.id"
        class="split-button__item"
        type="button"
        role="menuitem"
        @click="
          close();
          emit('download', file.id);
        "
      >
        <i class="bi bi-download"></i
        >{{ files.length === 1 ? 'Download file' : `Download ${file.filename}` }}
      </button>
      <button
        class="split-button__item split-button__item--danger"
        type="button"
        role="menuitem"
        @click="
          close();
          emit('delete-recording');
        "
      >
        <i class="bi bi-trash"></i>Delete recording
      </button>
    </template>
  </SplitButton>
</template>

<style scoped>
.recording-actions__profile-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recording-actions__profile-time {
  flex-shrink: 0;
  color: var(--color-text-muted);
}
</style>
