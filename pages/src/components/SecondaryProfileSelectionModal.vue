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
      <!-- Left Sidebar: Tree -->
      <div class="tree-sidebar">
        <!-- Quick Analysis node -->
        <div v-if="quickAnalysisNode" class="tree-node tree-node-quick"
             :class="{ active: selectedWorkspaceId === QUICK_ANALYSIS_WORKSPACE_ID }"
             @click="selectWorkspace(QUICK_ANALYSIS_WORKSPACE_ID)">
          <div class="tree-node-header">
            <i class="bi bi-lightning-charge-fill tree-node-icon-quick"></i>
            <span class="tree-node-name">Quick Analysis</span>
            <span class="tree-node-count">({{ quickAnalysisNode.profileCount }})</span>
          </div>
        </div>

        <!-- Regular workspace nodes -->
        <div v-for="ws in regularWorkspaceTree" :key="ws.id" class="tree-group">
          <div class="tree-node"
               :class="{ active: selectedWorkspaceId === ws.id && selectedProjectKey?.projectId === ws.id }"
               @click="toggleWorkspaceExpand(ws.id)">
            <div class="tree-node-header">
              <i class="bi tree-expand-icon"
                 :class="expandedWorkspaces.has(ws.id) ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
              <span class="tree-node-name">{{ ws.name }}</span>
              <span v-if="ws.id === props.workspaceId" class="current-dot"></span>
              <span class="tree-node-count">({{ ws.profileCount }})</span>
            </div>
          </div>
          <!-- Project children -->
          <div v-if="expandedWorkspaces.has(ws.id)" class="tree-children">
            <div v-for="proj in ws.projects" :key="proj.id"
                 class="tree-child"
                 :class="{ active: selectedProjectKey?.workspaceId === ws.id && selectedProjectKey?.projectId === proj.id }"
                 @click="selectProject(ws.id, proj.id)">
              <span class="tree-child-name">{{ proj.name }}</span>
              <span class="tree-child-count">({{ proj.profileCount }})</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Content: Profile Cards -->
      <div class="profile-content">
        <!-- No project selected -->
        <div v-if="!selectedProjectKey" class="profile-content-empty">
          <div class="empty-state-icon-wrapper small">
            <i class="bi bi-arrow-left-circle"></i>
          </div>
          <p class="mt-2 mb-0">Select a project to see profiles</p>
        </div>

        <!-- Project selected but no profiles -->
        <div v-else-if="selectedProjectProfiles.length === 0" class="profile-content-empty">
          <div class="empty-state-icon-wrapper small">
            <i class="bi bi-file-earmark-bar-graph"></i>
          </div>
          <p class="mt-2 mb-0">
            {{ profileSearchQuery ? 'No matching profiles in this project' : 'No profiles in this project' }}
          </p>
        </div>

        <!-- Profile cards -->
        <template v-else>
          <div class="profile-content-header">
            Profiles in "{{ selectedProjectName }}"
            <span class="profile-content-header-count">({{ selectedProjectProfiles.length }})</span>
          </div>
          <div class="profile-cards-list">
            <div v-for="profile in selectedProjectProfiles" :key="profile.id"
                 class="profile-card"
                 :class="{
                   selected: selectedProfile?.id === profile.id,
                   primary: isPrimaryProfile(profile)
                 }"
                 @click="selectProfile(profile)">
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
                  <span>{{ FormattingService.formatDurationInMillis2Units(profile.durationInMillis) }}</span>
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
          <button type="button" class="btn btn-cancel" @click="closeModal">
            Cancel
          </button>
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
import DirectProfileClient, { ProfileListResponse } from '@/services/api/DirectProfileClient';
import QuickAnalysisClient from '@/services/api/QuickAnalysisClient';
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

interface ProjectNode {
  id: string;
  name: string;
  profileCount: number;
}

interface WorkspaceNode {
  id: string;
  name: string;
  profileCount: number;
  projects: ProjectNode[];
}

const QUICK_ANALYSIS_WORKSPACE_ID = 'quick-analysis';
const QUICK_ANALYSIS_WORKSPACE_NAME = 'Quick Analysis';

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Local state
const allProfiles = ref<ProfileListResponse[]>([]);
const selectedProfile = ref<ProfileListResponse | null>(null);
const loadingProfiles = ref(false);
const profileSearchQuery = ref('');

// Selection state
const selectedProjectKey = ref<{ workspaceId: string; projectId: string } | null>(null);

// Tree expand state
const expandedWorkspaces = ref<Set<string>>(new Set());

// Saved state for search restore
const savedSelectionState = ref<{ selected: { workspaceId: string; projectId: string } | null } | null>(null);

