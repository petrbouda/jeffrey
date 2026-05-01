<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import ProjectRecordingClient from '@/services/api/ProjectRecordingClient';
import ProjectRecordingGroupClient from '@/services/api/ProjectRecordingGroupClient';
import { ToastService } from '@/services/ToastService';
import Recording from '@/services/api/model/Recording.ts';
import RecordingGroup from '@/services/api/model/RecordingGroup.ts';
import ProjectProfileClient from '@/services/api/ProjectProfileClient.ts';
import SecondaryProfileService from '@/services/SecondaryProfileService.ts';
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import RecordingCard from '@/components/RecordingCard.vue';
import RecordingFileGroupList from '@/components/RecordingFileGroupList.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import GenericModal from '@/components/GenericModal.vue';
import EditNameModal from '@/components/EditNameModal.vue';
import '@/styles/shared-components.css';

interface GroupSection {
  id: string | null;
  name: string;
  recordings: Recording[];
  collapsed: boolean;
}

const toast = ToastService;
const router = useRouter();
const { serverId, workspaceId, projectId, generateProfileUrl } = useNavigation();

const recordings = ref<Recording[]>([]);
const groups = ref<RecordingGroup[]>([]);
const loading = ref(true);
const searchText = ref('');

const deleteRecordingDialog = ref(false);
const recordingToDelete = ref<Recording | null>();
const deleteGroupDialog = ref(false);
const groupToDelete = ref<RecordingGroup | null>();

const showCreateGroupModal = ref(false);
const newGroupName = ref('');
const createGroupErrors = ref<string[]>([]);

const editingRecording = ref<Recording | null>(null);
const editProfileName = ref('');

const expandedRecordingFiles = ref<Set<string>>(new Set());
const profileCreationStates = ref<Map<string, boolean>>(new Map());
const pollInterval = ref<number | null>(null);

let projectProfileClient: ProjectProfileClient;
let projectRecordingClient: ProjectRecordingClient;
let projectRecordingGroupClient: ProjectRecordingGroupClient;

// --- Deleting-profile state persisted per project ---
const DELETING_PROFILES_KEY = computed(
  () => `deleting_profiles_${workspaceId.value}_${projectId.value}`
);

const getDeletingProfiles = (): Set<string> => {
  const stored = sessionStorage.getItem(DELETING_PROFILES_KEY.value);
  return stored ? new Set(JSON.parse(stored)) : new Set();
};

const addDeletingProfile = (profileId: string) => {
  const profiles = getDeletingProfiles();
  profiles.add(profileId);
  sessionStorage.setItem(DELETING_PROFILES_KEY.value, JSON.stringify(Array.from(profiles)));
};

const removeDeletingProfile = (profileId: string) => {
  const profiles = getDeletingProfiles();
  profiles.delete(profileId);
  sessionStorage.setItem(DELETING_PROFILES_KEY.value, JSON.stringify(Array.from(profiles)));
};

// --- Lifecycle ---
onMounted(async () => {
  if (!workspaceId.value || !projectId.value) return;

  projectProfileClient = new ProjectProfileClient(serverId.value, workspaceId.value, projectId.value);
  projectRecordingClient = new ProjectRecordingClient(serverId.value, workspaceId.value, projectId.value);
  projectRecordingGroupClient = new ProjectRecordingGroupClient(serverId.value, workspaceId.value, projectId.value);

  await loadData();

  if (hasInitializingOrDeletingProfiles()) {
    startPolling();
  }
});

onUnmounted(() => {
  stopPolling();
});

const hasInitializingOrDeletingProfiles = (): boolean => {
  const hasInitializing = recordings.value.some(r => r.hasProfile && !r.profileEnabled);
  const hasDeleting = getDeletingProfiles().size > 0;
  return hasInitializing || hasDeleting;
};

// --- Data loading ---
const loadData = async () => {
  const isInitialLoad = recordings.value.length === 0 && groups.value.length === 0;
  if (isInitialLoad) {
    loading.value = true;
  }
  try {
    const [recordingsData, groupsData] = await Promise.all([
      projectRecordingClient.list(),
      projectRecordingGroupClient.list()
    ]);

    const deletingProfiles = getDeletingProfiles();
    recordingsData.forEach(recording => {
      if (recording.profileId && deletingProfiles.has(recording.profileId)) {
        (recording as any)._profileDeleting = true;
      }
    });

    recordings.value = recordingsData;
    groups.value = groupsData;
  } catch (error: any) {
    toast.error('Failed to load data', error.message);
  } finally {
    loading.value = false;
  }
};

