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
    <!-- Header with logo and nav -->
    <div class="page-header">
      <div class="header-left">
        <img src="/jeffrey-icon.svg" alt="Jeffrey" class="header-logo">
        <h4>Jeffrey Server</h4>
        <span v-if="version" class="version-badge">{{ version }}</span>
      </div>
      <nav class="header-nav">
        <router-link to="/" class="nav-tab">Workspaces</router-link>
        <router-link to="/api-docs" class="nav-tab">API Documentation</router-link>
      </nav>
    </div>

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
import WorkspaceClient from '@/services/api/WorkspaceClient';
import WorkspaceProjectsClient from '@/services/api/WorkspaceProjectsClient';
import VersionClient from '@/services/api/VersionClient';
import type Workspace from '@/services/api/model/Workspace';
import type Project from '@/services/api/model/Project';
import ProjectModel from '@/services/api/model/Project';

interface WorkspaceWithProjects {
  workspace: Workspace;
  projects: Project[];
}

const workspaceClient = new WorkspaceClient();
const versionClient = new VersionClient();
const loading = ref(true);
const workspaces = ref<WorkspaceWithProjects[]>([]);
const searchQuery = ref('');
const expandedWorkspaces = ref(new Set<string>());
const version = ref<string>('');

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

    const results: WorkspaceWithProjects[] = [];
    for (const ws of allWorkspaces) {
      const projectsClient = new WorkspaceProjectsClient(ws.id);
      try {
        const projects = await projectsClient.list();
        results.push({ workspace: ws, projects });
      } catch {
        results.push({ workspace: ws, projects: [] });
      }
    }

    workspaces.value = results;
  } catch (error) {
    console.error('Failed to load dashboard:', error);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadDashboard();
  versionClient.getVersion()
      .then(v => { version.value = v; })
      .catch(err => console.error('Failed to load version:', err));
});
</script>

<style scoped>
.server-dashboard {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 24px;
}

/* Header (shared style with GrpcApiDocs) */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-logo {
  width: 32px;
  height: 32px;
}

.header-left h4 {
  margin: 0;
  font-weight: 600;
  color: #1f2937;
}

.version-badge {
  font-size: 0.72rem;
  font-weight: 500;
  color: #6b7280;
  background: #f3f4f6;
  padding: 2px 8px;
  border-radius: 10px;
  font-variant-numeric: tabular-nums;
}

.header-nav {
  display: flex;
  gap: 2px;
  background: #f3f4f6;
  border-radius: 8px;
  padding: 3px;
}

.nav-tab {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 500;
  color: #6b7280;
  text-decoration: none;
  transition: all 0.15s ease;
}

.nav-tab:hover {
  color: #374151;
}

.nav-tab.router-link-active {
  background: white;
  color: #5e64ff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
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
  color: #9ca3af;
  font-size: 0.85rem;
  pointer-events: none;
}

.search-input {
  width: 100%;
  height: 42px;
  padding: 0 36px 0 38px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 0.88rem;
  color: #1f2937;
  background: white;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.search-input::placeholder {
  color: #9ca3af;
}

.search-input:focus {
  border-color: #5e64ff;
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.1);
}

.clear-btn {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
}

.clear-btn:hover {
  color: #6b7280;
}

/* Loading / Empty */
.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 60px 20px;
  color: #9ca3af;
}

.empty-state i {
  font-size: 3rem;
}

.empty-hint {
  font-size: 0.8rem;
  color: #d1d5db;
}

/* Workspaces */
.workspaces-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.workspace-section {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
}

.workspace-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
  user-select: none;
  transition: background-color 0.15s ease;
}

.workspace-header:hover {
  background-color: #f9fafb;
}

.chevron-icon {
  font-size: 0.75rem;
  color: #9ca3af;
  transition: transform 0.15s ease;
}

.workspace-icon {
  font-size: 1.1rem;
  color: #5e64ff;
}

.workspace-name {
  font-weight: 600;
  font-size: 0.95rem;
  color: #1f2937;
}

.project-count {
  margin-left: auto;
  font-size: 0.75rem;
  font-weight: 600;
  color: #5e64ff;
  background: rgba(94, 100, 255, 0.08);
  padding: 2px 8px;
  border-radius: 10px;
}

.no-projects {
  padding: 20px 18px;
  font-size: 0.85rem;
  color: #9ca3af;
}

.project-list {
  padding: 6px 0;
}

.project-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  color: #6b7280;
  font-size: 0.9rem;
}

.project-row.project-active {
  color: #1f2937;
  font-weight: 500;
}

.active-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  flex-shrink: 0;
}

.project-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
