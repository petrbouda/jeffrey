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
    title="Select Secondary Profile"
    icon="bi-layers-half"
    size="xl"
    :show-footer="true"
    class="profile-selection-modal"
  >
    <!-- Search Bar -->
    <div class="search-wrapper">
      <div class="search-box">
        <i class="bi bi-search search-icon"></i>
        <input
          type="text"
          class="form-control"
          placeholder="Search profiles across all workspaces and projects..."
          v-model="profileSearchQuery"
        />
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loadingProfiles" class="loading-state">
      <div class="modern-spinner"></div>
      <p class="loading-text">Loading profiles...</p>
    </div>

    <!-- Empty State (no profiles at all) -->
    <div v-else-if="allProfiles.length === 0" class="empty-state">
      <div class="empty-state-icon-wrapper">
        <i class="bi bi-file-earmark-bar-graph"></i>
      </div>
      <h5>No profiles found</h5>
      <p class="mb-0">No profiles available for selection</p>
    </div>

    <!-- Split-Pane Layout -->
    <div v-else class="split-pane">
      <!-- Left Sidebar: Group list -->
      <div class="tree-sidebar">
        <button
          v-for="node in groupNodes"
          :key="node.key"
          type="button"
          class="tree-node"
          :class="{ active: selectedGroupKey === node.key }"
          @click="selectGroup(node.key)"
        >
          <i class="bi tree-node-icon" :class="node.icon"></i>
          <span class="tree-node-name">{{ node.name }}</span>
          <span class="tree-node-count">{{ node.count }}</span>
        </button>
      </div>

      <!-- Right Content: Profile Cards -->
      <div class="profile-content">
        <!-- No profiles in this group -->
        <div v-if="selectedGroupProfiles.length === 0" class="profile-content-empty">
          <div class="empty-state-icon-wrapper small">
            <i class="bi bi-file-earmark-bar-graph"></i>
          </div>
          <p class="mt-2 mb-0">
            {{
              profileSearchQuery
                ? 'No matching profiles in this group'
                : 'No profiles in this group'
            }}
          </p>
        </div>

        <!-- Profile cards -->
        <template v-else>
          <div class="profile-content-header">
            Profiles in "{{ selectedGroupName }}"
            <span class="profile-content-header-count">({{ selectedGroupProfiles.length }})</span>
          </div>
          <div class="profile-cards-list">
            <div
              v-for="profile in selectedGroupProfiles"
              :key="profile.id"
              class="profile-card"
              :class="{
                selected: selectedProfile?.id === profile.id,
                primary: isPrimaryProfile(profile)
              }"
              @click="selectProfile(profile)"
            >
              <div class="profile-card-indicator">
                <i v-if="selectedProfile?.id === profile.id" class="bi bi-check-circle-fill"></i>
                <i v-else class="bi bi-circle"></i>
              </div>
              <div class="profile-card-body">
                <div class="profile-card-name">
                  {{ profile.name }}
                  <span v-if="isPrimaryProfile(profile)" class="primary-badge">
                    <i class="bi bi-lock-fill me-1"></i>Primary
                  </span>
                </div>
                <div class="profile-card-meta">
                  <span>{{ FormattingService.formatDate(profile.createdAt) }}</span>
                  <span class="meta-separator"></span>
                  <span>{{
                    FormattingService.formatDurationInMillis2Units(profile.durationInMillis)
                  }}</span>
                  <span class="meta-separator"></span>
                  <span>{{ FormattingService.formatBytes(profile.sizeInBytes) }}</span>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- Custom Footer -->
    <template #footer>
      <div class="d-flex justify-content-between align-items-center w-100">
        <div class="selection-summary">
          <span v-if="selectedProfile" class="selected-chip">
            <i class="bi bi-check-circle-fill chip-icon"></i>
            {{ selectedProfile.name }}
          </span>
          <span v-else class="no-selection">No profile selected</span>
        </div>
        <div class="button-group">
          <button type="button" class="btn btn-outline-danger" @click="clearSelection">
            <i class="bi bi-x-circle me-1"></i>
            Clear
          </button>
          <button type="button" class="btn btn-cancel" @click="closeModal">Cancel</button>
          <button
            type="button"
            class="btn btn-select-profile"
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
import type { ProfileListResponse } from '@/services/api/DirectProfileClient';
import RecordingsClient from '@/services/api/RecordingsClient';
import type RecordingGroup from '@/services/api/model/RecordingGroup';

