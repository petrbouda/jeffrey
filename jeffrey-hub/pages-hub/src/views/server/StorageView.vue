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
    <div class="storage-view">
        <div class="page-header">
            <div class="header-left">
                <img src="/jeffrey-icon.svg" alt="Jeffrey" class="header-logo">
                <h4>Jeffrey Hub</h4>
                <span v-if="version" class="version-badge">{{ version }}</span>
            </div>
            <nav class="header-nav">
                <router-link to="/" class="nav-tab">Workspaces</router-link>
                <router-link to="/scheduler" class="nav-tab">Scheduler</router-link>
                <router-link to="/storage" class="nav-tab">Storage</router-link>
                <router-link to="/api-docs" class="nav-tab">API Documentation</router-link>
            </nav>
        </div>

        <div v-if="loading" class="loading-state">
            <div class="spinner-border spinner-border-sm text-secondary" role="status"></div>
            <span>Computing storage usage...</span>
        </div>

        <div v-else-if="error" class="empty-state">
            <i class="bi bi-exclamation-triangle"></i>
            <span>Failed to load storage usage</span>
            <span class="empty-hint">{{ error }}</span>
        </div>

        <template v-else-if="overview">
            <div class="section-card summary-strip">
                <div class="summary-kv">
                    <div class="summary-key">Total used</div>
                    <div class="summary-value">{{ formatBytes(totalUsedBytes) }}</div>
                </div>
                <div class="summary-kv">
                    <div class="summary-key">Projects</div>
                    <div class="summary-value">{{ overview.projects.length }}</div>
                </div>
                <div class="summary-kv">
                    <div class="summary-key">Files</div>
                    <div class="summary-value">{{ totalFiles.toLocaleString() }}</div>
                </div>
                <div v-if="overview.diskTotalBytes > 0" class="volume-meter">
                    <div class="volume-label">
                        <span>Volume usage</span>
                        <span class="volume-figures">
                            {{ formatBytes(volumeUsedBytes) }} / {{ formatBytes(overview.diskTotalBytes) }}
                            · {{ formatBytes(overview.diskUsableBytes) }} free
                        </span>
                    </div>
                    <div class="volume-bar">
                        <div class="volume-jeffrey" :style="{ width: volumeJeffreyPct + '%' }"></div>
                        <div class="volume-others" :style="{ width: volumeOthersPct + '%' }"></div>
                    </div>
                </div>
            </div>

            <div v-if="overview.projects.length === 0" class="empty-state">
                <i class="bi bi-hdd"></i>
                <span>No project data stored yet</span>
                <span class="empty-hint">Repositories fill up as connected applications record sessions</span>
            </div>

            <div v-else class="section-card">
                <table class="storage-table">
                    <thead>
                    <tr>
                        <th class="sortable" @click="sortBy('workspace')">
                            Workspace <span class="sort-arrow">{{ sortArrow('workspace') }}</span>
                        </th>
                        <th class="sortable" @click="sortBy('project')">
                            Project <span class="sort-arrow">{{ sortArrow('project') }}</span>
                        </th>
                        <th class="sortable text-end" @click="sortBy('size')">
                            Size <span class="sort-arrow">{{ sortArrow('size') }}</span>
                        </th>
                        <th class="sortable text-end" @click="sortBy('files')">
                            Files <span class="sort-arrow">{{ sortArrow('files') }}</span>
                        </th>
                        <th>Categories</th>
                        <th class="text-end">% of total</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="row in sortedProjects" :key="row.projectId">
                        <td>
                            <span class="workspace-cell">
                                <span class="workspace-dot" :style="{ background: workspaceColor(row.workspaceId) }"></span>
                                {{ row.workspaceName }}
                            </span>
                        </td>
                        <td class="project-cell">{{ displayName(row) }}</td>
                        <td class="text-end size-cell">{{ formatBytes(row.totalSizeBytes) }}</td>
                        <td class="text-end files-cell">{{ row.totalFiles.toLocaleString() }}</td>
                        <td>
                            <span class="category-bar">
                                <span
                                    v-for="segment in categorySegments(row)"
                                    :key="segment.key"
                                    :style="{ width: segment.width + '%', background: segment.color }"
                                ></span>
                            </span>
                        </td>
                        <td>
                            <span class="share-cell">
                                <span class="share-track">
                                    <span class="share-fill" :style="{ width: shareOfLargestPct(row) + '%' }"></span>
                                </span>
                                <span class="share-value">{{ shareOfProjectsPct(row) }}</span>
                            </span>
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>

            <div class="category-legend">
                <span v-for="category in CATEGORIES" :key="category.key" class="legend-item">
                    <span class="workspace-dot" :style="{ background: category.color }"></span>{{ category.label }}
                </span>
                <span class="legend-spacer"></span>
                <span class="infra-note">
                    Infrastructure (outside projects):
                    database {{ formatBytes(overview.databaseSizeBytes) }}
                    · folder queue {{ formatBytes(overview.queueSizeBytes) }}
                    · temp {{ formatBytes(overview.tempSizeBytes) }}
                </span>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import StorageClient from '@/services/api/StorageClient';
