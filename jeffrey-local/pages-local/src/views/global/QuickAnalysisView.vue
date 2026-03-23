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
    <div class="main-card mb-4">
      <!-- Header with integrated toolbar -->
      <div class="qa-header">
        <div class="qa-header-info">
          <i class="bi bi-lightning-charge qa-header-icon"></i>
          <span class="qa-header-title">Quick Analysis</span>
          <span v-if="allRecordings.length > 0" class="qa-count-badge">{{ allRecordings.length }}</span>
        </div>
        <div class="qa-header-actions">
          <div v-if="allRecordings.length > 0" class="qa-search">
            <i class="bi bi-search"></i>
            <input v-model="searchText" type="text" placeholder="Search...">
          </div>
          <button class="qa-new-group-btn" @click="showCreateGroupModal = true">
            <i class="bi bi-folder-plus"></i>
            New Group
          </button>
        </div>
      </div>

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
              <div class="qa-group-header" @click="section.collapsed = !section.collapsed">
                <i :class="section.collapsed ? 'bi bi-chevron-right' : 'bi bi-chevron-down'" class="qa-group-chevron"></i>
                <span class="qa-group-name">{{ section.name }}</span>
                <span class="qa-group-count">{{ section.recordings.length }}</span>
                <div v-if="section.id" class="qa-group-actions" @click.stop>
                  <button class="qa-action-btn qa-action-delete" @click="confirmDeleteGroup(section.id)" title="Delete group">
                    <i class="bi bi-trash"></i>
                  </button>
                </div>
              </div>
              <div v-if="!section.collapsed && section.recordings.length === 0" class="qa-group-empty">
                <span>No recordings</span>
              </div>
              <div v-if="!section.collapsed && section.recordings.length > 0" class="qa-group-items">
                <RecordingCard
                    v-for="recording in section.recordings"
                    :key="recording.id"
                    :recording-id="recording.id"
                    :name="recording.filename"
                    :size-in-bytes="recording.sizeInBytes"
                    :duration-in-millis="recording.durationInMillis"
                    :uploaded-at="recording.uploadedAt"
                    :source-type="recording.eventSource"
                    :has-profile="recording.hasProfile"
                    :profile-id="recording.profileId"
                    :profile-size-in-bytes="recording.profileSizeInBytes"
                    :analyzing="analyzingRecordings.has(recording.id)"
                    @click="handleCardClick(recording)"
                    @create-profile="analyzeRecording(recording.id)"
                    @open-profile="openProfile(recording)"
                    @edit-profile="startEditProfile(recording)"
                    @delete-profile="deleteProfileFromRecording(recording.id)"
                    @delete-recording="deleteRecording(recording.id)"
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
    </div>

    <!-- Create Group modal -->
    <div v-if="showCreateGroupModal" class="qa-modal-overlay" @click.self="showCreateGroupModal = false">
      <div class="qa-modal">
        <div class="qa-modal-header">
          <span class="qa-modal-title">New Group</span>
          <button class="qa-modal-close" @click="showCreateGroupModal = false">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>
        <div class="qa-modal-body">
          <input
              ref="groupNameInputRef"
              v-model="newGroupName"
              type="text"
              class="qa-modal-input"
              placeholder="Enter group name"
              @keydown.enter="createGroup"
              @keydown.escape="showCreateGroupModal = false"
          >
        </div>
        <div class="qa-modal-footer">
          <button class="qa-modal-btn-cancel" @click="showCreateGroupModal = false">Cancel</button>
          <button class="qa-modal-btn-save" @click="createGroup" :disabled="!newGroupName.trim()">Create</button>
        </div>
      </div>
    </div>

    <!-- Edit Profile modal -->
    <div v-if="editingRecording" class="qa-modal-overlay" @click.self="editingRecording = null">
      <div class="qa-modal">
        <div class="qa-modal-header">
          <span class="qa-modal-title">Edit Profile</span>
          <button class="qa-modal-close" @click="editingRecording = null">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>
        <div class="qa-modal-body">
          <input
              ref="editProfileInputRef"
              v-model="editProfileName"
              type="text"
              class="qa-modal-input"
              placeholder="Enter profile name"
              @keydown.enter="updateProfileName"
              @keydown.escape="editingRecording = null"
          >
        </div>
        <div class="qa-modal-footer">
          <button class="qa-modal-btn-cancel" @click="editingRecording = null">Cancel</button>
          <button class="qa-modal-btn-save" @click="updateProfileName" :disabled="!editProfileName.trim()">Update</button>
        </div>
      </div>
    </div>

    <!-- Delete Group confirmation -->
    <div v-if="deletingGroupId" class="qa-modal-overlay" @click.self="deletingGroupId = null">
      <div class="qa-modal">
        <div class="qa-modal-header">
          <span class="qa-modal-title">Delete Group</span>
          <button class="qa-modal-close" @click="deletingGroupId = null">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>
        <div class="qa-modal-body">
          <p class="qa-modal-warning">This will delete the group and all its recordings (including created profiles). This action cannot be undone.</p>
        </div>
        <div class="qa-modal-footer">
          <button class="qa-modal-btn-cancel" @click="deletingGroupId = null">Cancel</button>
          <button class="qa-modal-btn-danger" @click="deleteGroup">Delete</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, reactive, watch } from 'vue';
import { useRouter } from 'vue-router';
import QuickAnalysisClient from '@/services/api/QuickAnalysisClient';
import FileUploadPanel from '@/components/FileUploadPanel.vue';
import RecordingCard from '@/components/RecordingCard.vue';
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
const groupNameInputRef = ref<HTMLInputElement | null>(null);

