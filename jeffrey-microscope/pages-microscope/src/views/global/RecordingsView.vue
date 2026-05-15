<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader
          icon="bi bi-record-circle"
          title="Recordings"
          :badge="allRecordings.length"
        >
          <template #actions>
            <div v-if="allRecordings.length > 0" class="page-search">
              <i class="bi bi-search"></i>
              <input v-model="searchText" type="text" placeholder="Search..." />
            </div>
          </template>
        </MainCardHeader>
      </template>

      <!-- 1. Upload zone (single drop area for both types) -->
      <div class="upload-section">
        <div
          class="drop-zone"
          :class="{ 'drag-over': dragActive }"
          @dragover.prevent="dragActive = true"
          @dragleave.prevent="dragActive = false"
          @drop.prevent="handleDrop"
        >
          <input
            ref="fileInputRef"
            type="file"
            accept=".jfr,.lz4,.hprof,.gz"
            multiple
            class="file-input-hidden"
            @change="handleFileInput"
          />
          <div class="drop-stack">
            <div class="drop-ic drop-ic-jfr" title="JFR recording">
              <i class="bi bi-activity"></i>
            </div>
            <div class="drop-ic drop-ic-heap" title="Heap dump">
              <i class="bi bi-pie-chart-fill"></i>
            </div>
          </div>
          <div class="drop-body">
            <div class="drop-title">Drop Recordings</div>
            <div class="drop-hint">
              <Badge value=".jfr" variant="indigo" size="s" :uppercase="false" borderless />
              <Badge value=".jfr.lz4" variant="indigo" size="s" :uppercase="false" borderless />
              JFR recordings ·
              <Badge value=".hprof" variant="purple" size="s" :uppercase="false" borderless />
              <Badge value=".hprof.gz" variant="purple" size="s" :uppercase="false" borderless />
              heap dumps
              <span class="upload-target-hint">
                · uploads to <strong>{{ uploadTargetLabel }}</strong>
              </span>
            </div>
          </div>
          <button
            class="browse-btn"
            type="button"
            @click="triggerFileInput"
          >
            <i class="bi bi-folder2-open"></i>
            Browse files…
          </button>
        </div>

        <!-- Queued files -->
        <div v-if="uploadFiles.length > 0" class="queue-panel">
          <div class="queue-header">
            <span class="queue-title">
              <i class="bi bi-files"></i>
              Uploading to
              <span class="queue-target"><strong>{{ uploadTargetLabel }}</strong></span>
            </span>
            <div class="queue-actions">
              <button
                class="queue-btn queue-btn-secondary"
                type="button"
                :disabled="isUploading"
                @click="triggerFileInput"
              >
                <i class="bi bi-plus-lg"></i>
                Add more
              </button>
              <button
                class="queue-btn queue-btn-secondary"
                type="button"
                :disabled="isUploading"
                @click="clearFiles"
              >
                Clear
              </button>
              <button
                class="queue-btn queue-btn-primary"
                type="button"
                :disabled="isUploading"
                @click="uploadRecordings"
              >
                <i class="bi bi-cloud-upload"></i>
                Upload all
              </button>
            </div>
          </div>
          <div class="queue-list">
            <div
              v-for="(file, index) in uploadFiles"
              :key="file.name + index"
              class="queue-item"
            >
              <i :class="fileIconClass(file.name)" class="queue-item-icon"></i>
              <span class="queue-item-name">{{ file.name }}</span>
              <span class="queue-item-size">{{ FormattingService.formatBytes(file.size) }}</span>
              <template v-if="uploadProgress[file.name]">
                <div class="progress-track">
                  <div
                    class="progress-fill"
                    :class="uploadProgress[file.name].status"
                    :style="{ width: Math.min(uploadProgress[file.name].progress, 100) + '%' }"
                  ></div>
                </div>
                <span
                  class="progress-status"
                  :class="'status-' + uploadProgress[file.name].status"
                >
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
              </template>
              <button
                v-else
                class="queue-item-remove"
                type="button"
                title="Remove"
                @click="removeFile(index)"
              >
                <i class="bi bi-x"></i>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Error -->
      <div v-if="errorMessage" class="upload-error">
        <i class="bi bi-exclamation-triangle-fill"></i>
        {{ errorMessage }}
      </div>

      <!-- 2. Group selector bar -->
      <div v-if="allRecordings.length > 0 || allGroups.length > 0" class="group-bar">
        <span class="group-bar-label">Group</span>
        <button
          class="group-chip group-chip-all"
          :class="{ active: viewFilter === 'all' }"
          type="button"
          @click="selectFilter('all', null)"
        >
          All
          <span class="chip-count">{{ allRecordings.length }}</span>
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
          <span class="chip-count">{{ getGroupCount(group.id, 'total') }}</span>
          <span
            class="chip-delete"
            title="Delete group"
            @click.stop="confirmDeleteGroup(group.id)"
          >
            <i class="bi bi-trash"></i>
          </span>
        </button>
        <button
          class="new-group-btn"
          type="button"
          @click="showCreateGroupModal = true"
        >
          <i class="bi bi-plus-lg"></i>
          New Group
        </button>
      </div>

      <!-- 3. Two-column lists -->
      <EmptyState
        v-if="allRecordings.length === 0 && allGroups.length === 0"
        icon="bi-record-circle"
        title="No recordings yet"
        description="Drop a JFR or heap dump file above to get started"
      />
      <EmptyState
        v-else-if="jfrRecordings.length === 0 && heapRecordings.length === 0"
        icon="bi-search"
        title="No recordings match the current filter"
      />
      <div v-else class="recordings-columns">
        <!-- JFR column -->
        <div class="rec-column">
          <div class="col-head col-head-jfr">
            <span class="col-pill col-pill-jfr">
              <i class="bi bi-activity"></i>
              JFR Recordings
            </span>
            <Badge :value="jfrRecordings.length" variant="indigo" size="s" />
            <span v-if="viewFilter !== 'all'" class="col-context">
              Group: <strong>{{ activeFilterLabel }}</strong>
            </span>
          </div>
          <div class="card-stack">
            <RecordingCard
              v-for="recording in jfrRecordings"
              :key="recording.id"
              :recording-id="recording.id"
              :name="recording.profileName ?? recording.filename"
              :size-in-bytes="recording.sizeInBytes"
              :duration-in-millis="recording.durationInMillis"
              :uploaded-at="recording.uploadedAt"
              :source-type="recording.eventSource"
              :has-profile="recording.hasProfile"
              :profile-id="recording.profileId"
              :profile-size-in-bytes="recording.profileSizeInBytes"
              :profile-modified="recording.profileModified"
              :analyzing="analyzingRecordings.has(recording.id)"
              :draggable="true"
              :origin="buildOrigin(recording)"
              :file-count="recording.files?.length ?? 0"
              :expandable="(recording.files?.length ?? 0) > 1"
              :expanded="expandedRecordings.has(recording.id)"
              @click="handleCardClick(recording)"
              @create-profile="analyzeRecording(recording.id)"
              @open-profile="openProfile(recording)"
              @edit-profile="startEditProfile(recording)"
              @delete-profile="deleteProfileFromRecording(recording.id)"
              @delete-recording="deleteRecording(recording.id)"
              @toggle-expand="toggleRecordingFiles(recording.id)"
              @dragend="onDragEnd"
            >
              <template #expanded-content>
                <RecordingFileGroupList
                  v-if="recording.files && recording.files.length > 0"
                  :recording-id="recording.id"
                  :files="recording.files"
                  @download="downloadFile"
                />
                <div v-else class="small py-1 text-muted">
                  <i class="bi bi-exclamation-circle me-1"></i>
                  No recording files available
                </div>
              </template>
            </RecordingCard>
            <div v-if="jfrRecordings.length === 0" class="col-empty">
              <i class="bi bi-activity"></i>
              <span>No JFR recordings{{ viewFilter !== 'all' ? ' in this group' : '' }}</span>
            </div>
          </div>
        </div>

        <!-- Heap column -->
        <div class="rec-column">
          <div class="col-head col-head-heap">
            <span class="col-pill col-pill-heap">
              <i class="bi bi-pie-chart-fill"></i>
              Heap Dumps
            </span>
            <Badge :value="heapRecordings.length" variant="purple" size="s" />
            <span v-if="viewFilter !== 'all'" class="col-context">
              Group: <strong>{{ activeFilterLabel }}</strong>
            </span>
          </div>
          <div class="card-stack">
            <RecordingCard
              v-for="recording in heapRecordings"
              :key="recording.id"
              :recording-id="recording.id"
              :name="recording.profileName ?? recording.filename"
              :size-in-bytes="recording.sizeInBytes"
              :duration-in-millis="recording.durationInMillis"
              :uploaded-at="recording.uploadedAt"
              :source-type="recording.eventSource"
              :has-profile="recording.hasProfile"
              :profile-id="recording.profileId"
              :profile-size-in-bytes="recording.profileSizeInBytes"
              :profile-modified="recording.profileModified"
              :analyzing="analyzingRecordings.has(recording.id)"
              :draggable="true"
              :origin="buildOrigin(recording)"
              :file-count="recording.files?.length ?? 0"
              :expandable="(recording.files?.length ?? 0) > 1"
              :expanded="expandedRecordings.has(recording.id)"
              @click="handleCardClick(recording)"
              @create-profile="analyzeRecording(recording.id)"
              @open-profile="openProfile(recording)"
              @edit-profile="startEditProfile(recording)"
              @delete-profile="deleteProfileFromRecording(recording.id)"
              @delete-recording="deleteRecording(recording.id)"
              @toggle-expand="toggleRecordingFiles(recording.id)"
              @dragend="onDragEnd"
            >
              <template #expanded-content>
                <RecordingFileGroupList
                  v-if="recording.files && recording.files.length > 0"
                  :recording-id="recording.id"
                  :files="recording.files"
                  @download="downloadFile"
                />
                <div v-else class="small py-1 text-muted">
                  <i class="bi bi-exclamation-circle me-1"></i>
                  No recording files available
                </div>
              </template>
            </RecordingCard>
            <div v-if="heapRecordings.length === 0" class="col-empty">
              <i class="bi bi-pie-chart-fill"></i>
              <span>No heap dumps{{ viewFilter !== 'all' ? ' in this group' : '' }}</span>
            </div>
          </div>
        </div>
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
      v-if="editingRecording"
      v-model="editProfileName"
      @submit="updateProfileName"
      @close="editingRecording = null"
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
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import RecordingsClient from '@/services/api/RecordingsClient';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import RecordingCard from '@/components/RecordingCard.vue';
import Badge from '@/components/Badge.vue';
import EditNameModal from '@/components/EditNameModal.vue';
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import EmptyState from '@/components/EmptyState.vue';
import RecordingFileGroupList from '@/components/RecordingFileGroupList.vue';
import FormattingService from '@/services/FormattingService';
import ToastService from '@/services/ToastService';
import type RecordingGroup from '@/services/api/model/RecordingGroup';
import type Recording from '@/services/api/model/Recording';