// --- Search + section organization ---
const filteredRecordings = computed(() => {
  if (!searchText.value) return recordings.value;
  const search = searchText.value.toLowerCase();
  return recordings.value.filter(
    r =>
      r.name.toLowerCase().includes(search) ||
      (r.profileName ?? '').toLowerCase().includes(search)
  );
});

const groupedSections = computed<GroupSection[]>(() => {
  const groupMap = new Map<string | null, Recording[]>();
  const validGroupIds = new Set(groups.value.map(g => g.id));

  for (const recording of filteredRecordings.value) {
    const rawKey = recording.groupId ?? null;
    const key = rawKey && validGroupIds.has(rawKey) ? rawKey : null;
    if (!groupMap.has(key)) groupMap.set(key, []);
    groupMap.get(key)!.push(recording);
  }

  const groupNameMap = new Map<string, string>();
  for (const group of groups.value) {
    groupNameMap.set(group.id, group.name);
  }

  const sections: GroupSection[] = [];

  const newestUpload = (groupId: string) => {
    const recs = groupMap.get(groupId);
    if (!recs || recs.length === 0) return 0;
    return Math.max(...recs.map(r => new Date(r.uploadedAt).getTime()));
  };

  const sortRecordings = (recs: Recording[]) =>
    recs.sort((a, b) => {
      if (a.hasProfile !== b.hasProfile) return a.hasProfile ? -1 : 1;
      return new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime();
    });

  // Named groups first, sorted by newest recording
  const groupIds = [...groupMap.keys()]
    .filter((k): k is string => k !== null)
    .sort((a, b) => newestUpload(b) - newestUpload(a));

  for (const groupId of groupIds) {
    sections.push(
      reactive({
        id: groupId,
        name: groupNameMap.get(groupId) || 'Unknown Group',
        recordings: sortRecordings(groupMap.get(groupId)!),
        collapsed: false
      })
    );
  }

  // Empty groups (no recordings, but still exist)
  for (const group of groups.value) {
    if (!groupMap.has(group.id)) {
      sections.push(
        reactive({
          id: group.id,
          name: group.name,
          recordings: [],
          collapsed: false
        })
      );
    }
  }

  // Ungrouped last
  if (groupMap.has(null)) {
    sections.push(
      reactive({
        id: null,
        name: 'Ungrouped',
        recordings: sortRecordings(groupMap.get(null)!),
        collapsed: false
      })
    );
  }

  return sections;
});

// --- Recording file expansion ---
const toggleRecordingFiles = (recording: Recording) => {
  if (expandedRecordingFiles.value.has(recording.id)) {
    expandedRecordingFiles.value.delete(recording.id);
  } else {
    expandedRecordingFiles.value.add(recording.id);
  }
};

const downloadFile = async (recordingId: string, fileId: string) => {
  try {
    await projectRecordingClient.downloadFile(recordingId, fileId);
  } catch (error: any) {
    toast.error('Failed to download file', error.message);
  }
};

// --- Profile actions ---
const isProfileDeleting = (recording: Recording): boolean => {
  if (!recording.profileId) return false;
  return (
    getDeletingProfiles().has(recording.profileId) || (recording as any)._profileDeleting === true
  );
};

const isRecordingCreatingProfile = (recordingId: string): boolean => {
  return profileCreationStates.value.get(recordingId) || false;
};

const selectProfile = () => {
  SecondaryProfileService.remove();
  sessionStorage.removeItem('profile-sidebar-mode');
};

const createProfile = async (recording: Recording) => {
  if (profileCreationStates.value.get(recording.id) || recording.hasProfile) return;

  profileCreationStates.value.set(recording.id, true);
  recording.hasProfile = true;

  try {
    await projectProfileClient.create(recording.id);
    await loadData();

    const updatedRecording = recordings.value.find(r => r.id === recording.id);
    if (updatedRecording) {
      updatedRecording.hasProfile = true;
    }

    startPolling();
  } catch (error: any) {
    recording.hasProfile = false;
    toast.error('Profile Creation Failed', error.message);
  } finally {
    profileCreationStates.value.delete(recording.id);
  }
};

