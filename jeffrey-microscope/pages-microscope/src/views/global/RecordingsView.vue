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

<template>
  <div>
    <!-- 1. Upload zone (format pills + auto-uploading files in the same panel) -->
    <div class="upload-section">
      <div
        class="drop-zone"
        :class="{ 'drag-over': dragActive }"
        @dragenter.prevent="onDragEnter"
        @dragover.prevent
        @dragleave.prevent="onDragLeave"
        @drop.prevent="handleDrop"
      >
        <input
          ref="fileInputRef"
          type="file"
          :accept="FILE_INPUT_ACCEPT"
          multiple
          class="file-input-hidden"
          @change="handleFileInput"
        />

        <div class="drop-target-group">
          <i class="bi bi-folder2"></i>
          {{ dropHintPrefix }} <strong>{{ uploadTargetLabel }}</strong>
          <template v-if="uploadStatusLine"> · {{ uploadStatusLine }}</template>
        </div>

        <!-- Centred upload target: the rings ripple out while a file is held over the zone -->
        <button
          class="drop-target"
          type="button"
          :title="browseButtonLabel"
          @click="triggerFileInput"
        >
          <span class="drop-ring"></span>
          <span class="drop-ring drop-ring-outer"></span>
          <span class="drop-core">
            <i class="bi bi-upload"></i>
          </span>
        </button>

        <div class="drop-title">{{ dropTitle }}</div>
        <div class="drop-hint">
          Drag files here, or
          <button class="drop-browse" type="button" @click="triggerFileInput">
            {{ browseLinkLabel }}
          </button>
        </div>

        <!-- One pill per parser, in the colour that format wears everywhere else -->
        <div class="drop-formats">
          <span
            v-for="format in RECORDING_FORMATS"
            :key="format.id"
            class="format-pill"
            :class="'format-' + format.id"
          >
            <span class="format-ic">
              <i :class="format.icon"></i>
            </span>
            {{ format.label }}
            <code class="format-ext">{{ format.extensions.join(' ') }}</code>
          </span>
        </div>

        <div v-if="uploadFiles.length > 0" class="drop-files">
          <div v-for="(file, index) in uploadFiles" :key="file.name + index" class="drop-file">
            <span class="drop-file-dot" :class="'dot-' + formatIdForFile(file.name)"></span>
            <span class="drop-file-name">{{ file.name }}</span>
            <span class="drop-file-size">{{ FormattingService.formatBytes(file.size) }}</span>
            <template v-if="uploadProgress[file.name]">
              <div class="progress-track">
                <div
                  class="progress-fill"
                  :class="uploadProgress[file.name].status"
                  :style="{ width: Math.min(uploadProgress[file.name].progress, 100) + '%' }"
                ></div>
              </div>
              <span class="progress-status" :class="'status-' + uploadProgress[file.name].status">
                <i
                  v-if="uploadProgress[file.name].status === 'complete'"
                  class="bi bi-check-circle"
                ></i>
                <i
                  v-else-if="uploadProgress[file.name].status === 'error'"
                  class="bi bi-exclamation-circle"
                ></i>
                <template v-else-if="uploadProgress[file.name].status === 'uploading'">
                  {{ uploadProgress[file.name].progress }}%
                </template>
                <template v-else>Pending</template>
              </span>
              <button
                v-if="uploadProgress[file.name].status === 'error'"
                class="drop-file-remove"
                type="button"
                title="Dismiss"
                @click="removeFile(index)"
              >
                <i class="bi bi-x"></i>
              </button>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- Error -->
    <div v-if="errorMessage" class="upload-error">
      <i class="bi bi-exclamation-triangle-fill"></i>
      {{ errorMessage }}
    </div>

    <!-- 2. Recordings panel -->
    <MainCard>
      <!-- Group selector bar -->
      <div class="group-bar">
        <span class="group-bar-label">Group</span>
        <button
          class="group-chip group-chip-all"
          :class="{ active: viewFilter === 'all' }"
          type="button"
          @click="selectFilter('all', null)"
        >
          All
          <span class="chip-count">{{ listedCount }}</span>
        </button>
        <button
          v-if="ungroupedCount > 0"
          class="group-chip group-chip-ungrouped"
          :class="{
            active: viewFilter === '__ungrouped__',
            'drop-target': dragOverGroupKey === '__ungrouped__'
          }"
          type="button"
          @click="selectFilter('__ungrouped__', null)"
          @dragover="onChipDragOver($event, '__ungrouped__')"
          @dragleave="onChipDragLeave($event, '__ungrouped__')"
          @drop="onChipDrop($event, null)"
        >
          <i class="bi bi-collection"></i>
          Ungrouped
          <span class="chip-count">{{ ungroupedCount }}</span>
        </button>
        <button
          v-for="group in sortedGroups"
          :key="group.id"
          class="group-chip group-chip-custom"
          :class="{
            active: viewFilter === group.id,
            'drop-target': dragOverGroupKey === group.id
          }"
          type="button"
          @click="selectFilter(group.id, group.id)"
          @dragover="onChipDragOver($event, group.id)"
          @dragleave="onChipDragLeave($event, group.id)"
          @drop="onChipDrop($event, group.id)"
        >
          <i class="bi bi-folder"></i>
          {{ group.name }}
          <span class="chip-count">{{ getGroupCount(group.id) }}</span>
          <span class="chip-delete" title="Delete group" @click.stop="confirmDeleteGroup(group.id)">
            <i class="bi bi-trash"></i>
          </span>
        </button>
        <div class="page-search group-bar-search">
          <i class="bi bi-search"></i>
          <input v-model="searchText" type="text" placeholder="Search..." />
        </div>
        <button class="new-group-btn" type="button" @click="showCreateGroupModal = true">
          <i class="bi bi-plus-lg"></i>
          New Group
        </button>
      </div>

      <!-- Type filter + unified recordings list -->
      <!-- Type filter pills -->
      <div class="type-filter">
        <button
          class="type-chip type-chip--all"
          :class="{ active: typeFilter === 'all' }"
          type="button"
          @click="selectTypeFilter('all')"
        >
          <span class="type-ic">
            <i class="bi bi-grid-fill"></i>
          </span>
          All
          <span class="type-count">{{ selectionCount }}</span>
        </button>
        <button
          v-for="tf in TYPE_FILTERS"
          :key="tf.key"
          class="type-chip"
          :class="[`type-chip--${tf.format}`, { active: typeFilter === tf.key }]"
          type="button"
          @click="selectTypeFilter(tf.key)"
        >
          <span class="type-ic">
            <i :class="FORMAT_ICON_BY_ID.get(tf.format)"></i>
          </span>
          {{ tf.label }}
          <span class="type-count">{{ typeCounts[tf.key] }}</span>
        </button>
      </div>

      <EmptyState
        v-if="searchText && selectionCount === 0"
        icon="bi-search"
        title="No recordings match the current filter"
      />
      <div v-else class="recordings-split">
        <!-- Left: every recording, initialized or not; a recording never leaves this column. -->
        <section class="rec-col rec-col--recordings">
          <div class="rec-col__head">
            <span class="rec-col__dot rec-col__dot--recordings"></span>
            Recordings
            <span class="rec-col__count">{{ columns.recordings.length }}</span>
          </div>
          <div v-if="columns.recordings.length > 0" class="card-stack recordings-list">
            <template v-for="item in columns.recordings" :key="item.key">
              <RecordingBundleCard
                v-if="item.kind === 'bundle'"
                :bundle="item.bundle"
                :members="item.members"
                :listed-members="item.listed"
                :origin="buildOrigin(item.bundle.tags)"
                :tick-now="tickNow"
                :expanded="expandedBundles.has(item.bundle.id)"
                :expanded-members="expandedRecordings"
                :analyzing-members="analyzingRecordings"
                :draggable="true"
                @toggle-expand="toggleBundle(item.bundle.id)"
                @delete-bundle="deletingBundle = item.bundle"
                @download-file="fileId => downloadBundleFile(item.bundle.id, fileId)"
                @download-member-file="downloadFile"
                @member-click="handleRecordingClick"
                @member-create-profile="member => analyzeRecording(member.id)"
                @member-initialize-again="member => initializeAgain(member.id)"
                @member-delete-recording="member => deleteRecording(member.id)"
                @member-toggle-expand="member => toggleRecordingFiles(member.id)"
                @dragend="onDragEnd"
              />
              <RecordingCard
                v-else
                role="recording"
                :recording-id="item.recording.id"
                :name="item.recording.filename"
                :size-in-bytes="item.recording.sizeInBytes"
                :duration-in-millis="item.recording.durationInMillis"
                :uploaded-at="item.recording.uploadedAt"
                :source-type="item.recording.eventSource"
                :profile-id="item.recording.profileId"
                :profile-count="builtProfiles(item.recording).length"
                :analyzing="analyzingRecordings.has(item.recording.id)"
                :init-progress="item.recording.initProgress"
                :tick-now="tickNow"
                :draggable="true"
                :origin="buildOrigin(item.recording.tags)"
                :file-count="item.recording.files?.length ?? 0"
                :expandable="(item.recording.files?.length ?? 0) > 1"
                :expanded="expandedRecordings.has(item.recording.id)"
                @click="handleRecordingClick(item.recording)"
                @create-profile="analyzeRecording(item.recording.id)"
                @initialize-again="initializeAgain(item.recording.id)"
                @delete-recording="deleteRecording(item.recording.id)"
                @toggle-expand="toggleRecordingFiles(item.recording.id)"
                @dragend="onDragEnd"
              >
                <template #expanded-content>
                  <RecordingFileGroupList
                    v-if="item.recording.files && item.recording.files.length > 0"
                    :recording-id="item.recording.id"
                    :files="item.recording.files"
                    @download="downloadFile"
                  />
                  <div v-else class="small py-1 text-muted">
                    <i class="bi bi-exclamation-circle me-1"></i>
                    No recording files available
                  </div>
                </template>
              </RecordingCard>
            </template>
          </div>
          <div v-else class="rec-col__empty">
            <i class="bi bi-inbox"></i>
            <span>No recordings yet — drop a file above</span>
          </div>
        </section>

        <!-- Right: one row per profile, i.e. per initialization of a recording. -->
        <section class="rec-col rec-col--profiles">
          <div class="rec-col__head">
            <span class="rec-col__dot rec-col__dot--profiles"></span>
            Profiles
            <span class="rec-col__count">{{ columns.profiles.length }}</span>
          </div>
          <div v-if="columns.profiles.length > 0" class="card-stack recordings-list">
            <RecordingCard
              v-for="item in columns.profiles"
              :key="item.key"
              role="profile"
              :recording-id="item.recording.id"
              :name="item.profile.profileName"
              :size-in-bytes="item.recording.sizeInBytes"
              :duration-in-millis="item.recording.durationInMillis"
              :uploaded-at="item.recording.uploadedAt"
              :source-type="item.recording.eventSource"
              :profile-id="item.profile.profileId"
              :profile-size-in-bytes="item.profile.sizeInBytes"
              :profile-created-at="item.profile.createdAt"
              :profile-modified="item.profile.modified"
              :init-progress="item.profile.initProgress"
              :tick-now="tickNow"
              :origin="buildOrigin(item.recording.tags)"
              @click="openProfile(item.profile.profileId, item.recording.eventSource)"
              @edit-profile="startEditProfile(item)"
              @delete-profile="deleteProfile(item)"
              @reinitialize="reinitializeHeapDump(item.profile.profileId)"
            />
          </div>
          <div v-else class="rec-col__empty">
            <i class="bi bi-hourglass-split"></i>
            <span>No profiles yet — click a recording on the left to initialize it</span>
          </div>
        </section>
      </div>
    </MainCard>

    <!-- Create Group modal -->
    <EditNameModal
      v-if="showCreateGroupModal"
      v-model="newGroupName"
      title="New Group"
      placeholder="Enter group name"
      submit-label="Create"
      @submit="createGroup"
      @close="showCreateGroupModal = false"
    />

    <!-- Edit Profile modal -->
    <EditNameModal
      v-if="editingProfile"
      v-model="editProfileName"
      @submit="updateProfileName"
      @close="editingProfile = null"
    />

    <!-- A recording that already has profiles: open one, or initialize it again -->
    <InitializedRecordingModal
      v-if="confirmingRecording"
      :recording="confirmingRecording"
      :profiles="builtProfiles(confirmingRecording)"
      @open="openConfirmedProfile"
      @initialize-again="initializeConfirmedAgain"
      @close="confirmingRecording = null"
    />

    <!-- Delete Bundle confirmation -->
    <ConfirmationDialog
      :show="!!deletingBundle"
      title="Delete Bundle"
      :message="`This will delete the bundle ${deletingBundle?.name ?? ''} with every item in it, their profiles and its files. This action cannot be undone.`"
      confirm-label="Delete"
      confirm-button-class="btn-danger"
      @confirm="deleteBundle"
      @cancel="deletingBundle = null"
      @update:show="deletingBundle = null"
    />

    <!-- Delete Group confirmation -->
    <ConfirmationDialog
      :show="!!deletingGroupId"
      title="Delete Group"
      message="This will delete the group and all its recordings (including created profiles). This action cannot be undone."
      confirm-label="Delete"
      confirm-button-class="btn-danger"
      @confirm="deleteGroup"
      @cancel="deletingGroupId = null"
      @update:show="deletingGroupId = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import RecordingsClient from '@remote/services/api/RecordingsClient';