// Computed: which workspace is currently selected
const selectedWorkspaceId = computed(() => selectedProjectKey.value?.workspaceId ?? null);

// Computed properties
const filteredProfiles = computed(() => {
  let profiles = allProfiles.value;
  if (profileSearchQuery.value) {
    const query = profileSearchQuery.value.toLowerCase();
    profiles = profiles.filter(p =>
      p.name.toLowerCase().includes(query)
    );
  }
  return profiles;
});

const workspaceTree = computed((): WorkspaceNode[] => {
  const wsMap = new Map<string, { id: string; name: string; projects: Map<string, { id: string; name: string; count: number }> }>();

  for (const profile of filteredProfiles.value) {
    if (!wsMap.has(profile.workspaceId)) {
      wsMap.set(profile.workspaceId, {
        id: profile.workspaceId,
        name: profile.workspaceName || profile.workspaceId,
        projects: new Map()
      });
    }
    const ws = wsMap.get(profile.workspaceId)!;
    if (!ws.projects.has(profile.projectId)) {
      ws.projects.set(profile.projectId, {
        id: profile.projectId,
        name: profile.projectName || profile.projectId,
        count: 0
      });
    }
    ws.projects.get(profile.projectId)!.count++;
  }

  const nodes: WorkspaceNode[] = [];
  for (const ws of wsMap.values()) {
    const projects: ProjectNode[] = [];
    for (const proj of ws.projects.values()) {
      projects.push({ id: proj.id, name: proj.name, profileCount: proj.count });
    }
    // Sort projects: current project first, then alphabetical
    projects.sort((a, b) => {
      if (a.id === props.currentProjectId) return -1;
      if (b.id === props.currentProjectId) return 1;
      return a.name.localeCompare(b.name);
    });

    const totalCount = projects.reduce((sum, p) => sum + p.profileCount, 0);
    nodes.push({ id: ws.id, name: ws.name, profileCount: totalCount, projects });
  }

  // Sort workspaces: current workspace first, then alphabetical
  nodes.sort((a, b) => {
    if (a.id === props.workspaceId) return -1;
    if (b.id === props.workspaceId) return 1;
    return a.name.localeCompare(b.name);
  });

  return nodes;
});

const quickAnalysisNode = computed(() => {
  return workspaceTree.value.find(ws => ws.id === QUICK_ANALYSIS_WORKSPACE_ID) ?? null;
});

const regularWorkspaceTree = computed(() => {
  return workspaceTree.value.filter(ws => ws.id !== QUICK_ANALYSIS_WORKSPACE_ID);
});

// Computed: name of the selected project
const selectedProjectName = computed(() => {
  if (!selectedWorkspaceId.value) return '';
  if (selectedWorkspaceId.value === QUICK_ANALYSIS_WORKSPACE_ID) return 'Quick Analysis';
  const ws = workspaceTree.value.find(w => w.id === selectedWorkspaceId.value);
  if (!ws) return '';
  const proj = ws.projects.find(p => p.id === selectedProjectKey.value?.projectId);
  return proj?.name ?? ws.name;
});

const selectedProjectProfiles = computed(() => {
  if (!selectedProjectKey.value) return [];
  return filteredProfiles.value.filter(
    p => p.workspaceId === selectedProjectKey.value!.workspaceId &&
         p.projectId === selectedProjectKey.value!.projectId
  );
});

const hasValidSelection = computed(() => {
  return selectedProfile.value !== null &&
    selectedProfile.value.id !== props.currentProfileId;
});

// Watch modal open
watch(() => props.show, (newShow) => {
  if (newShow) {
    initializeModal();
  }
}, { immediate: true });

// Watch search query for auto-select and auto-expand
watch(profileSearchQuery, (newQuery, oldQuery) => {
  if (newQuery && !oldQuery) {
    // Entering search: save current selection state
    savedSelectionState.value = {
      selected: selectedProjectKey.value ? { ...selectedProjectKey.value } : null
    };
  }

  if (newQuery) {
    // Auto-expand workspaces that have matching profiles
    for (const ws of workspaceTree.value) {
      if (ws.id !== QUICK_ANALYSIS_WORKSPACE_ID && ws.profileCount > 0) {
        expandedWorkspaces.value.add(ws.id);
      }
    }

    // If current selection has no matches, auto-select workspace of first match
    if (selectedProjectKey.value) {
      const hasMatches = filteredProfiles.value.some(
        p => p.workspaceId === selectedProjectKey.value!.workspaceId &&
             p.projectId === selectedProjectKey.value!.projectId
      );
      if (!hasMatches && filteredProfiles.value.length > 0) {
        const first = filteredProfiles.value[0];
        selectedProjectKey.value = { workspaceId: first.workspaceId, projectId: first.projectId };
      }
    } else if (filteredProfiles.value.length > 0) {
      const first = filteredProfiles.value[0];
      selectedProjectKey.value = { workspaceId: first.workspaceId, projectId: first.projectId };
    }
  } else if (!newQuery && oldQuery) {
    // Clearing search: restore saved selection state
    if (savedSelectionState.value) {
      selectedProjectKey.value = savedSelectionState.value.selected;
      savedSelectionState.value = null;
    }
  }
});

