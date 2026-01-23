<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  <GenericModal
    :show="show"
    @update:show="emit('update:show', $event)"
    modal-id="secondary-profile-selection"
    title="Select Secondary Profile for Differential Analysis"
    icon="bi-layers"
    size="lg"
    :show-footer="true"
    class="profile-selection-modal"
  >
    <!-- Search Box -->
    <div class="search-box mb-4">
      <i class="bi bi-search search-icon"></i>
      <input
        type="text"
        class="form-control form-control-lg"
        placeholder="Search profiles across all workspaces and projects..."
        v-model="profileSearchQuery"
      />
    </div>

    <!-- Loading State -->
    <div v-if="loadingProfiles" class="text-center py-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      <p class="text-muted mt-3">Loading profiles...</p>
    </div>

    <!-- Profiles List -->
    <div v-else class="profiles-list">
      <!-- Empty State -->
      <div v-if="filteredProfiles.length === 0" class="empty-state">
        <i class="bi bi-file-earmark-bar-graph text-muted mb-3" style="font-size: 3rem;"></i>
        <h5 class="text-muted">No profiles found</h5>
        <p class="text-muted mb-0">
          {{ profileSearchQuery ? 'Try a different search term' : 'No profiles available for selection' }}
        </p>
      </div>

      <!-- Grouped Profiles -->
      <div v-for="group in groupedProfiles" :key="`${group.workspaceId}:${group.projectId}`" class="profile-group mb-4">
        <!-- Group Header -->
        <div class="group-header mb-2">
          <div class="group-path">
            <span class="badge" :class="isCurrentWorkspace(group.workspaceId) ? 'bg-primary' : 'bg-secondary'">
              <i class="bi bi-building me-1"></i>
              {{ group.workspaceId.substring(0, 8) }}...
            </span>
            <i class="bi bi-chevron-right text-muted mx-1"></i>
            <span class="badge" :class="isCurrentProject(group.projectId) ? 'bg-success' : 'bg-secondary'">
              <i class="bi bi-folder me-1"></i>
              {{ group.projectId.substring(0, 8) }}...
            </span>
            <span v-if="isCurrentProject(group.projectId)" class="badge bg-warning text-dark ms-2">
              <i class="bi bi-star-fill me-1"></i>
              Current Project
            </span>
          </div>
        </div>

        <!-- Profile Cards in Group -->
        <div v-for="profile in group.profiles"
             :key="profile.id"
             class="profile-card"
             :class="{
               'selected': selectedProfile?.id === profile.id,
               'current-primary': isPrimaryProfile(profile),
               'disabled': isPrimaryProfile(profile)
             }"
             @click="selectProfile(profile)">
          <div class="profile-header">
            <i class="bi bi-file-earmark-bar-graph me-3 profile-icon"></i>
            <div class="profile-info">
              <div class="profile-name">{{ profile.name }}</div>
              <div class="profile-meta">
                <span class="profile-date">
                  <i class="bi bi-calendar me-1"></i>
                  {{ FormattingService.formatDate(profile.createdAt) }}
                </span>
                <span class="profile-duration">
                  <i class="bi bi-stopwatch me-1"></i>
                  {{ FormattingService.formatDurationInMillis2Units(profile.durationInMillis) }}
                </span>
                <span class="profile-size">
                  <i class="bi bi-hdd me-1"></i>
                  {{ FormattingService.formatBytes(profile.sizeInBytes) }}
                </span>
              </div>
            </div>
            <div class="profile-indicators">
              <div v-if="isPrimaryProfile(profile)" class="indicator primary">
                <i class="bi bi-star-fill"></i>
                <span>Primary</span>
              </div>
              <div v-else-if="selectedProfile?.id === profile.id" class="indicator selected">
                <i class="bi bi-check-circle-fill"></i>
                <span>Selected</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Custom Footer -->
    <template #footer>
      <div class="d-flex justify-content-between align-items-center w-100">
        <div class="selection-summary">
          <span v-if="selectedProfile" class="text-muted">
            Selected: <strong>{{ selectedProfile.name }}</strong>
          </span>
          <span v-else class="text-muted fst-italic">No profile selected</span>
        </div>
        <div class="button-group">
          <button type="button" class="btn btn-outline-danger me-2" @click="clearSelection">
            <i class="bi bi-x-circle me-1"></i>
            Clear
          </button>
          <button type="button" class="btn btn-secondary me-2" @click="closeModal">
            Cancel
          </button>
          <button
            type="button"
            class="btn btn-primary"
            @click="confirmSelection"
            :disabled="!hasValidSelection"
          >
            <i class="bi bi-check2 me-1"></i>
            Select Profile
          </button>
        </div>
      </div>
    </template>
  </GenericModal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import GenericModal from '@/components/GenericModal.vue';
