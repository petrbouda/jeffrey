<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div class="server-dashboard">
    <HubPageHeader/>

    <div v-if="loading" class="loading-state">
      <div class="spinner-border spinner-border-sm text-secondary" role="status"></div>
      <span>Loading workspaces...</span>
    </div>

    <div v-else-if="error" class="empty-state">
      <i class="bi bi-exclamation-triangle"></i>
      <span>Failed to load workspaces</span>
      <span class="empty-hint">{{ error }}</span>
    </div>

    <div v-else-if="workspaceRows.length === 0" class="empty-state">
      <i class="bi bi-inbox"></i>
      <span>No workspaces registered</span>
      <span class="empty-hint">Workspaces are created automatically when applications connect</span>
    </div>

    <div v-else class="dashboard-layout">
      <!-- Workspace rail -->
      <aside>
        <div class="section-card total-card">
          <div class="total-key">
            Hub total
            <span class="total-refresh">
              <span class="total-ago" :title="'Recompute from the Scheduler page'">{{ storageComputedAgo }}</span>
            </span>
          </div>
          <div class="total-value">{{ formatBytes(totalUsedBytes) }}</div>
          <div class="total-sub">
            {{ totalProjects }} {{ totalProjects === 1 ? 'project' : 'projects' }}
            · {{ totalFiles.toLocaleString() }} files
          </div>
        </div>

        <div class="section-card">
          <div
              v-for="row in workspaceRows"
              :key="row.workspace.id"
              class="workspace-item"
              :class="{ selected: row.workspace.id === selectedWorkspaceId }"
              @click="selectWorkspace(row.workspace.id)"
          >
            <div class="workspace-item-top">
              <i class="bi bi-hdd-rack workspace-icon"></i>
              <span class="workspace-name">{{ row.workspace.name }}</span>
              <span class="project-count">{{ row.projects.length }}</span>
            </div>
            <div class="workspace-item-bottom">
              <span class="share-track">
                <span class="share-fill" :style="{ width: shareOfHubValue(row) + '%' }"></span>
              </span>
              <span class="workspace-size">{{ formatBytes(row.totalSizeBytes) }}</span>
            </div>
          </div>
        </div>
      </aside>

      <!-- Workspace detail -->
      <section v-if="selectedWorkspace">
        <div class="section-card">
          <div class="detail-head">
            <span class="detail-name">{{ selectedWorkspace.workspace.name }}</span>
            <span class="project-count">
              {{ selectedWorkspace.projects.length }}
              {{ selectedWorkspace.projects.length === 1 ? 'project' : 'projects' }}
            </span>
            <span class="detail-stats">
              <span class="detail-stat">
                <div class="stat-key">Size</div>
                <div class="stat-value">{{ formatBytes(selectedWorkspace.totalSizeBytes) }}</div>
              </span>
              <span class="detail-stat">
                <div class="stat-key">Files</div>
                <div class="stat-value">{{ selectedWorkspace.totalFiles.toLocaleString() }}</div>
              </span>
              <span class="detail-stat">
                <div class="stat-key">Of hub total</div>
                <div class="stat-value">{{ shareOfHubPct(selectedWorkspace) }}</div>
              </span>
            </span>
          </div>

          <div v-if="selectedWorkspace.projects.length === 0" class="empty-state">
            <i class="bi bi-hdd"></i>
            <span>No projects in this workspace</span>
            <span class="empty-hint">Projects appear when applications connect to this workspace</span>
          </div>

          <table v-else class="projects-table">
            <thead>
            <tr>
              <th class="sortable" @click="sortBy('project')">
                Project <span class="sort-arrow">{{ sortArrow('project') }}</span>
              </th>
              <th class="sortable text-end" @click="sortBy('size')">
                Size <span class="sort-arrow">{{ sortArrow('size') }}</span>
              </th>
              <th class="sortable text-end" @click="sortBy('files')">
                Files <span class="sort-arrow">{{ sortArrow('files') }}</span>
              </th>
              <th class="sortable text-end" @click="sortBy('activity')">
                Last activity <span class="sort-arrow">{{ sortArrow('activity') }}</span>
              </th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="row in sortedProjects" :key="row.projectId">
              <td>
                <span class="project-cell">
                  <span class="status-dot" :class="{ inactive: !row.active }"></span>
                  <span class="project-name" :class="{ inactive: !row.active }">{{ row.name }}</span>
                </span>
              </td>
              <td>
                <span class="size-cell">
                  <span class="share-track">
                    <span class="share-fill" :style="{ width: shareOfLargestPct(row) + '%' }"></span>
                  </span>
                  <span class="size-value">{{ formatBytes(row.totalSizeBytes) }}</span>
                  <span class="share-value">{{ shareOfWorkspacePct(row) }}</span>
                </span>
              </td>
              <td class="text-end files-cell">{{ row.totalFiles.toLocaleString() }}</td>
              <td class="text-end activity-cell">{{ lastActivity(row) }}</td>
            </tr>
            </tbody>
          </table>
        </div>

        <div class="infra-note">
          Infrastructure (outside projects):
          database {{ formatBytes(overview?.databaseSizeBytes ?? 0) }}
          · temp {{ formatBytes(overview?.tempSizeBytes ?? 0) }}
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import HubPageHeader from '@/components/HubPageHeader.vue';
import StorageClient from '@/services/api/StorageClient';
import WorkspaceClient from '@/services/api/WorkspaceClient';
import WorkspaceProjectsClient from '@/services/api/WorkspaceProjectsClient';
import RecordingStatus from '@/services/api/model/RecordingStatus';
import type Workspace from '@/services/api/model/Workspace';
import type Project from '@/services/api/model/Project';
import ProjectModel from '@/services/api/model/Project';
import type { ProjectStorage, StorageOverview } from '@/services/api/model/StorageOverview';

