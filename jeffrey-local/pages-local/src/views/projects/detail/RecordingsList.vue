<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref} from 'vue';
import { useRouter } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import ProjectRecordingClient from '@/services/api/ProjectRecordingClient';
import ProjectRecordingGroupClient from '@/services/api/ProjectRecordingGroupClient';
import {ToastService} from '@/services/ToastService';
import Recording from "@/services/api/model/Recording.ts";
import RecordingGroup from "@/services/api/model/RecordingGroup.ts";
import ProjectProfileClient from "@/services/api/ProjectProfileClient.ts";
import SecondaryProfileService from "@/services/SecondaryProfileService.ts";
import MessageBus from "@/services/MessageBus";
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import RecordingCard from '@/components/RecordingCard.vue';
import RecordingFileGroupList from '@/components/RecordingFileGroupList.vue';
import SectionHeaderBar from '@/components/SectionHeaderBar.vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import BaseModal from '@/components/BaseModal.vue';
import EditNameModal from '@/components/EditNameModal.vue';
import '@/styles/shared-components.css';

const toast = ToastService;
const recordings = ref<Recording[]>([]);
const loading = ref(true);
const deleteRecordingDialog = ref(false);
const recordingToDelete = ref<Recording | null>();
const deleteGroupDialog = ref(false);
const groupToDelete = ref<RecordingGroup | null>();

// Services
let projectProfileClient: ProjectProfileClient;
let projectRecordingClient: ProjectRecordingClient;
let projectRecordingGroupClient: ProjectRecordingGroupClient;

// Track expanded groups
const expandedGroups = ref<Set<string>>(new Set());

// Track expanded recording files sections
const expandedRecordingFiles = ref<Set<string>>(new Set());

const router = useRouter();
const { workspaceId, projectId, generateProfileUrl } = useNavigation();

// --- Profile Management State ---
const editingRecording = ref<Recording | null>(null);
const editProfileName = ref('');
const pollInterval = ref<number | null>(null);

// Persistent storage for deleting profiles
const DELETING_PROFILES_KEY = computed(() => `deleting_profiles_${workspaceId.value}_${projectId.value}`);

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

// Track profile creation states for each recording
const profileCreationStates = ref<Map<string, boolean>>(new Map());

const groups = ref<RecordingGroup[]>([]);
const newGroupName = ref('');
const createGroupModal = ref<InstanceType<typeof BaseModal>>();

onMounted(async () => {
  if (!workspaceId.value || !projectId.value) return;

  projectProfileClient = new ProjectProfileClient(workspaceId.value, projectId.value);
  projectRecordingClient = new ProjectRecordingClient(workspaceId.value, projectId.value);
  projectRecordingGroupClient = new ProjectRecordingGroupClient(workspaceId.value, projectId.value);

  expandedGroups.value.add('root');

  await loadData();

  // Start polling if any profiles are initializing or deleting
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

// Toggle the recording files section
const toggleRecordingFiles = (recording: Recording) => {
  if (expandedRecordingFiles.value.has(recording.id)) {
    expandedRecordingFiles.value.delete(recording.id);
  } else {
    expandedRecordingFiles.value.add(recording.id);
  }
};

// Download a recording file
const downloadFile = async (recordingId: string, fileId: string) => {
  try {
    await projectRecordingClient.downloadFile(recordingId, fileId);
  } catch (error: any) {
    toast.error('Failed to download file', error.message);
  }
};

const loadData = async () => {
  // Only show loader on initial load, not on refreshes
  const isInitialLoad = recordings.value.length === 0 && groups.value.length === 0;
  if (isInitialLoad) {
    loading.value = true;
  }
  try {
    const [recordingsData, groupsData] = await Promise.all([
      projectRecordingClient.list(),
      projectRecordingGroupClient.list()
    ]);

    // Restore deleting state from storage
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
    MessageBus.emit(MessageBus.RECORDINGS_COUNT_CHANGED, recordings.value.length);
    MessageBus.emit(MessageBus.PROFILES_COUNT_CHANGED, recordings.value.filter(r => r.hasProfile).length);
    loading.value = false;
  }
};

// Organize recordings by groups
const organizedRecordings = computed(() => {
  const validGroupIds = new Set(groups.value.map(group => group.id));

  const rootRecordings = recordings.value.filter(recording =>
      recording.groupId == null || !validGroupIds.has(recording.groupId))
      .sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime());

  const groupRecordings = new Map<string, Recording[]>();

  recordings.value.forEach(recording => {
    const groupId = recording.groupId;
    if (groupId && validGroupIds.has(groupId)) {
      if (!groupRecordings.has(groupId)) {
        groupRecordings.set(groupId, []);
      }
      groupRecordings.get(groupId)?.push(recording);
    }
  });

  groupRecordings.forEach((groupRecs, groupId) => {
    groupRecordings.set(
      groupId,
      groupRecs.sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime())
    );
  });

  return { rootRecordings, groupRecordings };
});