const editProfile = (recording: Recording) => {
  if (!recording.profileId || !recording.profileName) return;
  editingRecording.value = recording;
  editProfileName.value = recording.profileName;
};

const updateProfile = async () => {
  if (!editingRecording.value || !editProfileName.value.trim()) return;

  try {
    await projectProfileClient.update(
      editingRecording.value.profileId!,
      editProfileName.value.trim()
    );
    editingRecording.value = null;
    await loadData();
  } catch {
    // Toast shown by HttpInterceptor
  }
};

const deleteProfile = (recording: Recording) => {
  if (!recording.profileId) return;

  const profileId = recording.profileId;
  const profileName = recording.profileName || recording.name;

  addDeletingProfile(profileId);
  (recording as any)._profileDeleting = true;

  startPolling();

  projectProfileClient
    .delete(profileId)
    .then(() => {
      removeDeletingProfile(profileId);
      loadData();
    })
    .catch(error => {
      console.error('Failed to delete profile:', error);
      toast.error('Delete Profile', 'Failed to delete profile: ' + profileName);
      removeDeletingProfile(profileId);
      loadData();
    });
};

// --- Polling ---
const startPolling = () => {
  if (pollInterval.value !== null) return;

  pollInterval.value = window.setInterval(async () => {
    try {
      const profiles = await projectProfileClient.list();
      const profileByRecordingId = new Map<
        string,
        { id: string; name: string; enabled: boolean; sizeInBytes: number }
      >();
      const profileIds = new Set<string>();
      for (const p of profiles) {
        profileIds.add(p.id);
      }

      const deletingProfiles = getDeletingProfiles();
      let deletionCompleted = false;
      for (const profileId of deletingProfiles) {
        if (!profileIds.has(profileId)) {
          removeDeletingProfile(profileId);
          deletionCompleted = true;
        }
      }

      for (const p of profiles) {
        const rec = recordings.value.find(r => r.profileId === p.id);
        if (rec) {
          profileByRecordingId.set(rec.id, {
            id: p.id,
            name: p.name,
            enabled: p.enabled,
            sizeInBytes: p.sizeInBytes
          });
        }
      }

      let initializationCompleted = false;
      for (const rec of recordings.value) {
        const profile = profileByRecordingId.get(rec.id);
        if (profile && rec.hasProfile && !rec.profileEnabled && profile.enabled) {
          rec.profileEnabled = true;
          rec.profileName = profile.name;
          rec.profileSizeInBytes = profile.sizeInBytes;
          initializationCompleted = true;
        }
        if (rec.profileId && !profileIds.has(rec.profileId) && (rec as any)._profileDeleting) {
          rec.hasProfile = false;
          rec.profileId = null;
          rec.profileName = null;
          rec.profileEnabled = undefined;
          rec.profileSizeInBytes = undefined;
          (rec as any)._profileDeleting = false;
        }
      }

      if (!hasInitializingOrDeletingProfiles()) {
        stopPolling();
        if (initializationCompleted || deletionCompleted) {
          await loadData();
        }
      }
    } catch (error) {
      console.error('Error while polling profiles:', error);
    }
  }, 5000) as unknown as number;
};

const stopPolling = () => {
  if (pollInterval.value !== null) {
    window.clearInterval(pollInterval.value);
    pollInterval.value = null;
  }
};

// --- Recording actions ---
const confirmDeleteRecording = (recording: Recording) => {
  recordingToDelete.value = recording;
  deleteRecordingDialog.value = true;
};

const deleteRecording = async () => {
  if (!recordingToDelete.value) return;

  try {
    await projectRecordingClient.delete(recordingToDelete.value.id);
    await loadData();
    deleteRecordingDialog.value = false;
    recordingToDelete.value = null;
  } catch (error: any) {
    toast.error('Delete Failed', error.message);
  }
};

// --- Group actions ---
const confirmDeleteGroup = (groupId: string) => {
  const group = groups.value.find(g => g.id === groupId);
  if (!group) return;
  groupToDelete.value = group;
  deleteGroupDialog.value = true;
};

const deleteGroup = async () => {
  if (!groupToDelete.value) return;

  try {
    await projectRecordingGroupClient.delete(groupToDelete.value.id);
    await loadData();
    deleteGroupDialog.value = false;
    groupToDelete.value = null;
  } catch (error: any) {
    toast.error('Delete Failed', error.message);
  }
};