import MainCard from '@shared/components/MainCard.vue';
import RecordingCard from '@remote/components/RecordingCard.vue';
import RecordingBundleCard from '@remote/components/RecordingBundleCard.vue';
import EditNameModal from '@/components/EditNameModal.vue';
import ConfirmationDialog from '@shared/components/ConfirmationDialog.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import RecordingFileGroupList from '@remote/components/RecordingFileGroupList.vue';
import FormattingService from '@shared/services/FormattingService';
import ToastService from '@shared/services/ToastService';
import type RecordingGroup from '@remote/services/api/model/RecordingGroup';
import type Recording from '@remote/services/api/model/Recording';
import type { RecordingProfile, RecordingTag } from '@remote/services/api/model/Recording';
import type { RecordingOrigin } from '@remote/components/RecordingOriginChip.vue';
import type Bundle from '@remote/services/api/model/Bundle';
import { recordingKindOf, type RecordingKindKey } from '@remote/components/recordingKinds';
import { BUNDLE_DRAG_PREFIX, compareMembers } from '@remote/components/bundleSummary';
import InitializedRecordingModal from '@remote/components/InitializedRecordingModal.vue';
import {
  builtProfiles,
  splitColumns,
  type BundleWithMembers,
  type ProfileItem,
  type RecordingColumns
} from '@remote/components/recordingColumns';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import { profileLandingRoute } from '@/services/ProfileLandingRoute';

