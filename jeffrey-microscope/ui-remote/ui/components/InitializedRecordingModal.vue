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
  Asked when a recording that already has profiles is clicked: open one of them, or initialize the
  recording again into another profile beside them. Initializing is heavy, so a click on a recording
  you have analysed before never starts it unasked.
-->
<script setup lang="ts">
import { computed } from 'vue';
import GenericModal from '@shared/components/GenericModal.vue';
import FormattingService from '@shared/services/FormattingService';
import type Recording from '../services/api/model/Recording';
import type { RecordingProfile } from '../services/api/model/Recording';

const props = defineProps<{
  recording: Recording;
  /** The recording's profiles that can be opened, newest first. */
  profiles: RecordingProfile[];
}>();

const emit = defineEmits<{
  (e: 'open', profile: RecordingProfile): void;
  (e: 'initialize-again'): void;
  (e: 'close'): void;
}>();

const newest = computed(() => props.profiles[0]);

const intro = computed(() =>
  props.profiles.length === 1
    ? 'already has a profile:'
    : `already has ${props.profiles.length} profiles:`
);
</script>

<template>
  <GenericModal
    modal-id="initializedRecordingModal"
    :show="true"
    title="Already initialized"
    icon="bi bi-check-circle-fill"
    size="md"
    modal-dialog-class="modal-dialog-centered"
    @update:show="emit('close')"
  >
    <template #default>
      <p class="irm-intro">
        <strong>{{ recording.profileName ?? recording.filename }}</strong>
        {{ intro }}
      </p>
      <ul class="irm-profiles">
        <li v-for="profile in profiles" :key="profile.profileId" class="irm-profile">
          <i class="bi bi-check-circle-fill irm-profile__icon"></i>
          <span class="irm-profile__name" :title="profile.profileName">{{
            profile.profileName
          }}</span>
          <span class="irm-profile__meta">
            {{ FormattingService.formatBytes(profile.sizeInBytes) }} ·
            {{ FormattingService.formatRelativeTime(profile.createdAt) }}
          </span>
          <button
            class="btn btn-sm btn-outline-primary"
            type="button"
            @click="emit('open', profile)"
          >
            Open
          </button>
        </li>
      </ul>
      <p class="irm-note">
        Initializing again builds another profile beside
        {{ profiles.length === 1 ? 'it' : 'them' }}; nothing is replaced.
      </p>
    </template>
    <template #footer>
      <button class="btn btn-secondary" type="button" @click="emit('close')">Cancel</button>
      <button class="btn btn-outline-primary" type="button" @click="emit('initialize-again')">
        <i class="bi bi-arrow-repeat me-1"></i>
        Initialize again
      </button>
      <button class="btn btn-primary" type="button" @click="emit('open', newest)">
        <i class="bi bi-box-arrow-up-right me-1"></i>
        Open newest
      </button>
    </template>
  </GenericModal>
</template>

<style scoped>
.irm-intro {
  margin: 0 0 var(--spacing-3);
  font-size: var(--font-size-base);
  color: var(--color-dark);
  overflow-wrap: anywhere;
}

.irm-profiles {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
}

.irm-profile {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
}

.irm-profile__icon {
  color: var(--color-success);
}

.irm-profile__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--color-dark);
}

.irm-profile__meta {
  flex: none;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.irm-note {
  margin: var(--spacing-3) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
</style>
