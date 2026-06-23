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
    <div class="scheduler-view">
        <div class="page-header">
            <div class="header-left">
                <img src="/jeffrey-icon.svg" alt="Jeffrey" class="header-logo">
                <h4>Jeffrey Hub</h4>
                <span v-if="version" class="version-badge">{{ version }}</span>
            </div>
            <nav class="header-nav">
                <router-link to="/" class="nav-tab">Workspaces</router-link>
                <router-link to="/scheduler" class="nav-tab">Scheduler</router-link>
                <router-link to="/api-docs" class="nav-tab">API Documentation</router-link>
            </nav>
        </div>

        <div class="hint">
            <i class="bi bi-info-circle"></i>
            <span>Read-only view. To change a job's settings, edit <code>application.properties</code> and restart the server.</span>
        </div>

        <div v-if="loading" class="loading-state">
            <div class="spinner-border spinner-border-sm text-secondary" role="status"></div>
            <span>Loading scheduler jobs...</span>
        </div>

        <div v-else-if="error" class="empty-state">
            <i class="bi bi-exclamation-triangle"></i>
            <span>Failed to load scheduler jobs</span>
            <span class="empty-hint">{{ error }}</span>
        </div>

        <template v-else>
            <div class="filter-bar">
                <button
                    v-for="filter in filters"
                    :key="filter.value"
                    class="filter-chip"
                    :class="{ active: activeFilter === filter.value }"
                    @click="activeFilter = filter.value"
                >
                    {{ filter.label }} <span class="chip-count">{{ counts[filter.value] }}</span>
                </button>
                <div class="filter-spacer"></div>
                <span class="filter-meta">{{ enabledCount }} enabled · {{ disabledCount }} disabled</span>
            </div>

            <div class="section-card">
                <table class="jobs-table">
                    <thead>
                    <tr>
                        <th style="width: 32%">Job</th>
                        <th>Execution</th>
                        <th style="width: 9%">Period</th>
                        <th>Parameters</th>
                        <th style="width: 12%">Status</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="job in filteredJobs" :key="job.jobType">
                        <td>
                            <div class="job-name-cell">
                                <div class="job-icon-box" :class="iconClass(job.jobType)">
                                    <i class="bi" :class="iconFor(job.jobType)"></i>
                                </div>
                                <div class="job-name">{{ displayNameFor(job.jobType) }}</div>
                                <div class="job-tooltip" role="tooltip">
                                    <p class="job-tooltip-title">{{ displayNameFor(job.jobType) }}</p>
                                    {{ descriptionFor(job.jobType) }}
                                </div>
                            </div>
                        </td>
                        <td>
                            <Badge
                                :value="levelLabel(job.executionLevel)"
                                :variant="levelVariant(job.executionLevel)"
                                size="xs"
                            />
                        </td>
                        <td class="period-cell">{{ formatPeriod(job.period) }}</td>
                        <td class="params-cell">
                            <template v-if="hasParams(job.params)">
                                <span v-for="(value, key) in job.params" :key="key" class="param-pair">
                                    <span class="pkey">{{ key }}=</span>{{ value }}
                                </span>
                            </template>
                            <span v-else class="pdash">—</span>
                        </td>
                        <td>
                            <Badge
                                v-if="job.enabled"
                                value="Enabled"
                                variant="green"
                                size="xs"
                                icon="bi bi-check-circle-fill"
                                :uppercase="false"
                            />
                            <Badge
                                v-else
                                value="Disabled"
                                variant="grey"
                                size="xs"
                                icon="bi bi-pause-circle"
                                :uppercase="false"
                            />
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>

            <div class="section-card config-source">
                <div class="config-source-header">
                    <i class="bi bi-file-earmark-code config-source-icon"></i>
                    <span class="config-source-title">Resolved configuration</span>
                    <span class="config-source-hint">
                        Override any key below in <code>application.properties</code> and restart the server.
                    </span>
                </div>
                <pre class="config-source-pre">{{ resolvedConfigText }}</pre>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import Badge from '@shared/components/Badge.vue';
import type { Variant } from '@shared/types/ui';
import SchedulerClient from '@/services/api/SchedulerClient';
import VersionClient from '@/services/api/VersionClient';
import { type ExecutionLevel, formatPeriod, type JobView } from '@/services/api/model/JobView';

const schedulerClient = new SchedulerClient();
const versionClient = new VersionClient();