const createGroup = async () => {
  if (!newGroupName.value.trim()) {
    createGroupErrors.value = ['Group name cannot be empty'];
    return;
  }

  try {
    await projectRecordingGroupClient.create(newGroupName.value.trim());
    newGroupName.value = '';
    showCreateGroupModal.value = false;
    createGroupErrors.value = [];
    await loadData();
  } catch (error: any) {
    createGroupErrors.value = [error.message || 'Failed to create group'];
  }
};

const openCreateGroupDialog = () => {
  newGroupName.value = '';
  createGroupErrors.value = [];
  showCreateGroupModal.value = true;
};

const handleRecordingCardClick = (recording: Recording) => {
  if (isProfileDeleting(recording) || isRecordingCreatingProfile(recording.id)) return;
  if (recording.hasProfile && recording.profileEnabled) {
    navigateToProfile(recording);
  } else if (!recording.hasProfile) {
    createProfile(recording);
  }
};

const navigateToProfile = (recording: Recording) => {
  if (!recording.profileId) return;
  selectProfile();
  router.push(generateProfileUrl('overview', recording.profileId));
};

// --- Drag and drop ---
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
  const recording = recordings.value.find(r => r.id === recordingId);
  if (!recording) return;
  if ((recording.groupId || null) === targetGroupId) return;
  recording.groupId = targetGroupId;
  try {
    await projectRecordingClient.moveToGroup(recordingId, targetGroupId);
  } catch (error: any) {
    toast.error('Move Failed', error.message);
    await loadData();
  }
};

const onDragEnd = () => {
  dragOverGroupId.value = null;
};
</script>