import DirectProfileClient, { ProfileListResponse } from '@/services/api/DirectProfileClient';
import FormattingService from '@/services/FormattingService';
import ToastService from '@/services/ToastService';
import Profile from '@/services/api/model/Profile';

interface Props {
  show: boolean;
  currentProjectId: string;
  currentProfileId: string;
  currentSecondaryProfileId?: string;
  currentSecondaryProjectId?: string;
  workspaceId: string;
}

interface Emits {
  'update:show': [value: boolean];
  'profile-selected': [profile: Profile, projectId: string];
  'profile-cleared': [];
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Local state - simplified to use all profiles from DirectProfileClient
const allProfiles = ref<ProfileListResponse[]>([]);
const selectedProfile = ref<ProfileListResponse | null>(null);

// Loading state
const loadingProfiles = ref(false);

// Search query
const profileSearchQuery = ref('');

// Computed properties
const filteredProfiles = computed(() => {
  let profiles = allProfiles.value;

  // Filter by search query
  if (profileSearchQuery.value) {
    const query = profileSearchQuery.value.toLowerCase();
    profiles = profiles.filter(p =>
      p.name.toLowerCase().includes(query)
    );
  }

  return profiles;
});

// Group profiles by workspace and project for display
const groupedProfiles = computed(() => {
  const groups: Map<string, { workspaceId: string; projectId: string; profiles: ProfileListResponse[] }> = new Map();

  for (const profile of filteredProfiles.value) {
    const key = `${profile.workspaceId}:${profile.projectId}`;
    if (!groups.has(key)) {
      groups.set(key, {
        workspaceId: profile.workspaceId,
        projectId: profile.projectId,
        profiles: []
      });
    }
    groups.get(key)!.profiles.push(profile);
  }

  return Array.from(groups.values());
});

const hasValidSelection = computed(() => {
  return selectedProfile.value !== null &&
    selectedProfile.value.id !== props.currentProfileId;
});

// Initialize when modal opens
watch(() => props.show, (newShow) => {
  if (newShow) {
    initializeModal();
  }
}, { immediate: true });

const initializeModal = async () => {
  await loadAllProfiles();

  // Handle existing secondary profile selection
  if (props.currentSecondaryProfileId) {
    const existingSelection = allProfiles.value.find(p => p.id === props.currentSecondaryProfileId);
    if (existingSelection) {
      selectedProfile.value = existingSelection;
    }
  }
};

const loadAllProfiles = async () => {
  loadingProfiles.value = true;
  try {
    allProfiles.value = await DirectProfileClient.listAll();
  } catch (error) {
    console.error('Failed to load profiles:', error);
    ToastService.error('Failed to load profiles', 'Error occurred while loading profiles');
    allProfiles.value = [];
  } finally {
    loadingProfiles.value = false;
  }
};

const selectProfile = (profile: ProfileListResponse) => {
  // Don't allow selecting the primary profile as the secondary profile
  if (profile.id === props.currentProfileId) {
    ToastService.error("Selection failed", "Cannot select primary profile as secondary profile");
    return;
  }

  selectedProfile.value = profile;
};

const confirmSelection = () => {
  if (selectedProfile.value && hasValidSelection.value) {
    // Convert ProfileListResponse to Profile for compatibility
    const profile = new Profile(
      selectedProfile.value.id,
      selectedProfile.value.projectId,
      selectedProfile.value.workspaceId,
      selectedProfile.value.name,
      selectedProfile.value.createdAt,
      selectedProfile.value.eventSource as any,
      selectedProfile.value.enabled,
      selectedProfile.value.durationInMillis,
      selectedProfile.value.sizeInBytes
    );
    emit('profile-selected', profile, selectedProfile.value.projectId);
    closeModal();
  }
};

const clearSelection = () => {
  selectedProfile.value = null;
  emit('profile-cleared');
  closeModal();
};

const closeModal = () => {
  emit('update:show', false);
};

const isPrimaryProfile = (profile: ProfileListResponse) => {
  return profile.id === props.currentProfileId;
};

const isCurrentProject = (projectId: string) => {
  return projectId === props.currentProjectId;
};

const isCurrentWorkspace = (workspaceId: string) => {
  return workspaceId === props.workspaceId;
};
</script>

<style scoped>
/* Modal Customization */
:deep(.profile-selection-modal .modal-dialog) {
  max-width: 800px;
}

:deep(.profile-selection-modal .modal-body) {
  max-height: 70vh;
  overflow-y: auto;
  padding: 1.5rem;
}

:deep(.profile-selection-modal .modal-footer) {
  padding: 1rem 1.5rem;
  border-top: 1px solid #dee2e6;
  background: #fff;
}

/* Search Box */
.search-box {
  position: relative;
}

.search-icon {
  position: absolute;
  left: 1rem;
  top: 50%;
  transform: translateY(-50%);
  color: #6c757d;
  z-index: 1;
}

.search-box input {
  padding-left: 2.75rem;
  border-radius: 8px;
  border: 2px solid #e9ecef;
  transition: all 0.2s ease;
}

.search-box input:focus {
  border-color: #5e64ff;
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.1);
}

