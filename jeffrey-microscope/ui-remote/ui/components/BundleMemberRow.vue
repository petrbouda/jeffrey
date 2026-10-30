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
  own names, each with its download button. Everything the item can do sits in its split button: the
  main part initializes it, opens its profile once built, or retries a failed run; the ▾ lists its
  profiles, Initialize again and Delete recording.
-->
<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import SplitButton from '@shared/components/SplitButton.vue';
import StageChips from '@shared/components/StageChips.vue';
import type Recording from '../services/api/model/Recording';
import { fromRecordingFile, sortSessionFiles } from '../services/sessionFiles';
import InitFailedNote from './InitFailedNote.vue';
import RecordingFileList from './RecordingFileList.vue';
import RecordingFileRow from './RecordingFileRow.vue';
import { type MemberActionKind, memberAction, memberRowLook } from './bundleMemberRow';
import { memberState } from './bundleSummary';
import { failedStageLabel, toTimelineSteps } from './profileInitChips';
import { builtProfiles } from './recordingColumns';
import { recordingKindOf } from './recordingKinds';

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
const state = computed(() => memberState(props.member, props.analyzing));
const action = computed(() => memberAction(state.value));
const kind = computed(() => recordingKindOf(props.member.eventSource));
const profiles = computed(() => builtProfiles(props.member));

const steps = computed(() => toTimelineSteps(props.member.initProgress, props.tickNow));
const showsStages = computed(
  () => (state.value === 'running' || state.value === 'failed') && kind.value.chips.length > 0
);
const failedStage = computed(() => failedStageLabel(props.member.initProgress, kind.value.chips));

const files = computed(() => sortSessionFiles((props.member.files ?? []).map(fromRecordingFile)));

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
        <SplitButton
          class="bundle-member__action"
          :label="action.label"
          :icon="action.icon"
          :tone="action.tone"
          :disabled="action.disabled"
          :menu-label="`More actions for ${look.title}`"
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
                <span class="bundle-member__profile-name">{{ profile.profileName }}</span>
                <span class="bundle-member__profile-time">{{
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
    </RecordingFileRow>

    <StageChips
      v-if="showsStages"
      class="bundle-member__stages"
      :groups="kind.chips"
      :steps="steps"
      :tick-now="tickNow"
    />
    <InitFailedNote v-if="state === 'failed'" class="bundle-member__note" :stage="failedStage" />

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

.bundle-member__profile-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bundle-member__profile-time {
  flex-shrink: 0;
  color: var(--color-text-muted);
}

.bundle-member__stages,
.bundle-member__note {
  margin-left: var(--spacing-8);
}
</style>