const HEAP_DUMP_SOURCE = 'HEAP_DUMP';
const UNGROUPED_KEY = '__ungrouped__';

type ViewFilter = 'all' | typeof UNGROUPED_KEY | string;

interface UploadProgressEntry {
  progress: number;
  status: 'pending' | 'uploading' | 'complete' | 'error';
}

interface GroupCounts {
  total: number;
  jfr: number;
  heap: number;
}

const router = useRouter();
const recordingsClient = new RecordingsClient();

// State
const uploadFiles = ref<File[]>([]);
const uploadProgress = ref<Record<string, UploadProgressEntry>>({});
const isUploading = computed(() =>
  Object.values(uploadProgress.value).some(p => p.status === 'uploading')
);
const errorMessage = ref<string | null>(null);
const allRecordings = ref<Recording[]>([]);
const allGroups = ref<RecordingGroup[]>([]);

// Filter + upload target
const viewFilter = ref<ViewFilter>('all');
const selectedGroupId = ref<string | null>(null);

const searchText = ref('');
const analyzingRecordings = ref<Set<string>>(new Set());
const expandedRecordings = ref<Set<string>>(new Set());

// Drop zone
const dragActive = ref(false);
const fileInputRef = ref<HTMLInputElement | null>(null);

// Drag and drop (recording → chip)
const dragOverGroupKey = ref<string | null>(null);

