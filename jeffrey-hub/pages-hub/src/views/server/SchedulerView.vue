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
    <div class="scheduler-view">
        <HubPageHeader/>

        <div class="hint">
            <i class="bi bi-info-circle"></i>
            <span>Job settings are read-only — edit <code>application.properties</code> and restart the server to change them. Jobs that support it can be run on demand from the Run column.</span>
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
                <div class="filter-spacer"></div>
                <span class="filter-meta">{{ enabledCount }} enabled · {{ disabledCount }} disabled</span>
            </div>

            <div class="section-card">
                <table class="jobs-table">
                    <thead>
                    <tr>
                        <th style="width: 32%">Job</th>
                        <th style="width: 9%">Period</th>
                        <th>Parameters</th>
                        <th style="width: 12%">Status</th>
                        <th style="width: 92px" class="run-header">Run</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="job in jobs" :key="job.jobType">
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
                        <td class="run-cell">
                            <button
                                v-if="job.manualTriggerSupported"
                                class="btn-run"
                                :class="{ ran: lastRun(job.jobType) }"
                                :disabled="isRunning(job.jobType)"
                                :title="runTitle(job)"
                                @click="runJob(job.jobType)"
                            >
                                <span v-if="isRunning(job.jobType)" class="spinner-border spinner-border-sm" role="status"></span>
                                <i v-else-if="lastRun(job.jobType)" class="bi bi-check-lg"></i>
                                <i v-else class="bi bi-play-fill"></i>
                            </button>
                            <span
                                v-else
                                class="btn-run unsupported"
                                title="This job cannot be run manually"
                                aria-hidden="true"
                            >
                                <i class="bi bi-play-fill"></i>
                            </span>
                            <div v-if="lastRun(job.jobType)" class="run-summary">{{ lastRun(job.jobType) }}</div>
                            <div v-else-if="runError(job.jobType)" class="run-summary failed">{{ runError(job.jobType) }}</div>
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
import HubPageHeader from '@/components/HubPageHeader.vue';
import SchedulerClient from '@/services/api/SchedulerClient';
import { formatPeriod, type JobTypeName, type JobView } from '@/services/api/model/JobView';

const schedulerClient = new SchedulerClient();

const loading = ref(true);
const error = ref<string | null>(null);
const jobs = ref<JobView[]>([]);
const enabledCount = computed(() => jobs.value.filter(j => j.enabled).length);
const disabledCount = computed(() => jobs.value.length - enabledCount.value);

const hasParams = (params: Record<string, string>) => params && Object.keys(params).length > 0;

/**
 * Manual runs are transient by design: the result lives in the row that produced it until the
 * page is left. Nothing is persisted server-side, so there is no history to show here.
 */
const RESULT_VISIBLE_MS = 20_000;

const running = ref<Set<string>>(new Set());
const results = ref<Record<string, string>>({});
const failures = ref<Record<string, string>>({});

const isRunning = (jobType: string) => running.value.has(jobType);
const lastRun = (jobType: string) => results.value[jobType];
const runError = (jobType: string) => failures.value[jobType];

const runTitle = (job: JobView) => {
  if (isRunning(job.jobType)) {
    return 'Running…';
  }
  return `Run ${displayNameFor(job.jobType)} now`;
};

const runJob = async (jobType: string) => {
  if (isRunning(jobType)) {
    return;
  }

  running.value = new Set(running.value).add(jobType);
  delete results.value[jobType];
  delete failures.value[jobType];

  try {
    const result = await schedulerClient.run(jobType);
    results.value = { ...results.value, [jobType]: result.summary };
    window.setTimeout(() => {
      const { [jobType]: _removed, ...rest } = results.value;
      results.value = rest;
    }, RESULT_VISIBLE_MS);
  } catch (e) {
    console.error('Failed to run job:', e);
    failures.value = { ...failures.value, [jobType]: 'Run failed' };
  } finally {
    const next = new Set(running.value);
    next.delete(jobType);
    running.value = next;
  }
};

const displayNames: Record<JobTypeName, string> = {
    PROJECT_RETENTION: 'Project Retention',
    REPOSITORY_JFR_COMPRESSION: 'JFR Compression'
};
const displayNameFor = (jobType: string) => displayNames[jobType as JobTypeName] || jobType;

const descriptions: Record<JobTypeName, string> = {
    PROJECT_RETENTION:
        'One pass per project over a single listing of its sessions. A session goes whole once its last recorded data is older than session-retention, so one still recording is never aged out; each instance keeps at most max-sessions logical sessions (a run of failed 0-byte sessions counts as one, the newest session occupies a slot); every session loses its closed chunks older than active-session-file-retention; the project is then held under max-size, oldest data first; and instances that never got a session within instance-retention are deleted with their directories (an instance goes with its last session otherwise). The newest recording of each instance\'s newest session is never deleted by a trim, since it may still be on its way onto the volume. What is left is measured for the storage dashboard, so its figures are up to one period stale. The first tick runs at startup; run it manually to apply retention and refresh the dashboard now.',
    REPOSITORY_JFR_COMPRESSION:
        'Compresses JFR recording files using LZ4 compression to save storage space. Processes the two newest sessions of each instance on each tick, leaving out the newest recording of the newest session, which may still be on its way onto the volume.'
};
const descriptionFor = (jobType: string) => descriptions[jobType as JobTypeName] || '';

const icons: Record<JobTypeName, [string, string]> = {
    PROJECT_RETENTION: ['bi-trash', 'job-icon-trash'],
    REPOSITORY_JFR_COMPRESSION: ['bi-file-zip', 'job-icon-zip']
};
const iconFor = (jobType: string) => icons[jobType as JobTypeName]?.[0] || 'bi-gear';
const iconClass = (jobType: string) => icons[jobType as JobTypeName]?.[1] || 'job-icon-default';

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

/* Manual run control — rendered for every job so the column reads as a property of all of
   them, disabled where the job declares no manual run. */
.run-header {
    text-align: center;
}

.run-cell {
    text-align: center;
    white-space: nowrap;
}

.btn-run {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    padding: 0;
    border: 1px solid var(--color-border-row);
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--color-primary);
    font-size: 0.85rem;
    cursor: pointer;
}

.btn-run:hover:not(:disabled):not(.unsupported) {
    background: var(--color-primary-light);
    border-color: var(--color-primary);
}

.btn-run:focus-visible {
    outline: 2px solid var(--color-primary);
    outline-offset: 2px;
}

.btn-run:disabled {
    cursor: default;
    color: var(--color-text-light);
}

.btn-run.ran {
    color: var(--color-success);
    border-color: var(--color-success-light);
    background: var(--color-success-light);
}

.btn-run.unsupported {
    color: var(--color-border-row);
    border-color: transparent;
    cursor: default;
}

.run-summary {
    margin-top: 4px;
    font-size: 0.68rem;
    line-height: 1.25;
    color: var(--color-success);
    white-space: normal;
}

.run-summary.failed {
    color: var(--color-red-text);
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