const recordingsClient = new RecordingsClient();
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

interface ProfileEntry extends ProfileListResponse {
  groupId: string | null;
}

interface GroupNode {
  key: string;
  name: string;
  icon: string;
  count: number;
}

const RECORDINGS_WORKSPACE_ID = 'recordings';
const RECORDINGS_WORKSPACE_NAME = 'Recordings';
const ALL_GROUP_KEY = 'all';
const UNGROUPED_KEY = '__ungrouped__';

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Local state
const allProfiles = ref<ProfileEntry[]>([]);
const allGroups = ref<RecordingGroup[]>([]);
const selectedProfile = ref<ProfileEntry | null>(null);
const loadingProfiles = ref(false);
const profileSearchQuery = ref('');

// Selection state
const selectedGroupKey = ref<string>(ALL_GROUP_KEY);

// Saved state for search restore
const savedSelectionState = ref<{ groupKey: string } | null>(null);

// Computed properties
const filteredProfiles = computed(() => {
  let profiles = allProfiles.value;
  if (profileSearchQuery.value) {
    const query = profileSearchQuery.value.toLowerCase();
    profiles = profiles.filter(p => p.name.toLowerCase().includes(query));
  }
  return profiles;
});

const groupCounts = computed<Map<string | null, number>>(() => {
  const map = new Map<string | null, number>();
  for (const p of allProfiles.value) {
    map.set(p.groupId, (map.get(p.groupId) ?? 0) + 1);
  }
  return map;
});

const ungroupedCount = computed(() => groupCounts.value.get(null) ?? 0);

const sortedGroups = computed<RecordingGroup[]>(() => {
  const newestForGroup = (groupId: string): number => {
    let max = 0;
    for (const p of allProfiles.value) {
      if (p.groupId === groupId) {
        const t = Number(p.createdAt) || 0;
        if (t > max) {
          max = t;
        }
      }
    }
    return max;
  };
  return [...allGroups.value].sort((a, b) => {
    const diff = newestForGroup(b.id) - newestForGroup(a.id);
    if (diff !== 0) {
      return diff;
    }
    return a.name.localeCompare(b.name);
  });
});

const groupNodes = computed<GroupNode[]>(() => {
  const nodes: GroupNode[] = [];
  nodes.push({
    key: ALL_GROUP_KEY,
    name: 'All',
    icon: 'bi-record-circle-fill',
    count: allProfiles.value.length
  });
  for (const g of sortedGroups.value) {
    nodes.push({
      key: g.id,
      name: g.name,
      icon: 'bi-folder',
      count: groupCounts.value.get(g.id) ?? 0
    });
  }
  if (ungroupedCount.value > 0) {
    nodes.push({
      key: UNGROUPED_KEY,
      name: 'Ungrouped',
      icon: 'bi-collection',
      count: ungroupedCount.value
    });
  }
  return nodes;
});

const selectedGroupName = computed(() => {
  if (selectedGroupKey.value === ALL_GROUP_KEY) {
    return 'All';
  }
  if (selectedGroupKey.value === UNGROUPED_KEY) {
    return 'Ungrouped';
  }
  return allGroups.value.find(g => g.id === selectedGroupKey.value)?.name ?? 'Group';
});

const selectedGroupProfiles = computed(() => {
  if (selectedGroupKey.value === ALL_GROUP_KEY) {
    return filteredProfiles.value;
  }
  if (selectedGroupKey.value === UNGROUPED_KEY) {
    return filteredProfiles.value.filter(p => p.groupId === null);
  }
  return filteredProfiles.value.filter(p => p.groupId === selectedGroupKey.value);
});

const hasValidSelection = computed(() => {
  return selectedProfile.value !== null && selectedProfile.value.id !== props.currentProfileId;
});

// Watch modal open
watch(
  () => props.show,
  newShow => {
    if (newShow) {
      initializeModal();
    }
  },
  { immediate: true }
);

// Watch search query for auto-select
watch(profileSearchQuery, (newQuery, oldQuery) => {
  if (newQuery && !oldQuery) {
    // Entering search: save current selection state
    savedSelectionState.value = { groupKey: selectedGroupKey.value };
  }

  if (newQuery) {
    // If the currently selected group has no matches, jump to the first match's group
    if (selectedGroupProfiles.value.length === 0 && filteredProfiles.value.length > 0) {
      selectedGroupKey.value = ALL_GROUP_KEY;
    }
  } else if (!newQuery && oldQuery) {
    // Clearing search: restore saved selection state
    if (savedSelectionState.value) {
      selectedGroupKey.value = savedSelectionState.value.groupKey;
      savedSelectionState.value = null;
    }
  }
});