const loading = ref(true);
const error = ref<string | null>(null);
const jobs = ref<JobView[]>([]);
const version = ref<string>('');
const activeFilter = ref<'ALL' | ExecutionLevel>('ALL');

const filters: Array<{ value: 'ALL' | ExecutionLevel; label: string }> = [
    { value: 'ALL', label: 'All' },
    { value: 'GLOBAL', label: 'Global' },
    { value: 'WORKSPACE', label: 'Per workspace' },
    { value: 'PROJECT', label: 'Per project' }
];

const counts = computed(() => ({
    ALL: jobs.value.length,
    GLOBAL: jobs.value.filter(j => j.executionLevel === 'GLOBAL').length,
    WORKSPACE: jobs.value.filter(j => j.executionLevel === 'WORKSPACE').length,
    PROJECT: jobs.value.filter(j => j.executionLevel === 'PROJECT').length
}));

const enabledCount = computed(() => jobs.value.filter(j => j.enabled).length);
const disabledCount = computed(() => jobs.value.length - enabledCount.value);

const filteredJobs = computed(() => {
    if (activeFilter.value === 'ALL') return jobs.value;
    return jobs.value.filter(j => j.executionLevel === activeFilter.value);
});

const hasParams = (params: Record<string, string>) => params && Object.keys(params).length > 0;

const displayNames: Record<string, string> = {
    WORKSPACE_EVENTS_REPLICATOR: 'Workspace Events Replicator',
    WORKSPACE_EVENTS_CLEANER: 'Workspace Events Cleaner',
    PROJECTS_SYNCHRONIZER: 'Projects Synchronizer',
    PROFILER_SETTINGS_SYNCHRONIZER: 'Profiler Settings Synchronizer',
    PROJECT_INSTANCE_SESSION_CLEANER: 'Instance Session Cleaner',
    PROJECT_INSTANCE_RECORDING_CLEANER: 'Instance Recording Cleaner',
    EXPIRED_INSTANCE_CLEANER: 'Expired Instance Cleaner',
    REPOSITORY_JFR_COMPRESSION: 'JFR Compression',
    SESSION_FINISHED_DETECTOR: 'Session Finished Detector'
};
const displayNameFor = (jobType: string) => displayNames[jobType] || jobType;

const descriptions: Record<string, string> = {
    WORKSPACE_EVENTS_REPLICATOR:
        'Polls the shared workspace event folder for files written by the CLI and replicates them into the persistent queue. Events are not processed here — they are picked up by Projects Synchronizer.',
    WORKSPACE_EVENTS_CLEANER:
        'Trims the workspace events persistent queue, deleting entries older than the configured retention window so storage stays bounded.',
    PROJECTS_SYNCHRONIZER:
        'Drains the workspace event queue per workspace and applies project create / delete and session lifecycle events, keeping the server’s project list in sync with what each workspace reports.',
    PROFILER_SETTINGS_SYNCHRONIZER:
        'Resolves the effective profiler settings (global → workspace → project) for every workspace and uploads them to the remote workspace, pruning legacy versions to the configured max-versions cap.',
    PROJECT_INSTANCE_SESSION_CLEANER:
        'Removes Project Instance Sessions older than the configured duration. Once a session is removed, all associated Recordings and Additional Files (HeapDump, PerfCounters, ...) are removed as well.',
    PROJECT_INSTANCE_RECORDING_CLEANER:
        'Removes only Recordings in the active (latest) Project Instance Session. It does not remove recordings in older sessions — it just ensures that rolling recordings in the latest session are bounded by age.',
    EXPIRED_INSTANCE_CLEANER:
        'Removes expired instance metadata after the configured retention period. Instances transition to EXPIRED when all their sessions are cleaned up, and this job permanently deletes those rows.',
    REPOSITORY_JFR_COMPRESSION:
        'Compresses finished JFR recording files using LZ4 compression to save storage space. Processes the active and latest finished sessions on each tick.',
    SESSION_FINISHED_DETECTOR:
        'Detects when repository sessions become finished using a heartbeat-based strategy and emits SESSION_FINISHED workspace events so downstream consumers can react.'
};
const descriptionFor = (jobType: string) => descriptions[jobType] || '';