const UNGROUPED_KEY = '__ungrouped__';

/** One recording format Jeffrey can parse: a pill in the drop zone and a colour on every file row. */
interface RecordingFormat {
  id: string;
  label: string;
  icon: string;
  extensions: string[];
}

const RECORDING_FORMATS: RecordingFormat[] = [
  {
    id: 'jfr',
    label: 'JFR',
    icon: 'bi bi-activity',
    extensions: ['.jfr', '.jfr.lz4']
  },
  {
    id: 'pprof',
    label: 'pprof',
    icon: 'bi bi-bar-chart-fill',
    extensions: ['.pprof', '.pb.gz']
  },
  {
    id: 'otlp',
    label: 'OpenTelemetry',
    icon: 'bi bi-broadcast',
    extensions: ['.otlp']
  },
  {
    id: 'heap',
    label: 'Heap dump',
    icon: 'bi bi-pie-chart-fill',
    extensions: ['.hprof', '.hprof.gz']
  },
  {
    id: 'perf',
    label: 'Perf counters',
    icon: 'bi bi-speedometer2',
    extensions: ['.hsperfdata']
  }
];

const ALLOWED_FILE_SUFFIXES = RECORDING_FORMATS.flatMap(format => format.extensions);

/**
 * The file picker matches on the last dot segment only, so `.jfr.lz4` has to be offered
 * as `.lz4`. Derived from the same list the drop zone validates against.
 */
const FILE_INPUT_ACCEPT = [
  ...new Set(ALLOWED_FILE_SUFFIXES.map(suffix => suffix.slice(suffix.lastIndexOf('.'))))
].join(',');

/** Dot colour for a file that survived validation but matches no format — defensive only. */
const UNKNOWN_FORMAT_ID = 'unknown';

/** `DataTransfer.types` entry that marks a drag carrying files rather than a recording card. */
const DRAG_TYPE_FILES = 'Files';

type ViewFilter = 'all' | typeof UNGROUPED_KEY | string;

interface UploadProgressEntry {
  progress: number;
  status: 'pending' | 'uploading' | 'complete' | 'error';
}

const router = useRouter();
const recordingsClient = new RecordingsClient();

// State
const uploadFiles = ref<File[]>([]);
const uploadProgress = ref<Record<string, UploadProgressEntry>>({});
const errorMessage = ref<string | null>(null);
const autoRemoveTimers = new Map<string, ReturnType<typeof setTimeout>>();
const AUTO_REMOVE_DELAY_MS = 5000;

const scheduleAutoRemove = (filename: string) => {
  const existing = autoRemoveTimers.get(filename);
  if (existing !== undefined) {
    clearTimeout(existing);
  }
  const handle = setTimeout(() => {
    autoRemoveTimers.delete(filename);
    const idx = uploadFiles.value.findIndex(f => f.name === filename);
    if (idx !== -1) {
      uploadFiles.value.splice(idx, 1);
    }
    if (uploadProgress.value[filename] !== undefined) {
      const next = { ...uploadProgress.value };
      delete next[filename];
      uploadProgress.value = next;
    }
  }, AUTO_REMOVE_DELAY_MS);
  autoRemoveTimers.set(filename, handle);
};

const cancelAutoRemove = (filename: string) => {
  const existing = autoRemoveTimers.get(filename);
  if (existing !== undefined) {
    clearTimeout(existing);
    autoRemoveTimers.delete(filename);
  }
};
const allRecordings = ref<Recording[]>([]);
const allBundles = ref<Bundle[]>([]);
const allGroups = ref<RecordingGroup[]>([]);

// Filter + upload target
const viewFilter = ref<ViewFilter>('all');
const selectedGroupId = ref<string | null>(null);

const searchText = ref('');
const analyzingRecordings = ref<Set<string>>(new Set());
const expandedRecordings = ref<Set<string>>(new Set());
const expandedBundles = ref<Set<string>>(new Set());
const deletingBundle = ref<Bundle | null>(null);

// Drop zone. dragDepth counts enter/leave pairs, because the pills and buttons inside the zone
// each fire dragleave as the cursor crosses them and a plain flag would flicker.
const dragActive = ref(false);
const dragDepth = ref(0);
const dragFileCount = ref(0);
const fileInputRef = ref<HTMLInputElement | null>(null);

// Drag and drop (recording → chip)
const dragOverGroupKey = ref<string | null>(null);

// Group creation
const showCreateGroupModal = ref(false);
const newGroupName = ref('');
// Group deletion
const deletingGroupId = ref<string | null>(null);
// Profile editing
const editingProfile = ref<ProfileItem | null>(null);
const editProfileName = ref('');
// A recording with profiles that was clicked: the dialog asks whether to open one or build another
const confirmingRecording = ref<Recording | null>(null);

watch(showCreateGroupModal, val => {
  if (val) {
    newGroupName.value = '';
  }
});

// --- Computeds ---

// A bundle's items sit in no group of their own: the bundle is what a group holds.
const standaloneRecordings = computed<Recording[]>(() =>
  allRecordings.value.filter(recording => !recording.bundleId)
);