/* Profiles List */
.profiles-list {
  max-height: 55vh;
  overflow-y: auto;
}

.profiles-list::-webkit-scrollbar {
  width: 6px;
}

.profiles-list::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.profiles-list::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

/* Profile Group */
.profile-group {
  border: 1px solid #e9ecef;
  border-radius: 8px;
  overflow: hidden;
}

.group-header {
  padding: 0.75rem 1rem;
  background: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.group-path {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.group-path .badge {
  font-size: 0.7rem;
  font-weight: 500;
}

/* Profile Card */
.profile-card {
  padding: 1rem;
  border-bottom: 1px solid #e9ecef;
  cursor: pointer;
  transition: all 0.2s ease;
  background: #fff;
}

.profile-card:last-child {
  border-bottom: none;
}

.profile-card:hover:not(.disabled) {
  background: rgba(94, 100, 255, 0.04);
}

.profile-card.selected {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border-left: 3px solid #5e64ff;
}

.profile-card.current-primary {
  background: linear-gradient(135deg, #fff9e6, #fef3cd);
  border-left: 3px solid #ffc107;
  cursor: not-allowed;
}

.profile-card.disabled {
  opacity: 0.7;
}

/* Profile Header */
.profile-header {
  display: flex;
  align-items: center;
}

.profile-icon {
  font-size: 1.5rem;
  color: #5e64ff;
}

.profile-card.current-primary .profile-icon {
  color: #ffc107;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-name {
  font-weight: 600;
  color: #212529;
  font-size: 0.95rem;
  margin-bottom: 0.25rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.profile-meta {
  font-size: 0.75rem;
  color: #6c757d;
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.profile-meta span {
  display: flex;
  align-items: center;
}

/* Profile Indicators */
.profile-indicators {
  display: flex;
  align-items: center;
  margin-left: 1rem;
}

.indicator {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.375rem 0.75rem;
  border-radius: 16px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.indicator.primary {
  background: #fff3cd;
  color: #856404;
}

.indicator.selected {
  background: #d4edda;
  color: #155724;
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  text-align: center;
}

/* Selection Summary */
.selection-summary {
  font-size: 0.9rem;
}

/* Button Group */
.button-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.button-group .btn {
  display: flex;
  align-items: center;
  font-weight: 500;
  transition: all 0.2s ease;
}

.button-group .btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.button-group .btn-primary {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border-color: #5e64ff;
  box-shadow: 0 2px 4px rgba(94, 100, 255, 0.2);
}

.button-group .btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4c52ff, #3d47ff);
  border-color: #4c52ff;
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(94, 100, 255, 0.3);
}

/* Responsive Design */
@media (max-width: 768px) {
  :deep(.profile-selection-modal .modal-dialog) {
    max-width: 100%;
    margin: 0.5rem;
  }

  .profile-meta {
    flex-direction: column;
    gap: 0.25rem;
  }

  .button-group {
    flex-wrap: wrap;
  }
}
</style>