// Group creation
const showCreateGroupModal = ref(false);
const newGroupName = ref('');
// Group deletion
const deletingGroupId = ref<string | null>(null);
// Profile editing
const editingRecording = ref<Recording | null>(null);
const editProfileName = ref('');

watch(showCreateGroupModal, val => {
  if (val) {
    newGroupName.value = '';
  }
});

// --- Computeds ---

const groupCountsMap = computed<Map<string | null, GroupCounts>>(() => {
  const map = new Map<string | null, GroupCounts>();
  for (const recording of allRecordings.value) {
    const key = recording.groupId;
    let counts = map.get(key);
    if (!counts) {
      counts = { total: 0, jfr: 0, heap: 0 };
      map.set(key, counts);
    }
    counts.total++;
    if (recording.eventSource === HEAP_DUMP_SOURCE) {
      counts.heap++;
    } else {
      counts.jfr++;
    }
  }
  return map;
});

const ungroupedCount = computed(() => groupCountsMap.value.get(null)?.total ?? 0);

const sortedGroups = computed<RecordingGroup[]>(() => {
  const newestUpload = (groupId: string): number => {
    let max = 0;
    for (const r of allRecordings.value) {
      if (r.groupId === groupId && r.uploadedAt > max) {
        max = r.uploadedAt;
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

const activeFilterLabel = computed(() => {
  if (viewFilter.value === 'all') {
    return 'All';
  }
  if (viewFilter.value === UNGROUPED_KEY) {
    return 'Ungrouped';
  }
  return groupNameById.value.get(viewFilter.value) ?? 'Unknown';
});

const filteredRecordings = computed<Recording[]>(() => {
  let recs = allRecordings.value;

  if (viewFilter.value === UNGROUPED_KEY) {
    recs = recs.filter(r => r.groupId === null);
  } else if (viewFilter.value !== 'all') {
    const groupId = viewFilter.value;
    recs = recs.filter(r => r.groupId === groupId);
  }

  if (searchText.value) {
    const search = searchText.value.toLowerCase();
    recs = recs.filter(r => {
      const filename = r.filename.toLowerCase();
      const profile = (r.profileName ?? '').toLowerCase();
      return filename.includes(search) || profile.includes(search);
    });
  }
  return recs;
});

const sortRecordings = (recs: Recording[]): Recording[] => {
  return [...recs].sort((a, b) => {
    if (a.hasProfile !== b.hasProfile) {
      return a.hasProfile ? -1 : 1;
    }
    return b.uploadedAt - a.uploadedAt;
  });
};

const jfrRecordings = computed(() =>
  sortRecordings(filteredRecordings.value.filter(r => r.eventSource !== HEAP_DUMP_SOURCE))
);

const heapRecordings = computed(() =>
  sortRecordings(filteredRecordings.value.filter(r => r.eventSource === HEAP_DUMP_SOURCE))
);

// Origin breadcrumb derivation
interface RecordingOrigin {
  server: string;
  workspace: string;
  project: string;
}

const buildOrigin = (recording: Recording): RecordingOrigin | undefined => {
  const tags = recording.tags ?? [];
  const sys = Object.fromEntries(
    tags.filter(t => t.key.startsWith('origin.')).map(t => [t.key, t.value])
  );
  if (!sys['origin.server']) {
    return undefined;
  }
  return {
    server: sys['origin.server'],
    workspace: sys['origin.workspace'] ?? '',
    project: sys['origin.project'] ?? ''
  };
};

// --- Helpers ---

const getGroupCount = (groupId: string, kind: 'total' | 'jfr' | 'heap'): number => {
  return groupCountsMap.value.get(groupId)?.[kind] ?? 0;
};

const fileIconClass = (filename: string): string => {
  const lower = filename.toLowerCase();
  if (lower.endsWith('.hprof') || lower.endsWith('.hprof.gz')) {
    return 'bi bi-pie-chart-fill';
  }
  return 'bi bi-activity';
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

const handleFileInput = (event: Event) => {
  const input = event.target as HTMLInputElement;
  if (input.files && input.files.length > 0) {
    uploadFiles.value = [...uploadFiles.value, ...Array.from(input.files)];
  }
  input.value = '';
};

const handleDrop = (event: DragEvent) => {
  dragActive.value = false;
  const files = event.dataTransfer?.files;
  if (files && files.length > 0) {
    uploadFiles.value = [...uploadFiles.value, ...Array.from(files)];
  }
};

const removeFile = (index: number) => {
  uploadFiles.value.splice(index, 1);
};

const clearFiles = () => {
  uploadFiles.value = [];
  uploadProgress.value = {};
  errorMessage.value = null;
};

// --- Upload ---

const uploadRecordings = async () => {
  if (uploadFiles.value.length === 0) {
    return;
  }
  errorMessage.value = null;

  const newProgress: Record<string, UploadProgressEntry> = {};
  for (const file of uploadFiles.value) {
    newProgress[file.name] = { progress: 0, status: 'pending' };
  }
  uploadProgress.value = newProgress;

  const uploadPromises = uploadFiles.value.map(async file => {
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
      return { success: true };
    } catch {
      uploadProgress.value[file.name].status = 'error';
      return { success: false };
    }
  });

  const results = await Promise.all(uploadPromises);
  const successCount = results.filter(r => r.success).length;

  if (successCount === results.length) {
    setTimeout(() => {
      uploadFiles.value = [];
      uploadProgress.value = {};
    }, 1500);
  }

  await loadData();
};

// --- Recording actions ---

const analyzeRecording = async (recordingId: string) => {
  analyzingRecordings.value.add(recordingId);
  try {
    await recordingsClient.analyzeRecording(recordingId);
    analyzingRecordings.value.delete(recordingId);
    await loadData();
  } catch {
    analyzingRecordings.value.delete(recordingId);
  }
};

const handleCardClick = (recording: Recording) => {
  if (analyzingRecordings.value.has(recording.id)) {
    return;
  }
  if (recording.hasProfile) {
    openProfile(recording);
  } else {
    analyzeRecording(recording.id);
  }
};

const openProfile = async (recording: Recording) => {
  if (!recording.profileId) {
    return;
  }
  if (recording.eventSource === HEAP_DUMP_SOURCE) {
    await router.push(`/profiles/${recording.profileId}/heap-dump/settings`);
  } else {
    await router.push(`/profiles/${recording.profileId}/overview`);
  }
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

const startEditProfile = (recording: Recording) => {
  editingRecording.value = recording;
  editProfileName.value = recording.profileName ?? recording.filename;
};

const updateProfileName = async () => {
  if (!editingRecording.value || !editProfileName.value.trim()) {
    return;
  }
  try {
    await recordingsClient.updateProfileName(
      editingRecording.value.id,
      editProfileName.value.trim()
    );
    editingRecording.value = null;
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

const deleteProfileFromRecording = async (recordingId: string) => {
  try {
    await recordingsClient.deleteProfile(recordingId);
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
    const [recordings, groups] = await Promise.all([
      recordingsClient.listRecordings(),
      recordingsClient.listGroups()
    ]);
    allRecordings.value = recordings;
    allGroups.value = groups;
  } catch (error) {
    console.error('Failed to load data:', error);
  }
};

onMounted(() => {
  loadData();
});

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
  const recording = allRecordings.value.find(r => r.id === recordingId);
  if (!recording) {
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

const onDragEnd = () => {
  dragOverGroupKey.value = null;
};
</script>

<style scoped>
@import '@/styles/shared-components.css';

/* ============ Upload section ============ */
.upload-section {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-light);
  margin-bottom: 12px;
}

.drop-zone {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 24px 28px;
  background: var(--color-light);
  border: 1.5px dashed var(--color-border-input);
  border-radius: var(--radius-lg);
  transition:
    background var(--transition-base),
    border-color var(--transition-base),
    box-shadow var(--transition-base);
}

.drop-zone:hover,
.drop-zone.drag-over {
  border-color: var(--color-primary);
  background: var(--color-primary-lighter);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.file-input-hidden {
  display: none;
}

.drop-stack {
  display: flex;
  flex-shrink: 0;
}

.drop-ic {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  color: var(--color-white);
  border: 2px solid var(--color-white);
}

.drop-ic-jfr {
  background: var(--color-primary);
}

.drop-ic-heap {
  background: var(--color-purple);
  margin-left: -16px;
}

.drop-body {
  flex: 1;
  min-width: 0;
}

.drop-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
}

.drop-hint {
  font-size: var(--font-size-base);
  color: var(--color-text-muted);
  margin-top: 4px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}

.upload-target-hint {
  color: var(--color-text-muted);
}

.upload-target-hint strong {
  color: var(--color-dark);
  font-weight: var(--font-weight-semibold);
}

.browse-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--color-white);
  border: 1px solid var(--color-border-input);
  color: var(--color-primary);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  padding: 10px 16px;
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all var(--transition-base);
}

.browse-btn:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

/* ============ Queue panel ============ */
.queue-panel {
  margin-top: 10px;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}

.queue-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.queue-title {
  font-size: var(--font-size-sm);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.queue-title i {
  color: var(--color-primary);
}

.queue-target {
  color: var(--color-text-muted);
  margin-left: 4px;
}

.queue-target strong {
  color: var(--color-dark);
  font-weight: var(--font-weight-semibold);
}

.queue-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.queue-btn {
  border-radius: var(--radius-base);
  padding: 4px 11px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  transition: all var(--transition-base);
}

.queue-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.queue-btn-secondary {
  background: var(--color-white);
  border: 1px solid var(--color-border);
  color: var(--color-text);
}

.queue-btn-secondary:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.queue-btn-primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-white);
}

.queue-btn-primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}

.queue-item:hover {
  background: var(--color-bg-hover-alt);
}

.queue-item-icon {
  font-size: 0.85rem;
  color: var(--color-primary);
  flex-shrink: 0;
}

.queue-item-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.queue-item-size {
  font-size: var(--font-size-xs);
  color: var(--color-text-light);
  flex-shrink: 0;
}

.queue-item-remove {
  background: transparent;
  border: 0;
  color: var(--color-text-light);
  cursor: pointer;
  padding: 2px 4px;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  flex-shrink: 0;
  transition: all var(--transition-base);
}

.queue-item-remove:hover {
  color: var(--color-danger-hover);
  background: var(--color-danger-light);
}

.progress-track {
  width: 80px;
  height: 4px;
  background: var(--color-border);
  border-radius: 2px;
  overflow: hidden;
  flex-shrink: 0;
}

.progress-fill {
  height: 100%;
  border-radius: 2px;
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
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  width: 46px;
  text-align: right;
  flex-shrink: 0;
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
  margin-bottom: 12px;
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
  margin-left: auto;
  transition: all var(--transition-base);
}

.new-group-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* ============ Recordings columns ============ */
.recordings-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 12px;
}

.rec-column {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.col-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--color-border-light);
}

.col-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: var(--radius-base);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-white);
}

.col-pill-jfr {
  background: var(--color-primary);
}

.col-pill-heap {
  background: var(--color-purple);
}

.col-context {
  margin-left: auto;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.col-context strong {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.card-stack {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.col-empty {
  padding: 24px 0;
  text-align: center;
  color: var(--color-text-light);
  font-size: var(--font-size-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.col-empty i {
  font-size: 1.4rem;
  opacity: 0.4;
}

@media (max-width: 960px) {
  .recordings-columns {
    grid-template-columns: 1fr;
  }
}
</style>