/** Each bundle's items, its recording first, then its perf counters and heap dumps. */
const membersByBundle = computed<Map<string, Recording[]>>(() => {
  const map = new Map<string, Recording[]>();
  for (const recording of allRecordings.value) {
    if (recording.bundleId) {
      const members = map.get(recording.bundleId) ?? [];
      members.push(recording);
      map.set(recording.bundleId, members);
    }
  }
  for (const members of map.values()) {
    members.sort(compareMembers);
  }
  return map;
});

const listedCount = computed(() => standaloneRecordings.value.length + allBundles.value.length);

// Entries per group: a standalone recording or a bundle counts once.
const groupCountsMap = computed<Map<string | null, number>>(() => {
  const map = new Map<string | null, number>();
  const groupIds = [
    ...standaloneRecordings.value.map(recording => recording.groupId),
    ...allBundles.value.map(bundle => bundle.groupId)
  ];
  for (const groupId of groupIds) {
    map.set(groupId, (map.get(groupId) ?? 0) + 1);
  }
  return map;
});

const ungroupedCount = computed(() => groupCountsMap.value.get(null) ?? 0);

const sortedGroups = computed<RecordingGroup[]>(() => {
  const newestUpload = (groupId: string): number => {
    let max = 0;
    for (const r of standaloneRecordings.value) {
      if (r.groupId === groupId && r.uploadedAt > max) {
        max = r.uploadedAt;
      }
    }
    for (const bundle of allBundles.value) {
      if (bundle.groupId === groupId && bundle.createdAt > max) {
        max = bundle.createdAt;
      }
    }
    return max;
  };
  return [...allGroups.value].sort((a, b) => {
    const diff = newestUpload(b.id) - newestUpload(a.id);
    if (diff !== 0) {
      return diff;
    }
    return a.name.localeCompare(b.name);
  });
});

const groupNameById = computed<Map<string, string>>(() => {
  const map = new Map<string, string>();
  for (const group of allGroups.value) {
    map.set(group.id, group.name);
  }
  return map;
});

const uploadTargetLabel = computed(() => {
  if (selectedGroupId.value === null) {
    return 'No group';
  }
  return groupNameById.value.get(selectedGroupId.value) ?? 'No group';
});

interface UploadCounts {
  complete: number;
  failed: number;
  active: number;
  total: number;
}

const uploadCounts = computed<UploadCounts>(() => {
  const counts: UploadCounts = {
    complete: 0,
    failed: 0,
    active: 0,
    total: uploadFiles.value.length
  };
  for (const file of uploadFiles.value) {
    const entry = uploadProgress.value[file.name];
    if (entry === undefined) {
      continue;
    }
    if (entry.status === 'complete') {
      counts.complete++;
    } else if (entry.status === 'error') {
      counts.failed++;
    } else {
      counts.active++;
    }
  }
  return counts;
});

const dropTitle = computed<string>(() => {
  if (dragActive.value) {
    if (dragFileCount.value === 1) {
      return 'Release to upload 1 file';
    }
    if (dragFileCount.value > 1) {
      return `Release to upload ${dragFileCount.value} files`;
    }
    return 'Release to upload';
  }
  const counts = uploadCounts.value;
  if (counts.active > 0) {
    return counts.total === 1 ? 'Uploading 1 file' : `Uploading ${counts.total} files`;
  }
  if (counts.failed > 0) {
    return 'Some uploads failed';
  }
  if (counts.total > 0) {
    return counts.total === 1 ? 'Upload complete' : 'Uploads complete';
  }
  return 'Drop recordings to analyze';
});

const dropHintPrefix = computed<string>(() => {
  if (dragActive.value || uploadFiles.value.length > 0) {
    return 'Into';
  }
  return 'Uploads into';
});

const uploadStatusLine = computed<string>(() => {
  const { complete, failed } = uploadCounts.value;
  const parts: string[] = [];
  if (complete > 0) {
    parts.push(`${complete} done`);
  }
  if (failed > 0) {
    parts.push(`${failed} failed`);
  }
  return parts.join(' · ');
});

const browseButtonLabel = computed<string>(() => {
  return uploadFiles.value.length > 0 ? 'Add more files' : 'Browse files';
});

const browseLinkLabel = computed<string>(() => {
  return uploadFiles.value.length > 0 ? 'add more from your computer' : 'browse your computer';
});

const inSelectedGroup = (groupId: string | null): boolean => {
  if (viewFilter.value === UNGROUPED_KEY) {
    return groupId === null;
  }
  return viewFilter.value === 'all' || groupId === viewFilter.value;
};

const matchesSearch = (recording: Recording, search: string): boolean =>
  recording.filename.toLowerCase().includes(search) ||
  (recording.profileName ?? '').toLowerCase().includes(search);

// The group/search selection. A bundle is found by its own name or by any of its items.
const selection = computed<{ recordings: Recording[]; bundles: BundleWithMembers[] }>(() => {
  const search = searchText.value.toLowerCase();
  const recordings = standaloneRecordings.value.filter(
    recording => inSelectedGroup(recording.groupId) && (!search || matchesSearch(recording, search))
  );
  const bundles: BundleWithMembers[] = [];
  for (const bundle of allBundles.value) {
    const members = membersByBundle.value.get(bundle.id) ?? [];
    const found =
      !search ||
      bundle.name.toLowerCase().includes(search) ||
      members.some(member => matchesSearch(member, search));
    if (inSelectedGroup(bundle.groupId) && found) {
      bundles.push({ bundle, members });
    }
  }
  return { recordings, bundles };
});

const selectionCount = computed(
  () => selection.value.recordings.length + selection.value.bundles.length
);

// Recording format taxonomy — recordingKinds is the single source of truth for how a recording is typed.
type RecordingType = RecordingKindKey;
type TypeFilter = 'all' | RecordingType;

const recordingType = (recording: Recording): RecordingType =>
  recordingKindOf(recording.eventSource).key;

const typeFilter = ref<TypeFilter>('all');

// Counts per format within the current group/search selection — drives the type filter pills.
const typeCounts = computed<Record<RecordingType, number>>(() => {
  const counts: Record<RecordingType, number> = { JFR: 0, PPROF: 0, OTLP: 0, HEAP: 0, PERF: 0 };
  const { recordings, bundles } = selection.value;
  for (const recording of [...recordings, ...bundles.flatMap(entry => entry.members)]) {
    counts[recordingType(recording)]++;
  }
  return counts;
});

// The list is split into two columns: every recording on the left (standalone ones, and bundles with
// all their items and files), every profile built from them on the right, one row each.
const columns = computed<RecordingColumns>(() => {
  const type = typeFilter.value;
  return splitColumns({
    ...selection.value,
    shows: type === 'all' ? null : recording => recordingType(recording) === type
  });
});