const icons: Record<string, [string, string]> = {
    WORKSPACE_EVENTS_REPLICATOR: ['bi-broadcast', 'job-icon-bell'],
    WORKSPACE_EVENTS_CLEANER: ['bi-eraser', 'job-icon-broom'],
    PROJECTS_SYNCHRONIZER: ['bi-arrow-repeat', 'job-icon-sync'],
    PROFILER_SETTINGS_SYNCHRONIZER: ['bi-cpu', 'job-icon-cpu'],
    PROJECT_INSTANCE_SESSION_CLEANER: ['bi-trash', 'job-icon-trash'],
    PROJECT_INSTANCE_RECORDING_CLEANER: ['bi-trash', 'job-icon-trash'],
    EXPIRED_INSTANCE_CLEANER: ['bi-trash', 'job-icon-trash'],
    REPOSITORY_JFR_COMPRESSION: ['bi-file-zip', 'job-icon-zip'],
    SESSION_FINISHED_DETECTOR: ['bi-check-circle', 'job-icon-check']
};
const iconFor = (jobType: string) => icons[jobType]?.[0] || 'bi-gear';
const iconClass = (jobType: string) => icons[jobType]?.[1] || 'job-icon-default';

const levelLabels: Record<ExecutionLevel, string> = {
    GLOBAL: 'Global',
    WORKSPACE: 'Per workspace',
    PROJECT: 'Per project'
};
const levelLabel = (lvl: ExecutionLevel) => levelLabels[lvl];

const levelVariants: Record<ExecutionLevel, Variant> = {
    GLOBAL: 'violet',
    WORKSPACE: 'blue',
    PROJECT: 'primary'
};
const levelVariant = (lvl: ExecutionLevel): Variant => levelVariants[lvl];

const keyFor = (jobType: string) => jobType.toLowerCase().replace(/_/g, '-');

const resolvedConfigText = computed(() => {
    return jobs.value.map(job => {
        const key = keyFor(job.jobType);
        const prefix = `jeffrey.hub.scheduler.jobs.${key}`;
        const lines = [
            `# ${displayNameFor(job.jobType)}`,
            `${prefix}.enabled=${job.enabled}`,
            `${prefix}.period=${formatPeriod(job.period)}`
        ];
        if (job.params) {
            for (const [k, v] of Object.entries(job.params)) {
                lines.push(`${prefix}.params.${k}=${v}`);
            }
        }
        return lines.join('\n');
    }).join('\n\n');
});

onMounted(async () => {
    versionClient.getVersion()
        .then(v => { version.value = v; })
        .catch(err => console.error('Failed to load version:', err));

    try {
        jobs.value = await schedulerClient.jobs();
    } catch (e: any) {
        console.error('Failed to load scheduler jobs:', e);
        error.value = e?.message || 'Unknown error';
    } finally {
        loading.value = false;
    }
});
</script>

<style scoped>
.scheduler-view {
    max-width: 1100px;
    margin: 0 auto;
    padding: 32px 24px;
}

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

.hint {
    font-size: 0.78rem;
    color: var(--color-slate-muted);
    background: var(--color-blue-bg-lighter);
    border: 1px solid var(--color-accent-blue-light-bg);
    padding: 10px 14px;
    border-radius: 8px;
    margin: 22px 0 18px;
    display: flex;
    align-items: center;
    gap: 8px;
}
.hint i { color: var(--color-primary); }
.hint code {
    background: white;
    border: 1px solid var(--color-border);
    padding: 1px 6px;
    border-radius: 3px;
    font-size: 0.72rem;
    color: var(--color-heading-dark);
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

.filter-bar {
    display: flex;
    gap: 8px;
    margin-bottom: 14px;
    align-items: center;
}
.filter-chip {
    padding: 6px 12px;
    border-radius: 20px;
    font-size: 0.74rem;
    font-weight: 500;
    color: var(--color-slate-muted);
    background: white;
    border: 1px solid var(--color-border);
    cursor: pointer;
    user-select: none;
    font-family: inherit;
}
.filter-chip.active {
    background: var(--color-primary);
    color: white;
    border-color: var(--color-primary);
}
.chip-count { opacity: 0.6; }
.filter-spacer { flex: 1; }
.filter-meta { font-size: 0.72rem; color: var(--color-text-light); }

.section-card {
    background: white;
    border: 1px solid var(--color-border);
    border-radius: 10px;
    overflow: visible;
}

.jobs-table {
    width: 100%;
    border-collapse: collapse;
}
.jobs-table thead th {
    text-align: left;
    font-size: 0.7rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--color-text-light);
    padding: 10px 18px;
    background: var(--color-light);
    border-bottom: 1px solid var(--color-grey-bg);
}
.jobs-table tbody td {
    padding: 14px 18px;
    border-bottom: 1px solid var(--color-grey-bg);
    font-size: 0.82rem;
    color: var(--color-heading-dark);
    vertical-align: middle;
}
.jobs-table tbody tr:last-child td { border-bottom: none; }
.jobs-table tbody tr:hover { background: var(--color-light); }