const toggleWorkspaceExpand = (wsId: string) => {
  if (expandedWorkspaces.value.has(wsId)) {
    expandedWorkspaces.value.delete(wsId);
  } else {
    expandedWorkspaces.value.add(wsId);
    // Auto-select first project when expanding
    const ws = workspaceTree.value.find(w => w.id === wsId);
    if (ws && ws.projects.length > 0 && selectedProjectKey.value?.workspaceId !== wsId) {
      selectProject(wsId, ws.projects[0].id);
    }
  }
};

const initializeModal = async () => {
  profileSearchQuery.value = '';
  savedSelectionState.value = null;
  expandedWorkspaces.value = new Set();
  await loadAllProfiles();

  if (props.currentSecondaryProfileId) {
    // If secondary profile already selected: select its workspace/project, pre-select it
    const existing = allProfiles.value.find(p => p.id === props.currentSecondaryProfileId);
    if (existing) {
      selectedProfile.value = existing;
      selectedProjectKey.value = { workspaceId: existing.workspaceId, projectId: existing.projectId };
      if (existing.workspaceId !== QUICK_ANALYSIS_WORKSPACE_ID) {
        expandedWorkspaces.value.add(existing.workspaceId);
      }
      return;
    }
  }

  // Default: select current workspace + current project, expand current workspace
  selectedProjectKey.value = { workspaceId: props.workspaceId, projectId: props.currentProjectId };
  selectedProfile.value = null;
  expandedWorkspaces.value.add(props.workspaceId);
};

const loadAllProfiles = async () => {
  loadingProfiles.value = true;
  try {
    const [regularProfiles, quickProfiles] = await Promise.all([
      DirectProfileClient.listAll(),
      QuickAnalysisClient.listProfiles()
    ]);

    const mappedQuickProfiles: ProfileListResponse[] = quickProfiles.map(p => ({
      id: p.id,
      name: p.name,
      projectId: QUICK_ANALYSIS_WORKSPACE_ID,
      projectName: QUICK_ANALYSIS_WORKSPACE_NAME,
      workspaceId: QUICK_ANALYSIS_WORKSPACE_ID,
      workspaceName: QUICK_ANALYSIS_WORKSPACE_NAME,
      createdAt: p.createdAt,
      eventSource: p.eventSource,
      enabled: p.enabled,
      durationInMillis: p.durationInMillis,
      sizeInBytes: p.sizeInBytes
    }));

    allProfiles.value = [...regularProfiles, ...mappedQuickProfiles];
  } catch (error) {
    console.error('Failed to load profiles:', error);
    ToastService.error('Failed to load profiles', 'Error occurred while loading profiles');
    allProfiles.value = [];
  } finally {
    loadingProfiles.value = false;
  }
};

const selectWorkspace = (wsId: string) => {
  const ws = workspaceTree.value.find(w => w.id === wsId);
  if (ws && ws.projects.length > 0) {
    selectProject(wsId, ws.projects[0].id);
  }
};

const selectProject = (wsId: string, projId: string) => {
  selectedProjectKey.value = { workspaceId: wsId, projectId: projId };
};