// Type filters wear the drop zone's format icons, so a format looks the same wherever it appears.
const FORMAT_ICON_BY_ID = new Map(RECORDING_FORMATS.map(format => [format.id, format.icon]));

const TYPE_FILTERS: { key: RecordingType; label: string; format: string }[] = [
  { key: 'JFR', label: 'JFR', format: 'jfr' },
  { key: 'PPROF', label: 'pprof', format: 'pprof' },
  { key: 'OTLP', label: 'OpenTelemetry', format: 'otlp' },
  { key: 'HEAP', label: 'Heap Dumps', format: 'heap' },
  { key: 'PERF', label: 'Perf Counters', format: 'perf' }
];

const selectTypeFilter = (type: TypeFilter) => {
  typeFilter.value = type;
};

// Origin breadcrumb derivation
const buildOrigin = (recordingTags: RecordingTag[] | undefined): RecordingOrigin | undefined => {
  const tags = recordingTags ?? [];
  const sys = Object.fromEntries(
    tags.filter(t => t.key.startsWith('origin.')).map(t => [t.key, t.value])
  );
  if (!sys['origin.remote']) {
    return undefined;
  }
  return {
    remote: sys['origin.remote'],
    workspace: sys['origin.workspace'] ?? '',
    project: sys['origin.project'] ?? ''
  };
};

// --- Helpers ---

const getGroupCount = (groupId: string): number => {
  return groupCountsMap.value.get(groupId) ?? 0;
};

/**
 * Longest matching extension wins, so `.pb.gz` resolves to pprof rather than to whatever
 * else happens to end in `.gz`. Returns null for a file no parser claims.
 */
const formatForFile = (filename: string): RecordingFormat | null => {
  const lower = filename.toLowerCase();
  let matched: RecordingFormat | null = null;
  let matchedLength = 0;
  for (const format of RECORDING_FORMATS) {
    for (const extension of format.extensions) {
      if (lower.endsWith(extension) && extension.length > matchedLength) {
        matched = format;
        matchedLength = extension.length;
      }
    }
  }
  return matched;
};

const formatIdForFile = (filename: string): string => {
  return formatForFile(filename)?.id ?? UNKNOWN_FORMAT_ID;
};

const selectFilter = (filter: ViewFilter, uploadTarget: string | null) => {
  viewFilter.value = filter;
  selectedGroupId.value = uploadTarget;
};

const toggleRecordingFiles = (recordingId: string) => {
  const next = new Set(expandedRecordings.value);
  if (next.has(recordingId)) {
    next.delete(recordingId);
  } else {
    next.add(recordingId);
  }
  expandedRecordings.value = next;
};

const toggleBundle = (bundleId: string) => {
  const next = new Set(expandedBundles.value);
  if (next.has(bundleId)) {
    next.delete(bundleId);
  } else {
    next.add(bundleId);
  }
  expandedBundles.value = next;
};

const downloadBundleFile = async (bundleId: string, fileId: string) => {
  try {
    await recordingsClient.downloadBundleFile(bundleId, fileId);
  } catch {
    // Toast shown by HttpInterceptor
  }
};