<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader
          icon="bi bi-record-circle"
          title="Recordings"
        >
          <template #actions>
            <div v-if="recordings.length > 0" class="page-search">
              <i class="bi bi-search"></i>
              <input v-model="searchText" type="text" placeholder="Search..." />
            </div>
            <button class="page-header-btn" @click="openCreateGroupDialog">
              <i class="bi bi-folder-plus"></i>
              New Group
            </button>
          </template>
        </MainCardHeader>
      </template>

      <!-- Loading -->
      <LoadingState v-if="loading" message="Loading recordings..." />

      <!-- Empty state: no recordings, no groups -->
      <EmptyState
        v-else-if="recordings.length === 0 && groups.length === 0"
        icon="bi-folder-x"
        title="No Recordings Available"
        description="Recordings from sessions will appear here."
      />

      <!-- Recording list -->
      <div v-else class="qa-profiles">
        <div v-if="groupedSections.length > 0" class="qa-profile-list">
          <template v-for="section in groupedSections" :key="section.id ?? '__ungrouped__'">
            <div class="qa-group">
              <div
                class="recording-group-header"
                :class="{
                  'recording-group-drop-target':
                    dragOverGroupId === (section.id ?? '__ungrouped__')
                }"
                @click="section.collapsed = !section.collapsed"
                @dragover="onDragOver($event, section.id)"
                @dragleave="onDragLeave($event, section.id)"
                @drop="onDrop($event, section.id)"
              >
                <i
                  :class="section.collapsed ? 'bi bi-chevron-right' : 'bi bi-chevron-down'"
                  class="recording-group-chevron"
                ></i>
                <span class="recording-group-name">{{ section.name }}</span>
                <span class="recording-group-count">{{ section.recordings.length }}</span>
                <div v-if="section.id" class="recording-group-actions" @click.stop>
                  <button
                    class="recording-group-action-btn recording-group-action-delete"
                    @click="confirmDeleteGroup(section.id)"
                    title="Delete group and all its recordings"
                  >
                    <i class="bi bi-trash"></i>
                  </button>
                </div>
              </div>

              <div
                v-if="!section.collapsed && section.recordings.length === 0"
                class="recording-group-empty"
              >
                <span>No recordings</span>
              </div>

              <div
                v-if="!section.collapsed && section.recordings.length > 0"
                class="recording-group-items"
              >
                <RecordingCard
                  v-for="recording in section.recordings"
                  :key="`recording-${recording.id}`"
                  :recording-id="recording.id"
                  :name="recording.profileName || recording.name"
                  :size-in-bytes="recording.sizeInBytes"
                  :duration-in-millis="recording.durationInMillis"
                  :uploaded-at="recording.uploadedAt"
                  :source-type="recording.sourceType"
                  :has-profile="!!recording.hasProfile"
                  :profile-id="recording.profileId"
                  :profile-enabled="recording.profileEnabled ?? true"
                  :profile-size-in-bytes="recording.profileSizeInBytes"
                  :profile-modified="recording.profileModified"
                  :file-count="recording.recordingFiles.length"
                  :creating-profile="isRecordingCreatingProfile(recording.id)"
                  :deleting-profile="isProfileDeleting(recording)"
                  :expandable="true"
                  :expanded="expandedRecordingFiles.has(recording.id)"
                  :draggable="true"
                  @click="handleRecordingCardClick(recording)"
                  @create-profile="createProfile(recording)"
                  @open-profile="navigateToProfile(recording)"
                  @edit-profile="editProfile(recording)"
                  @delete-profile="deleteProfile(recording)"
                  @toggle-expand="toggleRecordingFiles(recording)"
                  @delete-recording="confirmDeleteRecording(recording)"
                  @dragend="onDragEnd"
                >
                  <template #expanded-content>
                    <RecordingFileGroupList
                      v-if="recording.recordingFiles && recording.recordingFiles.length > 0"
                      :recording-id="recording.id"
                      :files="recording.recordingFiles"
                      @download="downloadFile"
                    />
                    <div v-else class="small py-1 text-muted">
                      <i class="bi bi-exclamation-circle me-1"></i>
                      No recording files available
                    </div>
                  </template>
                </RecordingCard>
              </div>
            </div>
          </template>
        </div>

        <!-- No filter matches -->
        <EmptyState v-else icon="bi-search" title="No recordings match your search" />
      </div>
    </MainCard>

    <!-- Delete Recording Confirmation -->
    <ConfirmationDialog
      v-model:show="deleteRecordingDialog"
      title="Confirm Delete"
      :message="
        recordingToDelete
          ? `Are you sure you want to delete the recording: ${recordingToDelete.name}?`
          : 'Are you sure you want to delete this recording?'
      "
      sub-message="This action cannot be undone."
      confirm-label="Delete"
      confirm-button-class="btn-danger"
      confirm-button-id="deleteRecordingButton"
      modal-id="deleteRecordingModal"
      @confirm="deleteRecording"
    />

    <!-- Delete Group Confirmation -->
    <ConfirmationDialog
      v-model:show="deleteGroupDialog"
      title="Confirm Delete Group"
      :message="
        groupToDelete
          ? `Are you sure you want to delete the group: ${groupToDelete.name}?`
          : 'Are you sure you want to delete this group?'
      "
      sub-message="This will also delete all recordings within the group."
      confirm-label="Delete Group"
      confirm-button-class="btn-danger"
      confirm-button-id="deleteGroupButton"
      modal-id="deleteGroupModal"
      @confirm="deleteGroup"
    />

    <!-- Create Group Modal -->
    <GenericModal
      modal-id="createGroupModal"
      :show="showCreateGroupModal"
      @update:show="showCreateGroupModal = $event"
      title="Create New Group"
      icon="bi-folder-plus"
    >
      <div class="form-group">
        <label for="newGroupNameInput" class="form-label">Group Name</label>
        <input
          type="text"
          class="form-control"
          id="newGroupNameInput"
          v-model="newGroupName"
          placeholder="Enter group name"
        />
      </div>

      <div v-if="createGroupErrors.length > 0" class="alert alert-danger mt-3">
        <div v-for="(error, idx) in createGroupErrors" :key="idx">
          <i class="bi bi-exclamation-triangle-fill me-2"></i>{{ error }}
        </div>
      </div>

      <template #footer>
        <button type="button" class="btn btn-secondary" @click="showCreateGroupModal = false">
          Cancel
        </button>
        <button type="button" class="btn btn-primary" @click="createGroup">
          <i class="bi bi-folder-plus me-1"></i>
          Create
        </button>
      </template>
    </GenericModal>

    <!-- Edit Profile Modal -->
    <EditNameModal
      v-if="editingRecording"
      v-model="editProfileName"
      @submit="updateProfile"
      @close="editingRecording = null"
    />
  </div>
</template>

<style scoped>
@import '@/styles/shared-components.css';

.qa-profile-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
</style>
