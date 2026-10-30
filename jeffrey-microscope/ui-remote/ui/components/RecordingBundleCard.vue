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
  A bundle: the items a remote session download brought in together, as one row of the recordings list.
  Collapsed, it names the bundle and shows a badge per item with its state; expanded, it lists its items
  and then its download-only files (logs, crash files) as the same file rows a remote session's detail
  shows, grouped by the same rules. An item carries its state on the right; a download-only row dashes
  its border. Each profile built from an item is a row of its own in the Profiles column, outside the
  bundle.
-->
<script setup lang="ts">
import '@shared/styles/shared-components.css';
import { computed } from 'vue';
import Badge from '@shared/components/Badge.vue';
import FormattingService from '@shared/services/FormattingService';
import type Bundle from '../services/api/model/Bundle';
import type Recording from '../services/api/model/Recording';
import BundleMemberRow from './BundleMemberRow.vue';
import RecordingFileGroupList from './RecordingFileGroupList.vue';
import RecordingOriginChip, { type RecordingOrigin } from './RecordingOriginChip.vue';
import { BUNDLE_DRAG_PREFIX, bundleItemsLabel, bundleSize, memberBadges } from './bundleSummary';

const props = withDefaults(
  defineProps<{
    bundle: Bundle;
    /** The bundle's items, its recording first: the badges, size and counts describe all of them. */
    members: Recording[];
    /** The items listed when expanded; all of them when not given. */
    listedMembers?: Recording[];
    origin?: RecordingOrigin;
    tickNow?: number;
    expanded?: boolean;
    /** Items whose own files are listed. */
    expandedMembers?: Set<string>;
    /** Items whose Analyze request is in flight. */
    analyzingMembers?: Set<string>;
    draggable?: boolean;
  }>(),
  {
    listedMembers: undefined,
    origin: undefined,
    tickNow: 0,
    expanded: false,
    expandedMembers: () => new Set(),
    analyzingMembers: () => new Set(),
    draggable: false
  }
);

const emit = defineEmits<{
  (e: 'toggle-expand'): void;
  (e: 'delete-bundle'): void;
  (e: 'download-file', fileId: string): void;
  (e: 'download-member-file', recordingId: string, fileId: string): void;
  (e: 'member-open-profile', recording: Recording, profileId: string): void;
  (e: 'member-create-profile', recording: Recording): void;
  (e: 'member-initialize-again', recording: Recording): void;
  (e: 'member-delete-recording', recording: Recording): void;
  (e: 'member-toggle-expand', recording: Recording): void;
  (e: 'dragend'): void;
}>();

const memberBadgeList = computed(() => memberBadges(props.members, props.analyzingMembers));

const totalBytes = computed(() => bundleSize(props.bundle, props.members));

const itemsLabel = computed(() => bundleItemsLabel(props.bundle, props.members));

const listed = computed(() => props.listedMembers ?? props.members);

const onDragStart = (event: DragEvent) => {
  if (!props.draggable || !event.dataTransfer) {
    return;
  }
  event.dataTransfer.effectAllowed = 'move';
  event.dataTransfer.setData('text/plain', BUNDLE_DRAG_PREFIX + props.bundle.id);
};
</script>

<template>
  <div class="bundle-card" :class="{ 'bundle-card--expanded': expanded }">
    <div class="bundle-card__header">
      <button
        class="bundle-card__toggle"
        type="button"
        :aria-expanded="expanded"
        :title="expanded ? 'Collapse bundle' : 'Show the items of this bundle'"
        @click="emit('toggle-expand')"
      >
        <i class="bi" :class="expanded ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
      </button>
      <i class="bi bi-box-seam bundle-card__icon"></i>
      <div class="bundle-card__info" @click="emit('toggle-expand')">
        <div class="bundle-card__line1">
          <Badge value="Bundle" variant="grey" size="xxs" :uppercase="false" />
          <span class="bundle-card__name">{{ bundle.name }}</span>
          <RecordingOriginChip v-if="origin" :origin="origin" />
        </div>
        <div class="bundle-card__line2">
          <span>{{ itemsLabel }}</span>
          <span class="bundle-card__sep">&middot;</span>
          <span>{{ FormattingService.formatBytes(totalBytes) }}</span>
          <span class="bundle-card__sep">&middot;</span>
          <span>{{ FormattingService.formatRelativeTime(bundle.createdAt) }}</span>
          <span class="bundle-card__kinds">
            <span
              v-for="badge in memberBadgeList"
              :key="badge.id"
              class="bundle-card__kind"
              :title="badge.title"
            >
              <span class="bundle-card__dot" :class="`bundle-card__dot--${badge.state}`"></span>
              <Badge :value="badge.label" :variant="badge.variant" size="xxs" :uppercase="false" />
            </span>
          </span>
        </div>
      </div>
      <div class="bundle-card__actions">
        <button
          class="bundle-card__delete"
          type="button"
          title="Delete the bundle with every item, profile and file in it"
          @click.stop="emit('delete-bundle')"
        >
          <i class="bi bi-trash"></i>
        </button>
        <div
          v-if="draggable"
          class="recording-drag-handle"
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

    <div v-if="expanded" class="bundle-card__body">
      <BundleMemberRow
        v-for="member in listed"
        :key="member.id"
        :member="member"
        :analyzing="analyzingMembers.has(member.id)"
        :tick-now="tickNow"
        :expanded="expandedMembers.has(member.id)"
        @open-profile="profileId => emit('member-open-profile', member, profileId)"
        @create-profile="emit('member-create-profile', member)"
        @initialize-again="emit('member-initialize-again', member)"
        @delete-recording="emit('member-delete-recording', member)"
        @toggle-expand="emit('member-toggle-expand', member)"
        @download="fileId => emit('download-member-file', member.id, fileId)"
      />

      <RecordingFileGroupList
        v-if="bundle.files.length > 0"
        :recording-id="bundle.id"
        :files="bundle.files"
        download-only
        @download="(_ownerId, fileId) => emit('download-file', fileId)"
      />
    </div>
  </div>
</template>

<style scoped>
.bundle-card {
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-text-muted);
  border-radius: var(--radius-md);
  background: var(--color-white);
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease;
}

.bundle-card:hover {
  border-color: var(--color-primary-border);
  border-left-color: var(--color-text-muted);
}

.bundle-card--expanded {
  border-color: var(--color-primary-border-light);
}

.bundle-card__header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px 10px 8px;
}

.bundle-card__toggle {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}

.bundle-card__toggle:hover {
  background: var(--color-lighter);
  color: var(--color-dark);
}

.bundle-card__icon {
  font-size: 1rem;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.bundle-card__info {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.bundle-card__line1 {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.bundle-card__name {
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
  font-size: 0.88rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.bundle-card__line2 {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.bundle-card__sep {
  color: var(--color-muted-separator);
}

.bundle-card__kinds {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-left: 4px;
}

.bundle-card__kind {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.bundle-card__dot {
  width: 7px;
  height: 7px;
  border-radius: var(--radius-circle);
  background: var(--color-border);
}

.bundle-card__dot--built {
  background: var(--color-success);
}

.bundle-card__dot--running {
  background: var(--color-primary);
}

.bundle-card__dot--failed {
  background: var(--color-danger);
}

.bundle-card__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.bundle-card__delete {
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
  font-size: 1rem;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.bundle-card__delete:hover {
  color: var(--color-danger-hover);
  border-color: var(--color-danger-border-light);
  background: var(--color-danger-light);
}

.bundle-card__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 14px 12px 42px;
}
</style>