type SortColumn = 'project' | 'size' | 'files' | 'activity';
type SortDirection = 'asc' | 'desc';

/** One project row in the detail table: the live project merged with its storage figures. */
interface ProjectRow {
  projectId: string;
  name: string;
  active: boolean;
  totalSizeBytes: number;
  totalFiles: number;
  lastActivityTimeMillis: number;
  storage: ProjectStorage;
}

interface WorkspaceRow {
  workspace: Workspace;
  projects: ProjectRow[];
  totalSizeBytes: number;
  totalFiles: number;
}

/** How often the "computed X ago" label re-evaluates while the page stays open. */
const RELATIVE_TIME_REFRESH_MS = 30_000;

const storageClient = new StorageClient();
const workspaceClient = new WorkspaceClient();

const loading = ref(true);
const error = ref<string | null>(null);
const relativeTimeTick = ref(0);
let relativeTimeTimer: number | null = null;
const overview = ref<StorageOverview | null>(null);
const workspaceRows = ref<WorkspaceRow[]>([]);
const selectedWorkspaceId = ref<string | null>(null);
const sortColumn = ref<SortColumn>('size');
const sortDirection = ref<SortDirection>('desc');

const formatBytes = (bytes: number) => FormattingService.formatBytesShort(bytes);

const storageComputedAgo = computed(() => {
  // The tick dependency re-evaluates the label periodically so it ages in place
  relativeTimeTick.value;
  return FormattingService.formatRelativeTime(overview.value?.computedAtMillis);
});

/** Placeholder storage for a project the storage scan has no data for (nothing recorded yet). */
const emptyStorage = (workspace: Workspace, project: Project): ProjectStorage => ({
  workspaceId: workspace.id,
  workspaceName: workspace.name,
  projectId: project.id,
  projectName: project.name,
  projectLabel: project.label,
  totalSizeBytes: 0,
  totalFiles: 0,
  lastActivityTimeMillis: 0
});

const toProjectRow = (storage: ProjectStorage, project: Project | undefined): ProjectRow => ({
  projectId: storage.projectId,
  name: project ? ProjectModel.displayName(project)
      : (storage.projectLabel?.trim() ? storage.projectLabel : storage.projectName),
  active: project?.status === RecordingStatus.ACTIVE,
  totalSizeBytes: storage.totalSizeBytes,
  totalFiles: storage.totalFiles,
  lastActivityTimeMillis: storage.lastActivityTimeMillis,
  storage
});

const buildWorkspaceRow = (
    workspace: Workspace,
    projects: Project[],
    storageByProjectId: Map<string, ProjectStorage>): WorkspaceRow => {

  const projectById = new Map(projects.map(p => [p.id, p]));

  // Projects known to the workspace, enriched with storage; storage-only rows
  // (project deleted meanwhile) are still listed so sizes always add up
  const rows: ProjectRow[] = projects.map(project => {
    const storage = storageByProjectId.get(project.id) ?? emptyStorage(workspace, project);
    return toProjectRow(storage, project);
  });
  for (const [projectId, storage] of storageByProjectId) {
    if (storage.workspaceId === workspace.id && !projectById.has(projectId)) {
      rows.push(toProjectRow(storage, undefined));
    }
  }

  return {
    workspace,
    projects: rows,
    totalSizeBytes: rows.reduce((sum, row) => sum + row.totalSizeBytes, 0),
    totalFiles: rows.reduce((sum, row) => sum + row.totalFiles, 0)
  };
};

const selectedWorkspace = computed(() => {
  return workspaceRows.value.find(row => row.workspace.id === selectedWorkspaceId.value) ?? null;
});

const selectWorkspace = (workspaceId: string) => {
  selectedWorkspaceId.value = workspaceId;
};

