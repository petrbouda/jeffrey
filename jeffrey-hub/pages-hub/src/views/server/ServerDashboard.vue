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
  <div class="server-dashboard">
    <HubHeader />

    <div v-if="!loading && workspaces.length > 0" class="search-container">
      <i class="bi bi-search search-icon"></i>
      <input
          v-model="searchQuery"
          type="text"
          class="search-input"
          placeholder="Filter projects..."
      >
      <button v-if="searchQuery" class="clear-btn" @click="searchQuery = ''">
        <i class="bi bi-x-lg"></i>
      </button>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="spinner-border spinner-border-sm text-secondary" role="status"></div>
      <span>Loading workspaces...</span>
    </div>

    <div v-else-if="error" class="empty-state">
      <i class="bi bi-exclamation-triangle"></i>
      <span>Failed to load workspaces</span>
      <span class="empty-hint">{{ error }}</span>
    </div>

    <div v-else-if="workspaces.length === 0" class="empty-state">
      <i class="bi bi-inbox"></i>
      <span>No workspaces registered</span>
      <span class="empty-hint">Workspaces are created automatically when applications connect</span>
    </div>

    <div v-else class="workspaces-list">
      <div v-for="ws in filteredWorkspaces" :key="ws.workspace.id" class="workspace-section">
        <div class="workspace-header" @click="toggleWorkspace(ws.workspace.id)">
          <i class="bi chevron-icon"
             :class="isExpanded(ws.workspace.id) ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
          <i class="bi bi-hdd-rack workspace-icon"></i>
          <span class="workspace-name">{{ ws.workspace.name }}</span>
          <span class="project-count">{{ ws.projects.length }}</span>
        </div>

        <template v-if="isExpanded(ws.workspace.id)">
          <div v-if="ws.projects.length === 0" class="no-projects">
            No projects
          </div>

          <div v-else class="project-list">
            <div
                v-for="project in ws.projects"
                :key="project.id"
                class="project-row"
                :class="{ 'project-active': isActive(project) }"
            >
              <span v-if="isActive(project)" class="active-dot"></span>
              <span class="project-name">{{ displayName(project) }}</span>
            </div>
          </div>
        </template>
      </div>

      <div v-if="filteredWorkspaces.length === 0" class="empty-state">
        <i class="bi bi-search"></i>
        <span>No projects matching "{{ searchQuery }}"</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import HubHeader from '@/components/HubHeader.vue';
import WorkspaceClient from '@/services/api/WorkspaceClient';
import WorkspaceProjectsClient from '@/services/api/WorkspaceProjectsClient';
import type Workspace from '@/services/api/model/Workspace';
import type Project from '@/services/api/model/Project';
import ProjectModel from '@/services/api/model/Project';

interface WorkspaceWithProjects {
  workspace: Workspace;
  projects: Project[];
}

const workspaceClient = new WorkspaceClient();
const loading = ref(true);
const error = ref<string | null>(null);
const workspaces = ref<WorkspaceWithProjects[]>([]);
const searchQuery = ref('');
const expandedWorkspaces = ref(new Set<string>());

const displayName = (project: Project) => ProjectModel.displayName(project);
const isActive = (project: Project) => project.status === 'ACTIVE';
const isExpanded = (workspaceId: string) => expandedWorkspaces.value.has(workspaceId);

const toggleWorkspace = (workspaceId: string) => {
  const updated = new Set(expandedWorkspaces.value);
  if (updated.has(workspaceId)) {
    updated.delete(workspaceId);
  } else {
    updated.add(workspaceId);
  }
  expandedWorkspaces.value = updated;
};

const filteredWorkspaces = computed(() => {
  const query = searchQuery.value.toLowerCase().trim();
  if (!query) {
    return workspaces.value;
  }

  return workspaces.value
      .map(ws => ({
        workspace: ws.workspace,
        projects: ws.projects.filter(p =>
            ProjectModel.displayName(p).toLowerCase().includes(query)
        )
      }))
      .filter(ws => ws.projects.length > 0);
});

const loadDashboard = async () => {
  try {
    const allWorkspaces = await workspaceClient.list();

    // Fetch the project lists of all workspaces concurrently — a serial per-workspace
    // round-trip makes the dashboard load time grow linearly with the workspace count
    workspaces.value = await Promise.all(
        allWorkspaces.map(async (ws): Promise<WorkspaceWithProjects> => {
          const projectsClient = new WorkspaceProjectsClient(ws.id);
          try {
            return { workspace: ws, projects: await projectsClient.list() };
          } catch {
            return { workspace: ws, projects: [] };
          }
        })
    );
    error.value = null;
  } catch (e) {
    console.error('Failed to load dashboard:', e);
    error.value = e instanceof Error ? e.message : 'Unexpected error while loading workspaces';
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadDashboard();
});
</script>

<style scoped>
.server-dashboard {
  max-width: 1100px;
  margin: 0 auto;
  padding: 32px 24px;
}

/* Search */
.search-container {
  position: relative;
  margin-bottom: 20px;
}

.search-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-text-light);
  font-size: 0.85rem;
  pointer-events: none;
}

.search-input {
  width: 100%;
  height: 42px;
  padding: 0 36px 0 38px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 0.88rem;
  color: var(--color-heading-dark);
  background: white;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.search-input::placeholder {
  color: var(--color-text-light);
}

.search-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.1);
}

.clear-btn {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--color-text-light);
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
}

.clear-btn:hover {
  color: var(--color-slate-muted);
}

/* Loading / Empty */
.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 60px 20px;
  color: var(--color-text-light);
}

.empty-state i {
  font-size: 3rem;
}

.empty-hint {
  font-size: 0.8rem;
  color: var(--color-muted-separator);
}

/* Workspaces */
.workspaces-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.workspace-section {
  background: white;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  overflow: hidden;
}

.workspace-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-grey-bg);
  cursor: pointer;
  user-select: none;
  transition: background-color 0.15s ease;
}

.workspace-header:hover {
  background-color: var(--color-neutral-bg);
}

.chevron-icon {
  font-size: 0.75rem;
  color: var(--color-text-light);
  transition: transform 0.15s ease;
}

.workspace-icon {
  font-size: 1.1rem;
  color: var(--color-primary);
}

.workspace-name {
  font-weight: 600;
  font-size: 0.95rem;
  color: var(--color-heading-dark);
}

.project-count {
  margin-left: auto;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-primary);
  background: rgba(94, 100, 255, 0.08);
  padding: 2px 8px;
  border-radius: 10px;
}

.no-projects {
  padding: 20px 18px;
  font-size: 0.85rem;
  color: var(--color-text-light);
}

.project-list {
  padding: 6px 0;
}

.project-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  color: var(--color-slate-muted);
  font-size: 0.9rem;
}

.project-row.project-active {
  color: var(--color-heading-dark);
  font-weight: 500;
}

.active-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-emerald);
  flex-shrink: 0;
}

.project-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
