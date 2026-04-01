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
        <MainCardHeader icon="bi bi-lightning-charge" title="Quick Analysis" :badge="allRecordings.length">
          <template #actions>
            <div v-if="allRecordings.length > 0" class="page-search">
              <i class="bi bi-search"></i>
              <input v-model="searchText" type="text" placeholder="Search...">
            </div>
            <button class="page-header-btn" @click="showCreateGroupModal = true">
              <i class="bi bi-folder-plus"></i>
              New Group
            </button>
          </template>
        </MainCardHeader>
      </template>

      <!-- Upload panel -->
      <div class="qa-upload-section">
        <FileUploadPanel
            :files="uploadFiles"
            :progress="uploadProgress"
            :groups="allGroups"
            :selected-group-id="selectedGroupId"
            :is-uploading="isUploading"
            @update:files="uploadFiles = $event"
            @update:selected-group-id="selectedGroupId = $event"
            @upload="uploadRecordings"
            @clear="clearFiles"
            @remove="removeFile"
        />
      </div>

      <!-- Error message -->
      <div v-if="errorMessage" class="qa-error">
        <i class="bi bi-exclamation-triangle-fill"></i>
        {{ errorMessage }}
      </div>

      <!-- Recording list -->
      <div class="qa-profiles">
        <div v-if="groupedSections.length > 0" class="qa-profile-list">
          <template v-for="section in groupedSections" :key="section.id">
            <div class="qa-group">
              <div class="recording-group-header"
                  :class="{ 'recording-group-drop-target': dragOverGroupId === (section.id ?? '__ungrouped__') }"
                  @click="section.collapsed = !section.collapsed"
                  @dragover="onDragOver($event, section.id)"
                  @dragleave="onDragLeave($event, section.id)"
                  @drop="onDrop($event, section.id)">
                <i :class="section.collapsed ? 'bi bi-chevron-right' : 'bi bi-chevron-down'" class="recording-group-chevron"></i>
                <span class="recording-group-name">{{ section.name }}</span>
                <span class="recording-group-count">{{ section.recordings.length }}</span>
                <div v-if="section.id" class="recording-group-actions" @click.stop>
                  <button class="recording-group-action-btn recording-group-action-delete" @click="confirmDeleteGroup(section.id)" title="Delete group">
                    <i class="bi bi-trash"></i>
                  </button>
                </div>
              </div>
              <div v-if="!section.collapsed && section.recordings.length === 0" class="recording-group-empty">
                <span>No recordings</span>
              </div>
              <div v-if="!section.collapsed && section.recordings.length > 0" class="recording-group-items">
                <RecordingCard
                    v-for="recording in section.recordings"
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
                    @click="handleCardClick(recording)"
                    @create-profile="analyzeRecording(recording.id)"
                    @open-profile="openProfile(recording)"
                    @edit-profile="startEditProfile(recording)"
                    @delete-profile="deleteProfileFromRecording(recording.id)"
                    @delete-recording="deleteRecording(recording.id)"
                    @dragend="onDragEnd"
                />
              </div>
            </div>
          </template>
        </div>

        <!-- Empty state -->
        <EmptyState
            v-else-if="allRecordings.length === 0 && allGroups.length === 0"
            icon="bi-lightning-charge"
            title="No recordings yet"
            description="Drop a JFR or heap dump file above to get started"
        />

        <!-- No filter matches -->
        <EmptyState
            v-else
            icon="bi-search"
            title="No recordings match your search"
        />
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
import { computed, onMounted, ref, reactive, watch } from 'vue';
import { useRouter } from 'vue-router';
import QuickAnalysisClient from '@/services/api/QuickAnalysisClient';
import FileUploadPanel from '@/components/FileUploadPanel.vue';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import RecordingCard from '@/components/RecordingCard.vue';
import EditNameModal from '@/components/EditNameModal.vue';
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import EmptyState from '@/components/EmptyState.vue';
import type QuickGroup from '@/services/api/model/QuickGroup';
import type QuickRecording from '@/services/api/model/QuickRecording';

interface GroupSection {
  id: string | null;
  name: string;
  recordings: QuickRecording[];
  collapsed: boolean;
}

const router = useRouter();
const quickAnalysisClient = new QuickAnalysisClient();