/** "3 minutes ago" for a project that has recorded something, a dash for one that never has. */
const lastActivity = (row: ProjectRow) => {
  // The tick dependency re-evaluates the labels periodically so they age in place
  relativeTimeTick.value;
  if (row.lastActivityTimeMillis === 0) {
    return '—';
  }
  return FormattingService.formatRelativeTime(row.lastActivityTimeMillis);
};

const projectsTotalBytes = computed(() => {
  return workspaceRows.value.reduce((sum, row) => sum + row.totalSizeBytes, 0);
});

const totalUsedBytes = computed(() => {
  if (!overview.value) {
    return projectsTotalBytes.value;
  }
  return projectsTotalBytes.value
      + overview.value.databaseSizeBytes
      + overview.value.tempSizeBytes;
});

const totalProjects = computed(() => {
  return workspaceRows.value.reduce((sum, row) => sum + row.projects.length, 0);
});

const totalFiles = computed(() => {
  return workspaceRows.value.reduce((sum, row) => sum + row.totalFiles, 0);
});

const shareOfHubValue = (row: WorkspaceRow) => {
  if (projectsTotalBytes.value === 0) {
    return 0;
  }
  return (row.totalSizeBytes / projectsTotalBytes.value) * 100;
};

const shareOfHubPct = (row: WorkspaceRow) => {
  return FormattingService.formatPercentValue(shareOfHubValue(row));
};

const maxProjectBytes = computed(() => {
  if (!selectedWorkspace.value) {
    return 0;
  }
  return selectedWorkspace.value.projects.reduce((max, row) => Math.max(max, row.totalSizeBytes), 0);
});

const shareOfLargestPct = (row: ProjectRow) => {
  if (maxProjectBytes.value === 0) {
    return 0;
  }
  return (row.totalSizeBytes / maxProjectBytes.value) * 100;
};

const shareOfWorkspacePct = (row: ProjectRow) => {
  const workspaceRow = workspaceRows.value.find(ws => ws.workspace.id === row.storage.workspaceId);
  if (!workspaceRow || workspaceRow.totalSizeBytes === 0) {
    return FormattingService.formatPercentValue(0);
  }
  return FormattingService.formatPercentValue((row.totalSizeBytes / workspaceRow.totalSizeBytes) * 100);
};

const sortedProjects = computed(() => {
  if (!selectedWorkspace.value) {
    return [];
  }
  const direction = sortDirection.value === 'asc' ? 1 : -1;
  return [...selectedWorkspace.value.projects].sort((a, b) => {
    switch (sortColumn.value) {
      case 'project':
        return direction * a.name.localeCompare(b.name);
      case 'files':
        return direction * (a.totalFiles - b.totalFiles);
      case 'activity':
        return direction * (a.lastActivityTimeMillis - b.lastActivityTimeMillis);
      default:
        return direction * (a.totalSizeBytes - b.totalSizeBytes);
    }
  });
});

const sortBy = (column: SortColumn) => {
  if (sortColumn.value === column) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortColumn.value = column;
    sortDirection.value = column === 'project' ? 'asc' : 'desc';
  }
};

const sortArrow = (column: SortColumn) => {
  if (sortColumn.value !== column) {
    return '';
  }
  return sortDirection.value === 'asc' ? '▲' : '▼';
};

const applyOverview = async (workspaces: Workspace[], storageOverview: StorageOverview) => {
  overview.value = storageOverview;

  const storageByProjectId = new Map(
      storageOverview.projects.map(project => [project.projectId, project]));

  // Fetch the project lists of all workspaces concurrently — a serial per-workspace
  // round-trip makes the dashboard load time grow linearly with the workspace count
  workspaceRows.value = await Promise.all(
      workspaces.map(async (workspace): Promise<WorkspaceRow> => {
        const projectsClient = new WorkspaceProjectsClient(workspace.id);
        try {
          const projects = await projectsClient.list();
          return buildWorkspaceRow(workspace, projects, storageByProjectId);
        } catch {
          return buildWorkspaceRow(workspace, [], storageByProjectId);
        }
      })
  );

  // Preselect the first workspace that has projects, falling back to the first one,
  // but keep the current selection when it survived the reload
  if (!workspaceRows.value.some(row => row.workspace.id === selectedWorkspaceId.value)) {
    const withProjects = workspaceRows.value.find(row => row.projects.length > 0);
    selectedWorkspaceId.value = (withProjects ?? workspaceRows.value[0])?.workspace.id ?? null;
  }
};

const loadDashboard = async () => {
  try {
    const [workspaces, storageOverview] = await Promise.all([
      workspaceClient.list(),
      storageClient.overview()
    ]);
    await applyOverview(workspaces, storageOverview);
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
  relativeTimeTimer = window.setInterval(() => {
    relativeTimeTick.value++;
  }, RELATIVE_TIME_REFRESH_MS);
});