// Group deletion
const deletingGroupId = ref<string | null>(null);

// Profile editing
const editingRecording = ref<QuickRecording | null>(null);
const editProfileName = ref('');
const editProfileInputRef = ref<HTMLInputElement | null>(null);

// Focus edit profile input when modal opens
watch(editingRecording, async (val) => {
  if (val) {
    await nextTick();
    editProfileInputRef.value?.focus();
  }
});

// Focus group name input when modal opens
watch(showCreateGroupModal, async (val) => {
  if (val) {
    newGroupName.value = '';
    await nextTick();
    groupNameInputRef.value?.focus();
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

  // Named groups first (sorted by name)
  const groupIds = [...groupMap.keys()]
      .filter((k): k is string => k !== null)
      .sort((a, b) => (groupNameMap.get(a) || '').localeCompare(groupNameMap.get(b) || ''));

  for (const groupId of groupIds) {
    sections.push(reactive({
      id: groupId,
      name: groupNameMap.get(groupId) || 'Unknown Group',
      recordings: groupMap.get(groupId)!,
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
      recordings: groupMap.get(null)!,
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
  editProfileName.value = recording.filename;
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
</script>

<style scoped>
@import '@/styles/shared-components.css';

/* Header */
.qa-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border-bottom: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px 16px 0 0;
  gap: 12px;
}

.qa-header-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.qa-header-icon {
  font-size: 1.1rem;
  color: #5e64ff;
}

.qa-header-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: #374151;
}

.qa-count-badge {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 0.7rem;
  font-weight: 600;
}

.qa-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.qa-search {
  display: flex;
  align-items: center;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 4px 10px;
  width: 160px;
  height: 30px;
  transition: all 0.15s ease;
}

.qa-search:focus-within {
  border-color: #5e64ff;
  box-shadow: 0 0 0 2px rgba(94, 100, 255, 0.1);
  background: white;
}

.qa-search i {
  font-size: 0.7rem;
  color: #9ca3af;
  margin-right: 6px;
}

.qa-search input {
  border: none;
  outline: none;
  background: transparent;
  font-size: 0.75rem;
  color: #374151;
  width: 100%;
}

.qa-new-group-btn {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
  border: none;
  padding: 5px 12px;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  transition: all 0.2s ease;
  height: 30px;
}

.qa-new-group-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.35);
}

/* Upload section */
.qa-upload-section {
  padding: 0 20px;
  border-bottom: 1px solid rgba(94, 100, 255, 0.06);
}

/* Error */
.qa-error {
  margin: 0 20px 8px;
  padding: 8px 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #dc2626;
  font-size: 0.78rem;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* Recording list */
.qa-profiles {
  padding: 8px 20px 16px;
}

.qa-profile-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* Group headers */
.qa-group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s ease;
}

.qa-group-header:hover {
  background: #f3f4f6;
}

.qa-group-chevron {
  font-size: 0.65rem;
  color: #9ca3af;
  transition: transform 0.15s ease;
}

.qa-group-name {
  font-size: 0.75rem;
  font-weight: 600;
  color: #374151;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.qa-group-count {
  font-size: 0.65rem;
  color: #9ca3af;
  background: #f3f4f6;
  padding: 0 6px;
  border-radius: 8px;
}

.qa-group-empty {
  padding: 8px 8px 8px 20px;
  font-size: 0.72rem;
  color: #b6c1d2;
  font-style: italic;
}

.qa-group-actions {
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.qa-group-header:hover .qa-group-actions {
  opacity: 1;
}

/* Recording cards */
.qa-group-items {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-left: 12px;
  margin-top: 6px;
  margin-bottom: 6px;
  animation: groupExpand 0.2s ease-out;
}

@keyframes groupExpand {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Modal */
.qa-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1050;
}

.qa-modal {
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  width: 380px;
  max-width: 90vw;
  animation: modalSlideIn 0.2s ease-out;
}

@keyframes modalSlideIn {
  from { transform: translateY(-8px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.qa-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #e5e7eb;
}

.qa-modal-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
}

.qa-modal-close {
  background: transparent;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  font-size: 0.8rem;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.qa-modal-close:hover {
  color: #374151;
  background: #f3f4f6;
}

.qa-modal-body {
  padding: 16px 18px;
}

.qa-modal-input {
  width: 100%;
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 0.8rem;
  transition: border-color 0.15s ease;
}

.qa-modal-input:focus {
  outline: none;
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
}

.qa-modal-warning {
  font-size: 0.8rem;
  color: #6b7280;
  margin: 0;
  line-height: 1.5;
}

.qa-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  padding: 10px 18px;
  border-top: 1px solid #e5e7eb;
}

.qa-modal-btn-cancel {
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  color: #374151;
  padding: 5px 14px;
  border-radius: 6px;
  font-size: 0.78rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.qa-modal-btn-cancel:hover {
  background: #e5e7eb;
}

.qa-modal-btn-save {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border: none;
  color: white;
  padding: 5px 14px;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.qa-modal-btn-save:hover {
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.4);
}

.qa-modal-btn-save:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.qa-modal-btn-danger {
  background: linear-gradient(135deg, #dc2626, #b91c1c);
  border: none;
  color: white;
  padding: 5px 14px;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.qa-modal-btn-danger:hover {
  box-shadow: 0 2px 8px rgba(220, 38, 38, 0.4);
}
</style>