const initializeModal = async () => {
  profileSearchQuery.value = '';
  savedSelectionState.value = null;
  await loadAllProfiles();

  if (props.currentSecondaryProfileId) {
    // If secondary profile already selected: pre-select it and jump to its group
    const existing = allProfiles.value.find(p => p.id === props.currentSecondaryProfileId);
    if (existing) {
      selectedProfile.value = existing;
      selectedGroupKey.value = existing.groupId ?? UNGROUPED_KEY;
      return;
    }
  }

  // Default: All
  selectedProfile.value = null;
  selectedGroupKey.value = ALL_GROUP_KEY;
};

const loadAllProfiles = async () => {
  loadingProfiles.value = true;
  try {
    const [profiles, groups] = await Promise.all([
      recordingsClient.listProfiles(),
      recordingsClient.listGroups()
    ]);
    allProfiles.value = profiles.map(p => ({
      id: p.profileId!,
      name: p.profileName || p.filename,
      projectId: RECORDINGS_WORKSPACE_ID,
      projectName: RECORDINGS_WORKSPACE_NAME,
      workspaceId: RECORDINGS_WORKSPACE_ID,
      workspaceName: RECORDINGS_WORKSPACE_NAME,
      createdAt: p.uploadedAt,
      eventSource: p.eventSource,
      enabled: true,
      durationInMillis: p.durationInMillis,
      sizeInBytes: p.profileSizeInBytes,
      groupId: p.groupId
    }));
    allGroups.value = groups;
  } catch (error) {
    console.error('Failed to load profiles:', error);
    ToastService.error('Failed to load profiles', 'Error occurred while loading profiles');
    allProfiles.value = [];
    allGroups.value = [];
  } finally {
    loadingProfiles.value = false;
  }
};

const selectGroup = (key: string) => {
  selectedGroupKey.value = key;
};

const selectProfile = (profile: ProfileEntry) => {
  if (profile.id === props.currentProfileId) {
    ToastService.error('Selection failed', 'Cannot select primary profile as secondary profile');
    return;
  }
  selectedProfile.value = profile;
};

const confirmSelection = () => {
  if (selectedProfile.value && hasValidSelection.value) {
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

const isPrimaryProfile = (profile: ProfileEntry) => {
  return profile.id === props.currentProfileId;
};
</script>

<style scoped>
/* ============================================================
   Modal Container — Glass Morphism
   ============================================================ */
:deep(.modal-dialog.modal-xl) {
  max-width: 1400px;
}

:deep(.profile-selection-modal .modal-content) {
  background: linear-gradient(135deg, var(--color-white), var(--color-light));
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow:
    0 20px 40px rgba(0, 0, 0, 0.08),
    0 8px 24px rgba(0, 0, 0, 0.06);
  backdrop-filter: blur(10px);
  overflow: hidden;
}

:deep(.profile-selection-modal .modal-body) {
  overflow: hidden;
  padding: 0;
}

/* ============================================================
   Modal Header — Gradient Header
   ============================================================ */
:deep(.profile-selection-modal .modal-header) {
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.05), rgba(94, 100, 255, 0.08));
  border-radius: 16px 16px 0 0;
  border-bottom: none;
  padding: 20px 24px 12px;
}

:deep(.profile-selection-modal .modal-header .modal-title) {
  font-weight: 700;
  font-size: 1.1rem;
  color: var(--color-dark);
}

:deep(.profile-selection-modal .modal-header .bi-layers-half),
:deep(.profile-selection-modal .modal-header .bi-layers) {
  color: var(--color-primary);
  font-size: 1.3rem;
}

/* ============================================================
   Modal Footer — Polished
   ============================================================ */
:deep(.profile-selection-modal .modal-footer) {
  padding: 1rem 1.5rem;
  border-top: 1px solid rgba(94, 100, 255, 0.08);
  background: linear-gradient(135deg, var(--color-light), var(--color-white));
}

/* ============================================================
   Search Bar
   ============================================================ */