.job-name-cell {
    display: flex;
    align-items: center;
    gap: 10px;
    position: relative;
    cursor: help;
}

.job-tooltip {
    visibility: hidden;
    opacity: 0;
    position: absolute;
    top: calc(100% + 10px);
    left: 0;
    background: white;
    color: var(--color-heading-dark);
    font-size: 0.78rem;
    line-height: 1.55;
    padding: 14px 16px 14px 18px;
    border-radius: 10px;
    border: 1px solid var(--color-border);
    border-left: 3px solid var(--color-primary);
    width: 380px;
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.10);
    transition: visibility 0s linear 0.18s, opacity 0.18s ease, transform 0.18s ease;
    pointer-events: none;
    z-index: 100;
    transform: translateY(-4px);
    text-align: left;
    font-weight: 400;
}

.job-tooltip-title {
    font-size: 0.7rem;
    font-weight: 600;
    color: var(--color-primary);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin: 0 0 6px;
}

.job-name-cell:hover .job-tooltip {
    visibility: visible;
    opacity: 1;
    transform: translateY(0);
    transition-delay: 0s;
}
.job-icon-box {
    width: 32px;
    height: 32px;
    border-radius: 7px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 0.95rem;
    flex-shrink: 0;
}
.job-icon-trash  { background: var(--color-teal-bg); color: var(--color-teal-text); }
.job-icon-zip    { background: var(--color-orange-bg); color: var(--color-orange-text); }
.job-icon-check  { background: var(--color-cyan-bg); color: var(--color-teal-text); }
.job-icon-sync   { background: var(--color-violet-lightest-bg); color: var(--color-violet-deeper); }
.job-icon-bell   { background: var(--color-amber-light); color: var(--color-amber-text); }
.job-icon-broom  { background: var(--color-red-bg); color: var(--color-red-text); }
.job-icon-cpu    { background: var(--color-indigo-bg); color: var(--color-indigo-text); }
.job-icon-default { background: var(--color-grey-bg); color: var(--color-slate-muted); }

.job-name { font-weight: 600; color: var(--color-heading-dark); font-size: 0.86rem; }

.params-cell {
    font-family: 'SFMono-Regular', Consolas, monospace;
    font-size: 0.73rem;
    color: var(--color-slate-muted);
    line-height: 1.5;
}
.params-cell .param-pair { display: inline-block; margin-right: 12px; }
.params-cell .pkey { color: var(--color-text-light); }
.params-cell .pdash { color: var(--color-text-light); }

.period-cell {
    font-family: 'SFMono-Regular', Consolas, monospace;
    font-size: 0.78rem;
    color: var(--color-heading-dark);
    font-weight: 500;
}

.config-source {
    margin-top: 20px;
    padding: 16px 18px;
}
.config-source-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    flex-wrap: wrap;
}
.config-source-icon { color: var(--color-primary); font-size: 1rem; }
.config-source-title {
    font-weight: 600;
    color: var(--color-heading-dark);
    font-size: 0.86rem;
}
.config-source-hint {
    font-size: 0.74rem;
    color: var(--color-text-light);
    flex: 1;
    min-width: 240px;
}
.config-source-hint code {
    background: var(--color-grey-bg);
    padding: 1px 5px;
    border-radius: 3px;
    font-size: 0.7rem;
    color: var(--color-heading-dark);
}
.config-source-pre {
    margin: 0;
    background: var(--color-light);
    border: 1px solid var(--color-grey-bg);
    border-radius: 6px;
    padding: 12px 14px;
    font-family: 'SFMono-Regular', Consolas, monospace;
    font-size: 0.72rem;
    line-height: 1.65;
    color: var(--color-slate-text);
    overflow-x: auto;
    white-space: pre;
}
</style>