import VersionClient from '@/services/api/VersionClient';
import type { ProjectStorage, StorageOverview } from '@/services/api/model/StorageOverview';

type SortColumn = 'workspace' | 'project' | 'size' | 'files';
type SortDirection = 'asc' | 'desc';

const CATEGORIES = [
    { key: 'jfr', label: 'JFR recordings', color: 'var(--color-primary)' },
    { key: 'heapDump', label: 'Heap dumps', color: 'var(--color-orange)' },
    { key: 'log', label: 'Logs', color: 'var(--color-teal)' },
    { key: 'other', label: 'Other files', color: 'var(--color-amber)' }
] as const;

// Fixed palette assigned to workspaces in order of first appearance
const WORKSPACE_COLORS = [
    'var(--color-primary)',
    'var(--color-orange)',
    'var(--color-teal)',
    'var(--color-purple)',
    'var(--color-amber)',
    'var(--color-danger)'
];

const storageClient = new StorageClient();
const versionClient = new VersionClient();

const loading = ref(true);
const error = ref<string | null>(null);
const overview = ref<StorageOverview | null>(null);
const version = ref<string>('');
const sortColumn = ref<SortColumn>('size');
const sortDirection = ref<SortDirection>('desc');

const formatBytes = (bytes: number) => FormattingService.formatBytesShort(bytes);

const displayName = (row: ProjectStorage) => {
    return row.projectLabel?.trim() ? row.projectLabel : row.projectName;
};

const projectsTotalBytes = computed(() => {
    if (!overview.value) {
        return 0;
    }
    return overview.value.projects.reduce((sum, p) => sum + p.totalSizeBytes, 0);
});

const totalUsedBytes = computed(() => {
    if (!overview.value) {
        return 0;
    }
    return projectsTotalBytes.value
        + overview.value.databaseSizeBytes
        + overview.value.queueSizeBytes
        + overview.value.tempSizeBytes;
});

const totalFiles = computed(() => {
    if (!overview.value) {
        return 0;
    }
    return overview.value.projects.reduce((sum, p) => sum + p.totalFiles, 0);
});

const maxProjectBytes = computed(() => {
    if (!overview.value) {
        return 0;
    }
    return overview.value.projects.reduce((max, p) => Math.max(max, p.totalSizeBytes), 0);
});

const volumeUsedBytes = computed(() => {
    if (!overview.value) {
        return 0;
    }
    return Math.max(0, overview.value.diskTotalBytes - overview.value.diskUsableBytes);
});

const volumeJeffreyPct = computed(() => {
    if (!overview.value || overview.value.diskTotalBytes === 0) {
        return 0;
    }
    return (totalUsedBytes.value / overview.value.diskTotalBytes) * 100;
});

const volumeOthersPct = computed(() => {
    if (!overview.value || overview.value.diskTotalBytes === 0) {
        return 0;
    }
    const othersBytes = Math.max(0, volumeUsedBytes.value - totalUsedBytes.value);
    return (othersBytes / overview.value.diskTotalBytes) * 100;
});

const workspaceColorIndex = computed(() => {
    const indexByWorkspace = new Map<string, number>();
    if (!overview.value) {
        return indexByWorkspace;
    }
    for (const project of overview.value.projects) {
        if (!indexByWorkspace.has(project.workspaceId)) {
            indexByWorkspace.set(project.workspaceId, indexByWorkspace.size);
        }
    }
    return indexByWorkspace;
});

const workspaceColor = (workspaceId: string) => {
    const index = workspaceColorIndex.value.get(workspaceId) ?? 0;
    return WORKSPACE_COLORS[index % WORKSPACE_COLORS.length];
};