// --- Profile Actions ---

const isProfileDeleting = (recording: Recording): boolean => {
  if (!recording.profileId) return false;
  return getDeletingProfiles().has(recording.profileId) || (recording as any)._profileDeleting === true;
};

const selectProfile = () => {
  SecondaryProfileService.remove();
  sessionStorage.removeItem('profile-sidebar-mode');
};

const createProfile = async (recording: Recording) => {
  if (profileCreationStates.value.get(recording.id) || recording.hasProfile) {
    return;
  }

  profileCreationStates.value.set(recording.id, true);
  recording.hasProfile = true;

  try {
    MessageBus.emit(MessageBus.PROFILE_INITIALIZATION_STARTED, true);
    await projectProfileClient.create(recording.id);
    await loadData();

    const updatedRecording = recordings.value.find(r => r.id === recording.id);
    if (updatedRecording) {
      updatedRecording.hasProfile = true;
    }

    // Start polling for initialization
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
    await projectProfileClient.update(editingRecording.value.profileId!, editProfileName.value.trim());
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

  projectProfileClient.delete(profileId)
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
// Lightweight polling: only fetches profiles list and merges state changes
// into existing recordings in-place (no full reload, no blinking).
// Does a full loadData() once when all transitions finish.

const startPolling = () => {
  if (pollInterval.value !== null) return;

  pollInterval.value = window.setInterval(async () => {
    try {
      const profiles = await projectProfileClient.list();
      const profileByRecordingId = new Map<string, { id: string; name: string; enabled: boolean; sizeInBytes: number }>();
      const profileIds = new Set<string>();
      for (const p of profiles) {
        profileIds.add(p.id);
      }

      // Check which "deleting" profiles are actually gone
      const deletingProfiles = getDeletingProfiles();
      let deletionCompleted = false;
      for (const profileId of deletingProfiles) {
        if (!profileIds.has(profileId)) {
          removeDeletingProfile(profileId);
          deletionCompleted = true;
        }
      }

      // Build lookup by scanning recordings for their profileId
      for (const p of profiles) {
        // Find which recording this profile belongs to
        const rec = recordings.value.find(r => r.profileId === p.id);
        if (rec) {
          profileByRecordingId.set(rec.id, { id: p.id, name: p.name, enabled: p.enabled, sizeInBytes: p.sizeInBytes });
        }
      }

      // Merge state changes in-place
      let initializationCompleted = false;
      for (const rec of recordings.value) {
        const profile = profileByRecordingId.get(rec.id);
        if (profile && rec.hasProfile && !rec.profileEnabled && profile.enabled) {
          // Profile just finished initializing
          rec.profileEnabled = true;
          rec.profileName = profile.name;
          rec.profileSizeInBytes = profile.sizeInBytes;
          initializationCompleted = true;
        }
        // Clear deleting flag if profile is gone
        if (rec.profileId && !profileIds.has(rec.profileId) && (rec as any)._profileDeleting) {
          rec.hasProfile = false;
          rec.profileId = null;
          rec.profileName = null;
          rec.profileEnabled = undefined;
          rec.profileSizeInBytes = undefined;
          (rec as any)._profileDeleting = false;
        }
      }

      // If all transitions are done, do one final full reload and stop
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

// --- Recording Actions ---

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

const confirmDeleteGroup = (group: RecordingGroup) => {
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
    createGroupModal.value?.setValidationErrors(['Group name cannot be empty']);
    return;
  }

  try {
    await projectRecordingGroupClient.create(newGroupName.value.trim());
    newGroupName.value = '';
    createGroupModal.value?.hideModal();
    await loadData();
  } catch (error: any) {
    createGroupModal.value?.setValidationErrors([error.message || 'Failed to create group']);
  }
};

const openCreateGroupDialog = () => {
  newGroupName.value = '';
  createGroupModal.value?.showModal();
};

const isRecordingCreatingProfile = (recordingId: string): boolean => {
  return profileCreationStates.value.get(recordingId) || false;
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
  const recording = recordings.value.find(r => r.id === recordingId);
  if (!recording) return;
  if ((recording.groupId || null) === targetGroupId) return;
  recording.groupId = targetGroupId; // optimistic update
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
  <PageHeader
    title="Recordings"
    description="Manage recordings and their profiles. Organize with groups and create profiles for performance analysis."
    icon="bi-record-circle"
  >
    <!-- Recordings Header Bar -->
    <div class="col-12">
      <SectionHeaderBar :text="`Recordings (${recordings.length})`">
        <template #actions>
          <button class="btn btn-primary btn-sm" @click="openCreateGroupDialog">
            <i class="bi bi-folder-plus me-1"></i>New Group
          </button>
        </template>
      </SectionHeaderBar>
    </div>

    <!-- Recordings List -->
    <div class="col-12">
      <LoadingState v-if="loading" message="Loading recordings..." />

      <EmptyState
        v-else-if="recordings.length === 0 && groups.length === 0"
        icon="bi-folder-x"
        title="No Recordings Available"
        description="Recordings from sessions will appear here."
      />

      <div v-else>
            <!-- Groups with their recordings -->
            <div v-for="group in groups" :key="`group-${group.id}`">
              <div class="recording-group-header"
                  :class="{ 'recording-group-drop-target': dragOverGroupId === group.id }"
                  @click="expandedGroups.has(group.id) ? expandedGroups.delete(group.id) : expandedGroups.add(group.id)"
                  @dragover="onDragOver($event, group.id)"
                  @dragleave="onDragLeave($event, group.id)"
                  @drop="onDrop($event, group.id)">
                <i :class="expandedGroups.has(group.id) ? 'bi bi-chevron-down' : 'bi bi-chevron-right'" class="recording-group-chevron"></i>
                <span class="recording-group-name">{{ group.name }}</span>
                <span class="recording-group-count">{{ organizedRecordings.groupRecordings.get(group.id)?.length || 0 }}</span>
                <div class="recording-group-actions" @click.stop>
                  <button class="recording-group-action-btn recording-group-action-delete"
                      @click="confirmDeleteGroup(group)"
                      title="Delete group and all its recordings">
                    <i class="bi bi-trash"></i>
                  </button>
                </div>
              </div>

              <!-- Group recordings (shown when expanded) -->
              <div v-if="expandedGroups.has(group.id)" class="recording-group-items">
                <RecordingCard
                    v-for="recording in organizedRecordings.groupRecordings.get(group.id) || []"
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

                <div v-if="(organizedRecordings.groupRecordings.get(group.id)?.length || 0) === 0" class="recording-group-empty">
                  <span>No recordings</span>
                </div>
              </div>
            </div>

            <!-- Ungrouped Recordings -->
            <div v-if="organizedRecordings.rootRecordings.length > 0">
              <div class="recording-group-header"
                  :class="{ 'recording-group-drop-target': dragOverGroupId === '__ungrouped__' }"
                  @click="expandedGroups.has('root') ? expandedGroups.delete('root') : expandedGroups.add('root')"
                  @dragover="onDragOver($event, null)"
                  @dragleave="onDragLeave($event, null)"
                  @drop="onDrop($event, null)">
                <i :class="expandedGroups.has('root') ? 'bi bi-chevron-down' : 'bi bi-chevron-right'" class="recording-group-chevron"></i>
                <span class="recording-group-name">Ungrouped</span>
                <span class="recording-group-count">{{ organizedRecordings.rootRecordings.length }}</span>
              </div>

              <div v-if="expandedGroups.has('root')" class="recording-group-items">
                <RecordingCard
                    v-for="recording in organizedRecordings.rootRecordings"
                    :key="recording.id"
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
          </div>
    </div>

      <!-- Delete Recording Confirmation Dialog -->
      <ConfirmationDialog
        v-model:show="deleteRecordingDialog"
        title="Confirm Delete"
        :message="recordingToDelete ? `Are you sure you want to delete the recording: ${recordingToDelete.name}?` : 'Are you sure you want to delete this recording?'"
        sub-message="This action cannot be undone."
        confirm-label="Delete"
        confirm-button-class="btn-danger"
        confirm-button-id="deleteRecordingButton"
        modal-id="deleteRecordingModal"
        @confirm="deleteRecording"
      />

      <!-- Delete Group Confirmation Dialog -->
      <ConfirmationDialog
        v-model:show="deleteGroupDialog"
        title="Confirm Delete Group"
        :message="groupToDelete ? `Are you sure you want to delete the group: ${groupToDelete.name}?` : 'Are you sure you want to delete this group?'"
        sub-message="This will also delete all recordings within the group."
        confirm-label="Delete Group"
        confirm-button-class="btn-danger"
        confirm-button-id="deleteGroupButton"
        modal-id="deleteGroupModal"
        @confirm="deleteGroup"
      />

      <!-- Create Group Dialog -->
      <BaseModal
        ref="createGroupModal"
        modal-id="createGroupModal"
        title="Create New Group"
        icon="bi-folder-plus"
        primary-button-text="Create"
        @submit="createGroup"
      >
        <template #body>
          <div class="form-group">
            <label for="newGroupNameInput" class="form-label">Group Name</label>
            <input
                type="text"
                class="form-control"
                id="newGroupNameInput"
                v-model="newGroupName"
                placeholder="Enter group name"
            >
          </div>
        </template>
      </BaseModal>

      <!-- Edit Profile Modal -->
      <EditNameModal
          v-if="editingRecording"
          v-model="editProfileName"
          @submit="updateProfile"
          @close="editingRecording = null"
      />
  </PageHeader>
</template>

<style scoped>
.btn-sm i {
  font-size: 0.8rem;
}
</style>