interface UploadProgressEntry {
  progress: number;
  status: 'pending' | 'uploading' | 'complete' | 'error';
}

// State
const uploadFiles = ref<File[]>([]);
const uploadProgress = ref<Record<string, UploadProgressEntry>>({});
const isUploading = computed(() => Object.values(uploadProgress.value).some(p => p.status === 'uploading'));
const errorMessage = ref<string | null>(null);
const allRecordings = ref<QuickRecording[]>([]);
const allGroups = ref<QuickGroup[]>([]);
const selectedGroupId = ref<string | null>(null);
const searchText = ref('');
const analyzingRecordings = ref<Set<string>>(new Set());

// Group creation
const showCreateGroupModal = ref(false);
const newGroupName = ref('');
// Group deletion
const deletingGroupId = ref<string | null>(null);

// Profile editing
const editingRecording = ref<QuickRecording | null>(null);
const editProfileName = ref('');

// Reset group name when modal opens
watch(showCreateGroupModal, (val) => {
  if (val) {
    newGroupName.value = '';
  }
});

// Computed
const filteredRecordings = computed(() => {
  if (!searchText.value) {
    return allRecordings.value;
  }
  const search = searchText.value.toLowerCase();
  return allRecordings.value.filter(r => r.filename.toLowerCase().includes(search));
});

const groupedSections = computed(() => {
  const groupMap = new Map<string | null, QuickRecording[]>();

  for (const recording of filteredRecordings.value) {
    const key = recording.groupId;
    if (!groupMap.has(key)) {
      groupMap.set(key, []);
    }
    groupMap.get(key)!.push(recording);
  }

  const groupNameMap = new Map<string, string>();
  for (const group of allGroups.value) {
    groupNameMap.set(group.id, group.name);
  }

  const sections: GroupSection[] = [];

  // Named groups first (sorted by newest recording, then by name)
  const newestUpload = (groupId: string) => {
    const recs = groupMap.get(groupId);
    if (!recs || recs.length === 0) return 0;
    return Math.max(...recs.map(r => r.uploadedAt));
  };
  const groupIds = [...groupMap.keys()]
      .filter((k): k is string => k !== null)
      .sort((a, b) => newestUpload(b) - newestUpload(a));

  for (const groupId of groupIds) {
    sections.push(reactive({
      id: groupId,
      name: groupNameMap.get(groupId) || 'Unknown Group',
      recordings: groupMap.get(groupId)!.sort((a, b) => {
          if (a.hasProfile !== b.hasProfile) return a.hasProfile ? -1 : 1;
          return b.uploadedAt - a.uploadedAt;
        }),
      collapsed: false,
    }));
  }

  // Empty groups (no recordings but still exist)
  for (const group of allGroups.value) {
    if (!groupMap.has(group.id)) {
      sections.push(reactive({
        id: group.id,
        name: group.name,
        recordings: [],
        collapsed: false,
      }));
    }
  }

  // Ungrouped last
  if (groupMap.has(null)) {
    sections.push(reactive({
      id: null,
      name: 'Ungrouped',
      recordings: groupMap.get(null)!.sort((a, b) => {
          if (a.hasProfile !== b.hasProfile) return a.hasProfile ? -1 : 1;
          return b.uploadedAt - a.uploadedAt;
        }),
      collapsed: false,
    }));
  }

  return sections;
});

// File handling
const removeFile = (index: number) => {
  uploadFiles.value.splice(index, 1);
};

const clearFiles = () => {
  uploadFiles.value = [];
  uploadProgress.value = {};
  errorMessage.value = null;
};