const sortedProjects = computed(() => {
    if (!overview.value) {
        return [];
    }
    const direction = sortDirection.value === 'asc' ? 1 : -1;
    return [...overview.value.projects].sort((a, b) => {
        switch (sortColumn.value) {
            case 'workspace':
                return direction * a.workspaceName.localeCompare(b.workspaceName);
            case 'project':
                return direction * displayName(a).localeCompare(displayName(b));
            case 'files':
                return direction * (a.totalFiles - b.totalFiles);
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
        sortDirection.value = column === 'workspace' || column === 'project' ? 'asc' : 'desc';
    }
};

const sortArrow = (column: SortColumn) => {
    if (sortColumn.value !== column) {
        return '';
    }
    return sortDirection.value === 'asc' ? '▲' : '▼';
};

const categorySegments = (row: ProjectStorage) => {
    if (row.totalSizeBytes === 0) {
        return [];
    }
    const sizes: Record<(typeof CATEGORIES)[number]['key'], number> = {
        jfr: row.jfrSizeBytes,
        heapDump: row.heapDumpSizeBytes,
        log: row.logSizeBytes,
        other: row.otherSizeBytes
    };
    return CATEGORIES
        .filter(category => sizes[category.key] > 0)
        .map(category => ({
            key: category.key,
            color: category.color,
            width: (sizes[category.key] / row.totalSizeBytes) * 100
        }));
};

const shareOfLargestPct = (row: ProjectStorage) => {
    if (maxProjectBytes.value === 0) {
        return 0;
    }
    return (row.totalSizeBytes / maxProjectBytes.value) * 100;
};

const shareOfProjectsPct = (row: ProjectStorage) => {
    if (projectsTotalBytes.value === 0) {
        return FormattingService.formatPercentValue(0);
    }
    return FormattingService.formatPercentValue((row.totalSizeBytes / projectsTotalBytes.value) * 100);
};

const loadStorage = async () => {
    try {
        overview.value = await storageClient.overview();
        error.value = null;
    } catch (e) {
        console.error('Failed to load storage overview:', e);
        error.value = e instanceof Error ? e.message : 'Unexpected error while loading storage usage';
    } finally {
        loading.value = false;
    }
};

onMounted(() => {
    loadStorage();
    versionClient.getVersion()
        .then(v => { version.value = v; })
        .catch(err => console.error('Failed to load version:', err));
});
</script>

<style scoped>
.storage-view {
    max-width: 1100px;
    margin: 0 auto;
    padding: 32px 24px;
}

/* Header (shared style with the other hub views) */
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
.header-logo { width: 32px; height: 32px; }
.header-left h4 {
    margin: 0;
    font-weight: 600;
    color: var(--color-heading-dark);
}
.version-badge {
    font-size: 0.72rem;
    font-weight: 500;
    color: var(--color-slate-muted);
    background: var(--color-grey-bg);
    padding: 2px 8px;
    border-radius: 10px;
    font-variant-numeric: tabular-nums;
}
.header-nav {
    display: flex;
    gap: 2px;
    background: var(--color-grey-bg);
    border-radius: 8px;
    padding: 3px;
}
.nav-tab {
    padding: 6px 14px;
    border-radius: 6px;
    font-size: 0.78rem;
    font-weight: 500;
    color: var(--color-slate-muted);
    text-decoration: none;
    transition: all 0.15s ease;
}
.nav-tab:hover { color: var(--color-slate-text); }
.nav-tab.router-link-active {
    background: white;
    color: var(--color-primary);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.loading-state, .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 60px 20px;
    color: var(--color-text-light);
}
.empty-state i { font-size: 3rem; }
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

/* Summary strip */
.summary-strip {
    display: flex;
    align-items: center;
    gap: 28px;
    padding: 14px 18px;
    margin-bottom: 16px;
    flex-wrap: wrap;
}
.summary-key {
    font-size: 0.7rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--color-text-light);
}
.summary-value {
    font-size: 1.1rem;
    font-weight: 600;
    color: var(--color-heading-dark);
    font-variant-numeric: tabular-nums;
}
.volume-meter {
    flex: 1;
    min-width: 240px;
}
.volume-label {
    display: flex;
    justify-content: space-between;
    font-size: 0.72rem;
    color: var(--color-slate-muted);
    margin-bottom: 5px;
}
.volume-figures { font-variant-numeric: tabular-nums; }
.volume-bar {
    display: flex;
    gap: 2px;
    height: 10px;
    border-radius: 4px;
    overflow: hidden;
    background: var(--color-grey-bg);
}
.volume-jeffrey { background: var(--color-primary); }
.volume-others { background: var(--color-slate-light); }

/* Table */
.storage-table {
    width: 100%;
    border-collapse: collapse;
}
.storage-table thead th {
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
.storage-table thead th.sortable {
    cursor: pointer;
    user-select: none;
}
.sort-arrow {
    color: var(--color-primary);
    font-size: 0.6rem;
}
.storage-table tbody td {
    padding: 11px 18px;
    border-bottom: 1px solid var(--color-grey-bg);
    font-size: 0.82rem;
    color: var(--color-heading-dark);
    vertical-align: middle;
}
.storage-table tbody tr:last-child td { border-bottom: none; }
.storage-table tbody tr:hover { background: var(--color-light); }

.workspace-cell {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    color: var(--color-slate-muted);
    font-size: 0.78rem;
}
.workspace-dot {
    width: 9px;
    height: 9px;
    border-radius: 3px;
    flex: none;
    display: inline-block;
}
.project-cell { font-weight: 600; }
.size-cell {
    font-weight: 600;
    font-variant-numeric: tabular-nums;
}
.files-cell {
    color: var(--color-slate-muted);
    font-variant-numeric: tabular-nums;
}

.category-bar {
    display: flex;
    gap: 1px;
    width: 130px;
    height: 8px;
    border-radius: 3px;
    overflow: hidden;
    background: var(--color-grey-bg);
}

.share-cell {
    display: flex;
    align-items: center;
    gap: 8px;
    justify-content: flex-end;
}
.share-track {
    width: 70px;
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

/* Legend + infrastructure footnote */
.category-legend {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
    margin: 10px 4px 0;
    font-size: 0.74rem;
    color: var(--color-slate-muted);
}
.legend-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}
.legend-spacer { flex: 1; }
.infra-note { font-variant-numeric: tabular-nums; }
</style>