const deleteBundle = async () => {
  const bundle = deletingBundle.value;
  if (!bundle) {
    return;
  }
  try {
    await recordingsClient.deleteBundle(bundle.id);
    deletingBundle.value = null;
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

const downloadFile = async (recordingId: string, fileId: string) => {
  try {
    await recordingsClient.downloadFile(recordingId, fileId);
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : 'Failed to download file';
    ToastService.error('Failed to download file', msg);
  }
};

// --- File input / drop ---

const triggerFileInput = () => {
  fileInputRef.value?.click();
};

const isAcceptedFile = (filename: string): boolean => {
  return formatForFile(filename) !== null;
};

const filterAcceptedFiles = (files: File[]): File[] => {
  const accepted: File[] = [];
  const rejected: string[] = [];
  for (const file of files) {
    if (isAcceptedFile(file.name)) {
      accepted.push(file);
    } else {
      rejected.push(file.name);
    }
  }
  if (rejected.length > 0) {
    const noun = rejected.length === 1 ? 'file' : 'files';
    errorMessage.value = `Unsupported ${noun}: ${rejected.join(', ')}. Allowed: ${ALLOWED_FILE_SUFFIXES.join(', ')}`;
  } else if (accepted.length > 0) {
    errorMessage.value = null;
  }
  return accepted;
};

const handleFileInput = (event: Event) => {
  const input = event.target as HTMLInputElement;
  if (input.files && input.files.length > 0) {
    const accepted = filterAcceptedFiles(Array.from(input.files));
    if (accepted.length > 0) {
      uploadFiles.value = [...uploadFiles.value, ...accepted];
      uploadRecordings();
    }
  }
  input.value = '';
};

const isFileDrag = (event: DragEvent): boolean => {
  const types = event.dataTransfer?.types;
  if (types === undefined) {
    return false;
  }
  return Array.from(types).includes(DRAG_TYPE_FILES);
};

const resetDragState = () => {
  dragDepth.value = 0;
  dragActive.value = false;
  dragFileCount.value = 0;
};

const onDragEnter = (event: DragEvent) => {
  if (!isFileDrag(event)) {
    return;
  }
  dragDepth.value++;
  dragActive.value = true;
  const items = event.dataTransfer?.items;
  if (items !== undefined) {
    dragFileCount.value = Array.from(items).filter(item => item.kind === 'file').length;
  }
};

const onDragLeave = (event: DragEvent) => {
  if (!isFileDrag(event)) {
    return;
  }
  dragDepth.value = Math.max(0, dragDepth.value - 1);
  if (dragDepth.value === 0) {
    resetDragState();
  }
};

const handleDrop = (event: DragEvent) => {
  resetDragState();
  const files = event.dataTransfer?.files;
  if (files && files.length > 0) {
    const accepted = filterAcceptedFiles(Array.from(files));
    if (accepted.length > 0) {
      uploadFiles.value = [...uploadFiles.value, ...accepted];
      uploadRecordings();
    }
  }
};

const removeFile = (index: number) => {
  const file = uploadFiles.value[index];
  uploadFiles.value.splice(index, 1);
  if (file) {
    cancelAutoRemove(file.name);
    if (uploadProgress.value[file.name]) {
      const next = { ...uploadProgress.value };
      delete next[file.name];
      uploadProgress.value = next;
    }
  }
};

// --- Upload (auto-triggered, idempotent: only handles files without progress yet) ---

const uploadRecordings = async () => {
  const filesToUpload = uploadFiles.value.filter(f => !uploadProgress.value[f.name]);
  if (filesToUpload.length === 0) {
    return;
  }

  const seeded: Record<string, UploadProgressEntry> = { ...uploadProgress.value };
  for (const file of filesToUpload) {
    seeded[file.name] = { progress: 0, status: 'pending' };
  }
  uploadProgress.value = seeded;

  const uploadPromises = filesToUpload.map(async file => {
    try {
      uploadProgress.value[file.name].status = 'uploading';

      const progressInterval = setInterval(() => {
        if (uploadProgress.value[file.name]?.progress < 90) {
          uploadProgress.value[file.name].progress += Math.floor(Math.random() * 10) + 5;
        }
      }, 300);

      await recordingsClient.uploadRecording(file, selectedGroupId.value || undefined);

      clearInterval(progressInterval);
      uploadProgress.value[file.name].progress = 100;
      uploadProgress.value[file.name].status = 'complete';
      scheduleAutoRemove(file.name);
    } catch {
      uploadProgress.value[file.name].status = 'error';
    }
  });

  await Promise.all(uploadPromises);
  await loadData();
};

// --- Recording actions ---

/**
 * Runs one initialization of a recording. The request does not return until the pipeline is done, so
 * the card is marked as analyzing meanwhile; the list refresh picks up the run's stages.
 */
const runInitialization = async (recordingId: string, request: () => Promise<string>) => {
  analyzingRecordings.value.add(recordingId);
  try {
    await request();
    analyzingRecordings.value.delete(recordingId);
    await loadData();
  } catch {
    analyzingRecordings.value.delete(recordingId);
  }
};

/** A recording's first initialization, and the retry of a failed one. */
const analyzeRecording = (recordingId: string) =>
  runInitialization(recordingId, () => recordingsClient.analyzeRecording(recordingId));

/** Another profile from a recording that already has some; those stay as they are. */
const initializeAgain = (recordingId: string) =>
  runInitialization(recordingId, () => recordingsClient.initializeAgain(recordingId));

/**
 * A recording never initialized is initialized at once, and a failed run is retried. One that already
 * has profiles asks first: open one of them, or build another beside them.
 */
const handleRecordingClick = (recording: Recording) => {
  if (analyzingRecordings.value.has(recording.id)) {
    return;
  }
  if (builtProfiles(recording).length > 0) {
    confirmingRecording.value = recording;
  } else {
    analyzeRecording(recording.id);
  }
};

const openConfirmedProfile = (profile: RecordingProfile) => {
  const recording = confirmingRecording.value;
  confirmingRecording.value = null;
  if (recording) {
    openProfile(profile.profileId, recording.eventSource);
  }
};

const initializeConfirmedAgain = () => {
  const recording = confirmingRecording.value;
  confirmingRecording.value = null;
  if (recording) {
    initializeAgain(recording.id);
  }
};

/**
 * Drops a heap dump's index and builds it again, compressed oops auto-detected. The run shows on the
 * card like the first one; forcing compressed oops is the Heap Dump Overview's own re-initialize.
 */
const reinitializeHeapDump = async (profileId: string) => {
  try {
    await new HeapDumpClient(profileId).reinitialize();
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

const openProfile = async (profileId: string, eventSource: string) => {
  await router.push(profileLandingRoute(profileId, eventSource));
};

// --- Group CRUD ---

const createGroup = async () => {
  const name = newGroupName.value.trim();
  if (!name) {
    return;
  }
  try {
    await recordingsClient.createGroup(name);
    showCreateGroupModal.value = false;
    newGroupName.value = '';
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

const confirmDeleteGroup = (groupId: string) => {
  deletingGroupId.value = groupId;
};

const deleteGroup = async () => {
  if (!deletingGroupId.value) {
    return;
  }
  try {
    if (selectedGroupId.value === deletingGroupId.value) {
      selectedGroupId.value = null;
    }
    if (viewFilter.value === deletingGroupId.value) {
      viewFilter.value = 'all';
    }
    await recordingsClient.deleteGroup(deletingGroupId.value);
    deletingGroupId.value = null;
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

// --- Profile edit ---

const startEditProfile = (item: ProfileItem) => {
  editingProfile.value = item;
  editProfileName.value = item.profile.profileName;
};

const updateProfileName = async () => {
  const item = editingProfile.value;
  if (!item || !editProfileName.value.trim()) {
    return;
  }
  try {
    await recordingsClient.updateProfileName(
      item.recording.id,
      item.profile.profileId,
      editProfileName.value.trim()
    );
    editingProfile.value = null;
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

/** Deletes one profile; its recording stays on the left with its other profiles. */
const deleteProfile = async (item: ProfileItem) => {
  try {
    await recordingsClient.deleteProfile(item.recording.id, item.profile.profileId);
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

const deleteRecording = async (recordingId: string) => {
  try {
    await recordingsClient.deleteRecording(recordingId);
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

// --- Data ---

const loadData = async () => {
  try {
    const [recordings, bundles, groups] = await Promise.all([
      recordingsClient.listRecordings(),
      recordingsClient.listBundles(),
      recordingsClient.listGroups()
    ]);
    allRecordings.value = recordings;
    allBundles.value = bundles;
    allGroups.value = groups;
  } catch (error) {
    console.error('Failed to load data:', error);
  }
};

/**
 * Refreshing while an initialization is in flight, and only then. The list is otherwise refreshed by
 * the actions that change it; a profile being built changes underneath us with nothing to emit, so
 * the stage chips need asking. The tick is separate and faster, because the chip showing the running
 * stage counts up between refreshes rather than sitting still until the next one.
 */
const INIT_REFRESH_INTERVAL_MS = 2000;
const INIT_TICK_INTERVAL_MS = 200;

const tickNow = ref(Date.now());
let initRefreshTimer: ReturnType<typeof setInterval> | null = null;
let initTickTimer: ReturnType<typeof setInterval> | null = null;

const anyInitializing = computed(
  () =>
    // The client-side set counts too: the analyze request does not return until the pipeline is
    // done, so without it nothing refreshes the list to discover the run it just started.
    analyzingRecordings.value.size > 0 ||
    allRecordings.value.some(recording => recording.initProgress?.state === 'running')
);

const stopInitWatch = () => {
  if (initRefreshTimer !== null) {
    clearInterval(initRefreshTimer);
    initRefreshTimer = null;
  }
  if (initTickTimer !== null) {
    clearInterval(initTickTimer);
    initTickTimer = null;
  }
};

const startInitWatch = () => {
  if (initRefreshTimer !== null) {
    return;
  }
  initRefreshTimer = setInterval(loadData, INIT_REFRESH_INTERVAL_MS);
  initTickTimer = setInterval(() => {
    tickNow.value = Date.now();
  }, INIT_TICK_INTERVAL_MS);
};

watch(anyInitializing, initializing => {
  if (initializing) {
    startInitWatch();
  } else {
    stopInitWatch();
  }
});

onMounted(() => {
  loadData();
});

onBeforeUnmount(stopInitWatch);

// --- Drag and drop: recording card → group chip ---

const onChipDragOver = (event: DragEvent, key: string) => {
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move';
  }
  dragOverGroupKey.value = key;
};

const onChipDragLeave = (event: DragEvent, key: string) => {
  const related = event.relatedTarget as HTMLElement | null;
  const current = event.currentTarget as HTMLElement;
  if (!related || !current.contains(related)) {
    if (dragOverGroupKey.value === key) {
      dragOverGroupKey.value = null;
    }
  }
};

const onChipDrop = async (event: DragEvent, targetGroupId: string | null) => {
  event.preventDefault();
  dragOverGroupKey.value = null;
  const recordingId = event.dataTransfer?.getData('text/plain');
  if (!recordingId) {
    return;
  }
  if (recordingId.startsWith(BUNDLE_DRAG_PREFIX)) {
    await moveBundle(recordingId.slice(BUNDLE_DRAG_PREFIX.length), targetGroupId);
    return;
  }
  const recording = allRecordings.value.find(r => r.id === recordingId);
  if (!recording) {
    return;
  }
  // A bundle of one item is shown as that item's row; the bundle, not the item, sits in a group.
  if (recording.bundleId) {
    await moveBundle(recording.bundleId, targetGroupId);
    return;
  }
  if ((recording.groupId || null) === targetGroupId) {
    return;
  }
  recording.groupId = targetGroupId;
  try {
    await recordingsClient.moveRecordingToGroup(recordingId, targetGroupId);
  } catch {
    await loadData();
  }
};

const moveBundle = async (bundleId: string, targetGroupId: string | null) => {
  const bundle = allBundles.value.find(b => b.id === bundleId);
  if (!bundle || (bundle.groupId || null) === targetGroupId) {
    return;
  }
  bundle.groupId = targetGroupId;
  try {
    await recordingsClient.moveBundleToGroup(bundleId, targetGroupId);
  } catch {
    await loadData();
  }
};

const onDragEnd = () => {
  dragOverGroupKey.value = null;
};
</script>

<style scoped>
@import '@shared/styles/shared-components.css';

/* ============ Upload section ============ */
.upload-section {
  margin-bottom: var(--spacing-4);
}

.drop-zone {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 28px 24px 22px;
  text-align: center;
  background-color: var(--color-white);
  background-image: radial-gradient(var(--color-border-input) 1px, transparent 1px);
  background-size: 16px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  transition:
    background-color var(--transition-base),
    border-color var(--transition-base),
    box-shadow var(--transition-base);
}

.drop-zone:hover {
  border-color: var(--color-primary-border-light);
}

.drop-zone.drag-over {
  border-color: var(--color-primary);
  background-color: var(--color-indigo-lightest);
  background-image: radial-gradient(var(--color-indigo-accent) 1px, transparent 1px);
  box-shadow: 0 0 0 4px var(--color-primary-light);
}

.file-input-hidden {
  display: none;
}

.drop-target-group {
  position: absolute;
  top: 14px;
  right: 16px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.drop-target-group strong {
  color: var(--color-dark);
  font-weight: var(--font-weight-semibold);
}

/* ============ Upload target ============ */
.drop-target {
  position: relative;
  width: 76px;
  height: 76px;
  margin-bottom: 4px;
  display: grid;
  place-items: center;
  background: transparent;
  border: 0;
  padding: 0;
  cursor: pointer;
}

.drop-ring {
  position: absolute;
  inset: 0;
  border: 1px solid var(--color-primary-border-light);
  border-radius: var(--radius-circle);
}

.drop-ring-outer {
  inset: -14px;
  border-style: dashed;
  opacity: 0.7;
}

.drop-core {
  position: relative;
  z-index: 1;
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-circle);
  background: var(--color-white);
  border: 1px solid var(--color-primary-border-light);
  color: var(--color-primary);
  font-size: var(--font-size-xxl);
  box-shadow: var(--shadow-md);
  transition:
    background var(--transition-base),
    color var(--transition-base),
    transform var(--transition-base);
}

.drop-target:hover .drop-core,
.drop-target:focus-visible .drop-core {
  border-color: var(--color-primary);
}

.drop-zone.drag-over .drop-core {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
  transform: scale(1.08);
}

.drop-zone.drag-over .drop-ring {
  border-color: var(--color-primary);
  animation: drop-ripple 1.4s ease-out infinite;
}

.drop-zone.drag-over .drop-ring-outer {
  animation-delay: 0.5s;
}

@keyframes drop-ripple {
  from {
    transform: scale(0.8);
    opacity: 0.9;
  }
  to {
    transform: scale(1.5);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .drop-zone.drag-over .drop-ring {
    animation: none;
  }
}

.drop-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
}

.drop-hint {
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.drop-browse {
  background: none;
  border: 0;
  padding: 0;
  font: inherit;
  font-weight: var(--font-weight-medium);
  color: var(--color-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.drop-browse:hover {
  color: var(--color-primary-hover);
}

/* ============ Format pills (one per parser) ============ */
.drop-formats {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 4px;
}

.format-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px 6px 6px;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-sm);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-dark);
}

.format-ic {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  color: var(--color-white);
  font-size: var(--font-size-xs);
}

.format-ext {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.format-jfr .format-ic {
  background: var(--color-primary);
}

.format-pprof .format-ic {
  background: var(--color-teal);
}

.format-otlp .format-ic {
  background: var(--color-orange);
}

.format-heap .format-ic {
  background: var(--color-purple);
}

.format-perf .format-ic {
  background: var(--color-sky);
}

.drop-file-dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-circle);
  background: var(--color-text-light);
  flex-shrink: 0;
}

.dot-jfr {
  background: var(--color-primary);
}

.dot-pprof {
  background: var(--color-teal);
}

.dot-otlp {
  background: var(--color-orange);
}

.dot-heap {
  background: var(--color-purple);
}

.dot-perf {
  background: var(--color-sky);
}

/* ============ Files (auto-uploading, inside the drop zone) ============ */
.drop-files {
  align-self: stretch;
  margin-top: 8px;
  padding: 6px;
  text-align: left;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drop-file {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 10px;
  border-radius: var(--radius-base);
  transition: background var(--transition-fast);
}

.drop-file:hover {
  background: var(--color-bg-hover-alt);
}

.drop-file-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.drop-file-size {
  font-size: var(--font-size-sm);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.drop-file-remove {
  background: transparent;
  border: 0;
  color: var(--color-text-light);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: var(--radius-sm);
  font-size: 1rem;
  flex-shrink: 0;
  transition: all var(--transition-base);
}

.drop-file-remove:hover {
  color: var(--color-danger-hover);
  background: var(--color-danger-light);
}

.progress-track {
  width: 160px;
  height: 6px;
  background: var(--color-border);
  border-radius: 3px;
  overflow: hidden;
  flex-shrink: 0;
}

.progress-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--color-primary);
  transition: width 0.3s ease;
}

.progress-fill.uploading {
  background: var(--color-primary);
}

.progress-fill.complete {
  background: var(--color-success);
}

.progress-fill.error {
  background: var(--color-danger);
}

.progress-status {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  width: 64px;
  text-align: right;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

.progress-status i {
  font-size: 1rem;
}

.progress-status.status-pending {
  color: var(--color-text-light);
}

.progress-status.status-uploading {
  color: var(--color-primary);
}

.progress-status.status-complete {
  color: var(--color-success);
}

.progress-status.status-error {
  color: var(--color-danger);
}

/* ============ Error ============ */
.upload-error {
  margin-bottom: var(--spacing-4);
  padding: 8px 12px;
  background: var(--color-danger-bg-lighter);
  border: 1px solid var(--color-danger-border-light);
  border-radius: var(--radius-base);
  color: var(--color-danger-hover);
  font-size: var(--font-size-sm);
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ============ Group bar ============ */
.group-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 4px 0 14px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--color-border-light);
}

.group-bar-label {
  font-size: var(--font-size-xs);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-muted);
  font-weight: var(--font-weight-semibold);
  margin-right: 4px;
}

.group-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  border-radius: var(--radius-base);
  background: var(--color-white);
  border: 1px solid var(--color-border);
  font-size: var(--font-size-sm);
  color: var(--color-text);
  cursor: pointer;
  font-weight: var(--font-weight-medium);
  font-family: inherit;
  transition: all var(--transition-base);
}

.group-chip:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.group-chip-all {
  border-left: 3px solid var(--color-slate-muted);
  padding-left: 9px;
}

.group-chip-all:hover {
  border-left-color: var(--color-slate-muted);
}

.group-chip-all.active {
  border-left-color: var(--color-slate-muted);
}

.group-chip-custom {
  border-left: 3px solid var(--color-teal);
  padding-left: 9px;
}

.group-chip-custom:hover {
  border-left-color: var(--color-teal);
}

.group-chip-custom.active {
  border-left-color: var(--color-teal);
}

.group-chip-ungrouped {
  border-left: 3px solid var(--color-amber);
  padding-left: 9px;
}

.group-chip-ungrouped:hover {
  border-left-color: var(--color-amber);
}

.group-chip-ungrouped.active {
  border-left-color: var(--color-amber);
}

.group-chip > i {
  font-size: 0.75rem;
}

.group-chip.active {
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-color: var(--color-primary-light);
}

.group-chip.drop-target {
  background: var(--color-primary-lighter);
  outline: 1.5px dashed var(--color-primary);
  outline-offset: 2px;
}

.chip-count {
  background: var(--color-lighter);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  padding: 1px 7px;
  border-radius: 999px;
}

.group-chip.active .chip-count {
  background: var(--color-white);
  color: var(--color-primary);
}

.chip-delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 0 0 2px;
  color: var(--color-text-light);
  font-size: 0.75rem;
  visibility: hidden;
  transition: color var(--transition-fast);
}

.group-chip:hover .chip-delete {
  visibility: visible;
}

.chip-delete:hover {
  color: var(--color-danger);
}

.new-group-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  border-radius: var(--radius-base);
  background: transparent;
  border: 1px dashed var(--color-border-input);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-muted);
  cursor: pointer;
  font-family: inherit;
  transition: all var(--transition-base);
}

.group-bar-search {
  margin-left: auto;
}

.new-group-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* ============ Type filter + recordings list ============ */
.type-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 14px;
}

/* Same shape as the drop zone's format pills; the active one takes its format's soft tint */
.type-chip {
  --type-solid: var(--color-dark-bg);
  --type-soft: var(--color-lighter);
  --type-text: var(--color-dark);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px 5px 5px;
  border-radius: var(--radius-base);
  background: var(--color-white);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-dark);
  cursor: pointer;
  font-family: inherit;
  transition: all var(--transition-base);
}

.type-chip--jfr {
  --type-solid: var(--color-primary);
  --type-soft: var(--color-indigo-bg);
  --type-text: var(--color-indigo-text);
}

.type-chip--pprof {
  --type-solid: var(--color-teal);
  --type-soft: var(--color-teal-bg);
  --type-text: var(--color-teal-text);
}

.type-chip--otlp {
  --type-solid: var(--color-orange);
  --type-soft: var(--color-orange-bg);
  --type-text: var(--color-orange-text);
}

.type-chip--heap {
  --type-solid: var(--color-purple);
  --type-soft: var(--color-purple-bg);
  --type-text: var(--color-purple-text);
}

.type-chip--perf {
  --type-solid: var(--color-sky);
  --type-soft: var(--color-sky-bg);
  --type-text: var(--color-info-text);
}

.type-chip:hover {
  border-color: var(--type-solid);
}

.type-ic {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--type-solid);
  color: var(--color-white);
  font-size: var(--font-size-xs);
}

.type-count {
  font-variant-numeric: tabular-nums;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-lighter);
  color: var(--color-text);
}

.type-chip.active {
  background: var(--type-soft);
  border-color: var(--type-solid);
  color: var(--type-text);
}

.type-chip.active .type-count {
  background: var(--color-white);
  color: var(--type-text);
}

.card-stack {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.recordings-list {
  margin-top: 4px;
}

/* Two-column split: recordings (left) | profiles (right). */
.recordings-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.25rem;
  align-items: start;
  margin-top: 0.5rem;
}

.rec-col {
  min-width: 0;
}

.rec-col__head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--color-text-muted);
  margin-bottom: 0.6rem;
}

.rec-col__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.rec-col__dot--recordings {
  background: var(--color-amber);
}

.rec-col__dot--profiles {
  background: var(--color-success);
}

.rec-col__count {
  background: var(--color-lighter);
  color: var(--color-text);
  border-radius: var(--radius-pill, 999px);
  padding: 1px 8px;
  font-size: 0.7rem;
  font-weight: 700;
}

.rec-col__empty {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1.1rem 1rem;
  border: 1px dashed var(--color-border-input);
  border-radius: var(--radius-md);
  background: var(--color-light);
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

@media (max-width: 900px) {
  .recordings-split {
    grid-template-columns: 1fr;
    gap: 1.5rem;
  }

  .drop-lanes {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