// Upload recordings (parallel)
const uploadRecordings = async () => {
  if (uploadFiles.value.length === 0) return;
  errorMessage.value = null;

  // Initialize progress for all files
  const newProgress: Record<string, UploadProgressEntry> = {};
  for (const file of uploadFiles.value) {
    newProgress[file.name] = { progress: 0, status: 'pending' };
  }
  uploadProgress.value = newProgress;

  const uploadPromises = uploadFiles.value.map(async (file) => {
    try {
      uploadProgress.value[file.name].status = 'uploading';

      const progressInterval = setInterval(() => {
        if (uploadProgress.value[file.name]?.progress < 90) {
          uploadProgress.value[file.name].progress += Math.floor(Math.random() * 10) + 5;
        }
      }, 300);

      await quickAnalysisClient.uploadRecording(file, selectedGroupId.value || undefined);

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

// Analyze recording
const analyzeRecording = async (recordingId: string) => {
  analyzingRecordings.value.add(recordingId);

  try {
    await quickAnalysisClient.analyzeRecording(recordingId);
    analyzingRecordings.value.delete(recordingId);
    await loadData();
  } catch {
    analyzingRecordings.value.delete(recordingId);
    // Toast shown by HttpInterceptor
  }
};

// Card click: analyze if not analyzed, open profile if analyzed
const handleCardClick = (recording: QuickRecording) => {
  if (analyzingRecordings.value.has(recording.id)) return;
  if (recording.hasProfile) {
    openProfile(recording);
  } else {
    analyzeRecording(recording.id);
  }
};

// Open profile
const openProfile = async (recording: QuickRecording) => {
  if (!recording.profileId) return;
  if (recording.eventSource === 'HEAP_DUMP') {
    await router.push(`/profiles/${recording.profileId}/heap-dump/settings`);
  } else {
    await router.push(`/profiles/${recording.profileId}/overview`);
  }
};

// Group CRUD
const createGroup = async () => {
  const name = newGroupName.value.trim();
  if (!name) return;

  try {
    await quickAnalysisClient.createGroup(name);
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
  if (!deletingGroupId.value) return;
  try {
    if (selectedGroupId.value === deletingGroupId.value) {
      selectedGroupId.value = null;
    }
    await quickAnalysisClient.deleteGroup(deletingGroupId.value);
    deletingGroupId.value = null;
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

// Profile edit
const startEditProfile = (recording: QuickRecording) => {
  editingRecording.value = recording;
  editProfileName.value = recording.profileName ?? recording.filename;
};

const updateProfileName = async () => {
  if (!editingRecording.value || !editProfileName.value.trim()) return;
  try {
    await quickAnalysisClient.updateProfileName(editingRecording.value.id, editProfileName.value.trim());
    editingRecording.value = null;
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

// Profile delete (keeps recording)
const deleteProfileFromRecording = async (recordingId: string) => {
  try {
    await quickAnalysisClient.deleteProfile(recordingId);
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

// Delete recording
const deleteRecording = async (recordingId: string) => {
  try {
    await quickAnalysisClient.deleteRecording(recordingId);
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

// Load data
const loadData = async () => {
  try {
    const [recordings, groups] = await Promise.all([
      quickAnalysisClient.listRecordings(),
      quickAnalysisClient.listGroups(),
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

// --- Drag and Drop ---
const dragOverGroupId = ref<string | null>(null);

const onDragOver = (event: DragEvent, groupId: string | null) => {
  event.preventDefault();
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
  dragOverGroupId.value = groupId ?? '__ungrouped__';
};

const onDragLeave = (event: DragEvent, groupId: string | null) => {
  const related = event.relatedTarget as HTMLElement | null;
  const current = event.currentTarget as HTMLElement;
  if (!related || !current.contains(related)) {
    const key = groupId ?? '__ungrouped__';
    if (dragOverGroupId.value === key) dragOverGroupId.value = null;
  }
};

const onDrop = async (event: DragEvent, targetGroupId: string | null) => {
  event.preventDefault();
  dragOverGroupId.value = null;
  const recordingId = event.dataTransfer?.getData('text/plain');
  if (!recordingId) return;
  const recording = allRecordings.value.find(r => r.id === recordingId);
  if (!recording) return;
  if ((recording.groupId || null) === targetGroupId) return;
  recording.groupId = targetGroupId; // optimistic update
  try {
    await quickAnalysisClient.moveRecordingToGroup(recordingId, targetGroupId);
  } catch {
    await loadData();
  }
};

const onDragEnd = () => {
  dragOverGroupId.value = null;
};
</script>

<style scoped>
@import '@/styles/shared-components.css';


/* Upload section */
.qa-upload-section {
  border-bottom: 1px solid rgba(94, 100, 255, 0.06);
}

/* Error */
.qa-error {
  margin-bottom: 8px;
  padding: 8px 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: var(--color-danger-hover);
  font-size: 0.78rem;
  display: flex;
  align-items: center;
  gap: 6px;
}

.qa-profile-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* Recording cards - group styles are in shared-components.css */
</style>