const selectProfile = (profile: ProfileListResponse) => {
  if (profile.id === props.currentProfileId) {
    ToastService.error("Selection failed", "Cannot select primary profile as secondary profile");
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

const isPrimaryProfile = (profile: ProfileListResponse) => {
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
  background: linear-gradient(135deg, #ffffff, #fafbff);
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
  color: #1a1a2e;
}

:deep(.profile-selection-modal .modal-header .bi-layers-half),
:deep(.profile-selection-modal .modal-header .bi-layers) {
  color: #5e64ff;
  font-size: 1.3rem;
}

/* ============================================================
   Modal Footer — Polished
   ============================================================ */
:deep(.profile-selection-modal .modal-footer) {
  padding: 1rem 1.5rem;
  border-top: 1px solid rgba(94, 100, 255, 0.08);
  background: linear-gradient(135deg, #fafbff, #ffffff);
}

/* ============================================================
   Search Bar
   ============================================================ */
.search-wrapper {
  padding: 0.75rem 1.5rem;
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
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
  color: #9ca3af;
  z-index: 1;
  transition: color 0.2s ease;
}

.search-box:focus-within .search-icon {
  color: #5e64ff;
}

.search-box input {
  height: 38px;
  padding-left: 2.5rem;
  border-radius: 10px;
  border: 1px solid rgba(94, 100, 255, 0.12);
  background: #ffffff;
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
  border-top-color: #5e64ff;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-text {
  color: #7c8db0;
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
  color: #5e6e82;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.empty-state p {
  color: #9ca3af;
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
  color: #7c8db0;
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
  background: #f8f9fb;
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

/* Tree node (workspace level) */
.tree-node {
  padding: 0.45rem 0.75rem;
  cursor: pointer;
  transition: background 0.15s ease;
  user-select: none;
}

.tree-node:hover {
  background: rgba(94, 100, 255, 0.04);
}

.tree-node-header {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.tree-expand-icon {
  font-size: 0.7rem;
  color: #9ca3af;
  width: 14px;
  text-align: center;
  flex-shrink: 0;
  transition: transform 0.15s ease;
}

.tree-node-name {
  font-weight: 600;
  font-size: 0.88rem;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.tree-node-count {
  font-size: 0.78rem;
  color: #9ca3af;
  flex-shrink: 0;
}

.current-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #5e64ff;
  box-shadow: 0 0 0 2px rgba(94, 100, 255, 0.2);
  flex-shrink: 0;
}

/* Quick Analysis node */
.tree-node-quick {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.04) 0%, rgba(118, 75, 162, 0.04) 100%);
  margin: 0 0.4rem 0.25rem;
  border-radius: 6px;
}

.tree-node-quick:hover {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
}

.tree-node-quick.active {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.14) 0%, rgba(118, 75, 162, 0.14) 100%);
}

.tree-node-icon-quick {
  font-size: 0.82rem;
  color: #667eea;
  flex-shrink: 0;
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
  border-left-color: #5e64ff;
}

.tree-child.active .tree-child-name {
  color: #5e64ff;
  font-weight: 600;
}

.tree-child-name {
  font-size: 0.85rem;
  color: #5e6e82;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.tree-child-count {
  font-size: 0.75rem;
  color: #9ca3af;
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
  color: #7c8db0;
}

.profile-content-header {
  color: #8e99a8;
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
  background: linear-gradient(135deg, #ffffff, #fafbff);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.profile-card:hover {
  border-color: rgba(94, 100, 255, 0.25);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.08);
}

.profile-card.selected {
  border-color: #5e64ff;
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.15);
}

.profile-card.primary {
  opacity: 0.55;
  pointer-events: none;
  background: #fef9ec;
  border-color: rgba(245, 158, 11, 0.2);
}

.profile-card-indicator {
  padding-top: 0.15rem;
  flex-shrink: 0;
  font-size: 1rem;
  color: #c8cdd5;
}

.profile-card.selected .profile-card-indicator {
  color: #5e64ff;
}

.profile-card-body {
  flex: 1;
  min-width: 0;
}

.profile-card-name {
  font-weight: 600;
  font-size: 0.92rem;
  color: #374151;
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
  color: #9ca3af;
  flex-wrap: wrap;
}

.meta-separator {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #d1d5db;
  flex-shrink: 0;
}

.primary-badge {
  display: inline-flex;
  align-items: center;
  font-size: 0.65rem;
  font-weight: 600;
  color: #92400e;
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
  color: #4c52ff;
  font-weight: 600;
  font-size: 0.82rem;
  padding: 0.35rem 0.75rem;
  border-radius: 8px;
  border: 1px solid rgba(94, 100, 255, 0.12);
}

.chip-icon {
  color: #5e64ff;
  font-size: 0.8rem;
}

.no-selection {
  color: #9ca3af;
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
  background: linear-gradient(135deg, #f8f9fa, #e9ecef);
  border: 1px solid rgba(108, 117, 125, 0.2);
  color: #6c757d;
}

.btn-cancel:hover {
  background: linear-gradient(135deg, #e9ecef, #dee2e6);
  color: #495057;
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
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border: none;
  color: #ffffff;
  font-weight: 600;
  box-shadow:
    0 4px 12px rgba(94, 100, 255, 0.3),
    0 2px 4px rgba(94, 100, 255, 0.2);
}

.btn-select-profile:hover:not(:disabled) {
  background: linear-gradient(135deg, #4c52ff, #3f46ff);
  color: #ffffff;
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