onUnmounted(() => {
  if (relativeTimeTimer !== null) {
    window.clearInterval(relativeTimeTimer);
  }
});
</script>

<style scoped>
.server-dashboard {
  max-width: 1240px;
  margin: 0 auto;
  padding: 32px 24px;
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

.section-card {
  background: white;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  overflow: hidden;
}

/* Age of the cached storage snapshot, shown inside the Hub-total card */
.total-refresh {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.total-ago {
  font-size: 0.73rem;
  font-weight: 400;
  text-transform: none;
  letter-spacing: normal;
  color: var(--color-slate-muted);
  font-variant-numeric: tabular-nums;
}

/* Layout */
.dashboard-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  align-items: start;
}

@media (max-width: 900px) {
  .dashboard-layout {
    grid-template-columns: 1fr;
  }
}

/* Rail: hub total */
.total-card {
  padding: 14px 16px;
  margin-bottom: 12px;
}

.total-key {
  display: flex;
  align-items: center;
  font-size: 0.68rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-light);
  margin-bottom: 6px;
}

.total-value {
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--color-heading-dark);
  font-variant-numeric: tabular-nums;
}

.total-sub {
  font-size: 0.75rem;
  color: var(--color-slate-muted);
  font-variant-numeric: tabular-nums;
}

/* Rail: workspace list */
.workspace-item {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--color-grey-bg);
  user-select: none;
}

.workspace-item:last-child {
  border-bottom: none;
}

.workspace-item:hover {
  background: var(--color-light);
}

.workspace-item.selected {
  background: var(--color-primary-lighter);
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.workspace-item-top {
  display: flex;
  align-items: center;
  gap: 9px;
}

.workspace-icon {
  font-size: 1rem;
  color: var(--color-primary);
}

.workspace-name {
  font-weight: 600;
  font-size: 0.88rem;
  color: var(--color-heading-dark);
}

.project-count {
  margin-left: auto;
  font-size: 0.73rem;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-primary-light);
  padding: 2px 8px;
  border-radius: 10px;
}

.workspace-item-bottom {
  display: flex;
  align-items: center;
  gap: 10px;
}

.workspace-item-bottom .share-track {
  flex: 1;
}

.workspace-size {
  font-size: 0.76rem;
  color: var(--color-slate-muted);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* Detail: header */
.detail-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px 18px;
  border-bottom: 1px solid var(--color-grey-bg);
  flex-wrap: wrap;
}

.detail-name {
  font-size: 1.02rem;
  font-weight: 600;
  color: var(--color-heading-dark);
}

.detail-stats {
  margin-left: auto;
  display: flex;
  gap: 22px;
}

.stat-key {
  font-size: 0.66rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-light);
}

.stat-value {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--color-heading-dark);
  font-variant-numeric: tabular-nums;
}

/* Detail: table */
.projects-table {
  width: 100%;
  border-collapse: collapse;
}

.projects-table thead th {
  text-align: left;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-light);
  padding: 10px 18px;
  background: var(--color-light);
  border-bottom: 1px solid var(--color-grey-bg);
  white-space: nowrap;
}

.projects-table thead th.sortable {
  cursor: pointer;
  user-select: none;
}

.sort-arrow {
  color: var(--color-primary);
  font-size: 0.6rem;
}

.projects-table tbody td {
  padding: 11px 18px;
  border-bottom: 1px solid var(--color-grey-bg);
  font-size: 0.82rem;
  color: var(--color-heading-dark);
  vertical-align: middle;
}

.projects-table tbody tr:last-child td {
  border-bottom: none;
}

.projects-table tbody tr:hover td {
  background: var(--color-light);
}

.project-cell {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-emerald);
  flex-shrink: 0;
}

.status-dot.inactive {
  background: var(--color-slate-light);
}

.project-name {
  font-weight: 600;
}

.project-name.inactive {
  color: var(--color-slate-muted);
  font-weight: 400;
}

/* Size column: bar scaled to the workspace's largest project, the bytes, the share of the workspace */
.size-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
}

.size-cell .share-track {
  width: 90px;
}

.size-value {
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.files-cell,
.activity-cell {
  color: var(--color-slate-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.share-track {
  height: 6px;
  border-radius: 3px;
  background: var(--color-grey-bg);
  overflow: hidden;
  display: inline-block;
}

.share-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: var(--color-primary);
}

.share-value {
  color: var(--color-slate-muted);
  font-size: 0.78rem;
  font-variant-numeric: tabular-nums;
  min-width: 44px;
  text-align: right;
}

/* Infrastructure footnote */
.infra-note {
  margin: 10px 4px 0;
  font-size: 0.74rem;
  color: var(--color-slate-muted);
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