.search-wrapper {
  padding: 0.75rem 1.5rem;
  background: linear-gradient(135deg, var(--color-light), var(--color-white));
  border-bottom: 1px solid rgba(94, 100, 255, 0.06);
}

.search-box {
  position: relative;
}

.search-icon {
  position: absolute;
  left: 0.875rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-text-light);
  z-index: 1;
  transition: color 0.2s ease;
}

.search-box:focus-within .search-icon {
  color: var(--color-primary);
}

.search-box input {
  height: 38px;
  padding-left: 2.5rem;
  border-radius: 10px;
  border: 1px solid rgba(94, 100, 255, 0.12);
  background: var(--color-white);
  font-size: 0.875rem;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.03);
}

.search-box input:focus {
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow:
    inset 0 1px 2px rgba(0, 0, 0, 0.03),
    0 0 0 3px rgba(94, 100, 255, 0.06);
  outline: none;
}

/* ============================================================
   Loading & Empty States
   ============================================================ */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
}

.modern-spinner {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 3px solid rgba(94, 100, 255, 0.12);
  border-top-color: var(--color-primary);
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  color: var(--color-text-muted);
  font-size: 0.85rem;
  margin-top: 1rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
}

.empty-state h5 {
  color: var(--color-text);
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.empty-state p {
  color: var(--color-text-light);
  font-size: 0.85rem;
}

.empty-state-icon-wrapper {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.06), rgba(94, 100, 255, 0.1));
  border: 1px solid rgba(94, 100, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 0.75rem;
}

.empty-state-icon-wrapper i {
  font-size: 1.6rem;
  color: var(--color-text-muted);
}

.empty-state-icon-wrapper.small {
  width: 48px;
  height: 48px;
  border-radius: 12px;
}

.empty-state-icon-wrapper.small i {
  font-size: 1.2rem;
}

/* ============================================================
   Split-Pane Layout
   ============================================================ */
.split-pane {
  display: flex;
  max-height: calc(75vh - 180px);
  min-height: 300px;
  overflow: hidden;
}

/* ============================================================
   Tree Sidebar
   ============================================================ */
.tree-sidebar {
  width: 350px;
  min-width: 350px;
  background: var(--color-light);
  border-right: 1px solid rgba(94, 100, 255, 0.08);
  overflow-y: auto;
  padding: 0.5rem 0;
}

.tree-sidebar::-webkit-scrollbar {
  width: 4px;
}

.tree-sidebar::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.08);
  border-radius: 4px;
}

.tree-sidebar:hover::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
}

/* Group list item */
.tree-node {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.5rem 0.85rem;
  background: transparent;
  border: 0;
  border-left: 3px solid transparent;
  text-align: left;
  font-family: inherit;
  font-size: 0.88rem;
  color: var(--color-text);
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    border-color 0.15s ease;
}

.tree-node:hover {
  background: rgba(94, 100, 255, 0.04);
  color: var(--color-dark);
}

.tree-node.active {
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-left-color: var(--color-primary);
}

.tree-node-icon {
  font-size: 0.82rem;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.tree-node.active .tree-node-icon {
  color: var(--color-primary);
}

.tree-node-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.tree-node.active .tree-node-name {
  font-weight: 600;
}

.tree-node-count {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--color-text-muted);
  background: var(--color-lighter);
  padding: 1px 7px;
  border-radius: 999px;
  flex-shrink: 0;
}

.tree-node.active .tree-node-count {
  background: var(--color-white);
  color: var(--color-primary);
}

/* Tree children (project level) */
.tree-children {
  padding-left: 0.5rem;
}

.tree-child {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.75rem 0.3rem 1.4rem;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
  border-left: 2px solid transparent;
}

.tree-child:hover {
  background: rgba(94, 100, 255, 0.04);
}

.tree-child.active {
  background: rgba(94, 100, 255, 0.06);
  border-left-color: var(--color-primary);
}

.tree-child.active .tree-child-name {
  color: var(--color-primary);
  font-weight: 600;
}

.tree-child-name {
  font-size: 0.85rem;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.tree-child-count {
  font-size: 0.75rem;
  color: var(--color-text-light);
  flex-shrink: 0;
}

/* ============================================================
   Profile Content Panel
   ============================================================ */
.profile-content {
  flex: 1;
  overflow-y: auto;
  min-width: 0;
}

.profile-content::-webkit-scrollbar {
  width: 5px;
}

.profile-content::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.08);
  border-radius: 4px;
}

.profile-content:hover::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
}

.profile-content-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 2rem;
  color: var(--color-text-muted);
}

.profile-content-header {
  color: var(--color-text-muted);
  font-weight: 700;
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  padding: 0.75rem 1rem 0.4rem;
}

.profile-content-header-count {
  font-weight: 500;
}

/* ============================================================
   Profile Cards
   ============================================================ */
.profile-cards-list {
  padding: 0 0.75rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.profile-card {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-radius: 10px;
  border: 2px solid rgba(94, 100, 255, 0.1);
  background: linear-gradient(135deg, var(--color-white), var(--color-light));
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.profile-card:hover {
  border-color: rgba(94, 100, 255, 0.25);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.08);
}

.profile-card.selected {
  border-color: var(--color-primary);
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.06), rgba(94, 100, 255, 0.1));
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.15);
}

.profile-card.primary {
  opacity: 0.55;
  pointer-events: none;
  background: var(--color-amber-bg);
  border-color: var(--color-amber-border);
}

.profile-card-indicator {
  padding-top: 0.15rem;
  flex-shrink: 0;
  font-size: 1rem;
  color: var(--color-slate-light);
}

.profile-card.selected .profile-card-indicator {
  color: var(--color-primary);
}

.profile-card-body {
  flex: 1;
  min-width: 0;
}

.profile-card-name {
  font-weight: 600;
  font-size: 0.92rem;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-card-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
  font-size: 0.82rem;
  color: var(--color-text-light);
  flex-wrap: wrap;
}

.meta-separator {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--color-muted-separator);
  flex-shrink: 0;
}

.primary-badge {
  display: inline-flex;
  align-items: center;
  font-size: 0.65rem;
  font-weight: 600;
  color: var(--color-amber-text);
  background: rgba(245, 158, 11, 0.15);
  padding: 0.1rem 0.45rem;
  border-radius: 6px;
  margin-left: 0.4rem;
  vertical-align: middle;
}

/* ============================================================
   Footer — Selection Summary & Buttons
   ============================================================ */
.selection-summary {
  font-size: 0.875rem;
}

.selected-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.08), rgba(94, 100, 255, 0.12));
  color: var(--color-primary-hover);
  font-weight: 600;
  font-size: 0.82rem;
  padding: 0.35rem 0.75rem;
  border-radius: 8px;
  border: 1px solid rgba(94, 100, 255, 0.12);
}

.chip-icon {
  color: var(--color-primary);
  font-size: 0.8rem;
}

.no-selection {
  color: var(--color-text-light);
  font-style: italic;
  font-size: 0.82rem;
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
  font-size: 0.85rem;
  border-radius: 10px;
  padding: 0.45rem 1rem;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.btn-cancel {
  background: linear-gradient(135deg, var(--color-light), var(--color-border));
  border: 1px solid rgba(108, 117, 125, 0.2);
  color: var(--color-text-muted);
}

.btn-cancel:hover {
  background: linear-gradient(135deg, var(--color-border), var(--color-border));
  color: var(--color-text);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.btn-outline-danger {
  border-radius: 10px;
}

.btn-outline-danger:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(220, 53, 69, 0.15);
}

.btn-select-profile {
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-hover));
  border: none;
  color: var(--color-white);
  font-weight: 600;
  box-shadow:
    0 4px 12px rgba(94, 100, 255, 0.3),
    0 2px 4px rgba(94, 100, 255, 0.2);
}

.btn-select-profile:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--color-primary-hover), var(--color-primary-hover));
  color: var(--color-white);
  transform: translateY(-2px);
  box-shadow:
    0 6px 16px rgba(94, 100, 255, 0.4),
    0 3px 6px rgba(94, 100, 255, 0.3);
}

.btn-select-profile:active:not(:disabled) {
  transform: translateY(-1px);
}

.btn-select-profile:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ============================================================
   Responsive
   ============================================================ */
@media (max-width: 768px) {
  :deep(.profile-selection-modal .modal-dialog) {
    max-width: 100%;
    margin: 0.5rem;
  }

  .split-pane {
    flex-direction: column;
    max-height: calc(70vh - 140px);
  }

  .tree-sidebar {
    width: 100%;
    min-width: 100%;
    max-height: 140px;
    border-right: none;
    border-bottom: 1px solid rgba(94, 100, 255, 0.08);
  }

  .button-group {
    flex-wrap: wrap;
  }
}
</style>
