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
    <div class="job-executions-view">
        <HubHeader />

        <div class="hint">
            <i class="bi bi-info-circle"></i>
            <span>
                In-memory execution history for debugging. Keeps the last
                <b>100 runs per job type</b>; cleared on restart.
            </span>
        </div>

        <div v-if="loading" class="loading-state">
            <div class="spinner-border spinner-border-sm text-secondary" role="status"></div>
            <span>Loading job executions...</span>
        </div>

        <div v-else-if="error" class="empty-state">
            <i class="bi bi-exclamation-triangle"></i>
            <span>Failed to load job executions</span>
            <span class="empty-hint">{{ error }}</span>
        </div>

        <template v-else>
            <div class="filter-bar">
                <button
                    class="filter-chip"
                    :class="{ active: activeFilter === 'ALL' }"
                    @click="activeFilter = 'ALL'"
                >
                    All <span class="chip-count">{{ executions.length }}</span>
                </button>
                <button
                    class="filter-chip chip-failures"
                    :class="{ active: activeFilter === 'FAILURES' }"
                    @click="activeFilter = 'FAILURES'"
                >
                    Failures <span class="chip-count">{{ failureCount }}</span>
                </button>
                <button
                    v-for="jobType in presentJobTypes"
                    :key="jobType"
                    class="filter-chip"
                    :class="{ active: activeFilter === jobType }"
                    @click="activeFilter = jobType"
                >
                    {{ displayNameFor(jobType) }} <span class="chip-count">{{ countByType[jobType] }}</span>
                </button>
                <div class="filter-spacer"></div>
                <label class="auto-refresh-toggle">
                    <input v-model="hideNoops" type="checkbox"> hide no-ops
                </label>
                <label class="auto-refresh-toggle">
                    <input v-model="autoRefresh" type="checkbox"> auto-refresh 5s
                </label>
                <button class="refresh-btn" :disabled="refreshing" @click="refresh">
                    <i class="bi bi-arrow-clockwise"></i> Refresh
                </button>
            </div>

            <div v-if="filteredExecutions.length === 0" class="empty-state">
                <i class="bi bi-inbox"></i>
                <span>No job executions recorded yet</span>
                <span class="empty-hint">Executions appear here as scheduler jobs run</span>
            </div>

            <div v-else class="section-card">
                <table class="executions-table">
                    <thead>
                    <tr>
                        <th style="width: 3%"></th>
                        <th style="width: 17%">Executed At</th>
                        <th style="width: 22%">Job</th>
                        <th>Summary</th>
                        <th style="width: 9%" class="th-right">Duration</th>
                        <th style="width: 11%">Status</th>
                    </tr>
                    </thead>
                    <tbody>
                    <template v-for="execution in filteredExecutions" :key="rowKey(execution)">
                        <tr
                            :class="{ expandable: isExpandable(execution), expanded: isExpanded(execution) }"
                            @click="toggleExpanded(execution)"
                        >
                            <td>
                                <i
                                    v-if="isExpandable(execution)"
                                    class="bi chevron-icon"
                                    :class="isExpanded(execution) ? 'bi-chevron-down' : 'bi-chevron-right'"
                                ></i>
                            </td>
                            <td class="time-cell">
                                <span class="time-of-day">{{ FormattingService.formatTimeOfDay(execution.startedAt) }}</span>
                                <span class="time-relative">{{ FormattingService.formatRelativeTime(execution.startedAt) }}</span>
                            </td>
                            <td>
                                <div class="job-name-cell">
                                    <div class="job-icon-box" :class="iconClassFor(execution.jobType)">
                                        <i class="bi" :class="iconFor(execution.jobType)"></i>
                                    </div>
                                    <div class="job-name">{{ displayNameFor(execution.jobType) }}</div>
                                </div>
                            </td>
                            <td class="summary-cell">
                                <span v-if="execution.summary">{{ execution.summary }}</span>
                                <span v-else-if="execution.error" class="summary-error">{{ execution.error }}</span>
                                <span v-else-if="execution.noop" class="summary-noop">Nothing to do</span>
                                <span v-else>{{ execution.items.length }} {{ execution.items.length === 1 ? 'detail item' : 'detail items' }}</span>
                            </td>
                            <td class="duration-cell">
                                {{ FormattingService.formatDurationMillisCompact(execution.durationMs) }}
                            </td>
                            <td>
                                <Badge
                                    v-if="execution.status === 'FAILURE'"
                                    value="Failure"
                                    variant="red"
                                    size="xs"
                                    icon="bi bi-x-circle-fill"
                                    :uppercase="false"
                                />
                                <Badge
                                    v-else-if="execution.noop"
                                    value="No-op"
                                    variant="grey"
                                    size="xs"
                                    :uppercase="false"
                                />
                                <Badge
                                    v-else
                                    value="Success"
                                    variant="green"
                                    size="xs"
                                    icon="bi bi-check-circle-fill"
                                    :uppercase="false"
                                />
                            </td>
                        </tr>
                        <tr v-if="isExpanded(execution)" class="detail-row">
                            <td></td>
                            <td colspan="5">
                                <div class="detail-box">
                                    <ul>
                                        <li
                                            v-for="(item, itemIndex) in execution.items"
                                            :key="itemIndex"
                                            :class="{ 'detail-error': isErrorItem(execution, item) }"
                                        >
                                            {{ item }}
                                        </li>
                                        <li v-if="execution.error && execution.items.length === 0" class="detail-error">
                                            {{ execution.error }}
                                        </li>
                                    </ul>
                                </div>
                            </td>
                        </tr>
                    </template>
                    </tbody>
                </table>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import Badge from '@shared/components/Badge.vue';
import FormattingService from '@shared/services/FormattingService';
import HubHeader from '@/components/HubHeader.vue';
import SchedulerClient from '@/services/api/SchedulerClient';
import type { JobExecutionView } from '@/services/api/model/JobExecutionView';
import { displayNameFor, iconClassFor, iconFor } from '@/services/jobDisplay';

const AUTO_REFRESH_INTERVAL_MS = 5000;

const schedulerClient = new SchedulerClient();

const loading = ref(true);
const refreshing = ref(false);
const error = ref<string | null>(null);
const executions = ref<JobExecutionView[]>([]);
const activeFilter = ref<string>('ALL');
const hideNoops = ref(false);
const autoRefresh = ref(false);
const expandedRows = ref(new Set<string>());

let autoRefreshTimer: ReturnType<typeof setInterval> | null = null;

const failureCount = computed(() => executions.value.filter(e => e.status === 'FAILURE').length);

const countByType = computed(() => {
    const counts: Record<string, number> = {};
    for (const execution of executions.value) {
        counts[execution.jobType] = (counts[execution.jobType] || 0) + 1;
    }
    return counts;
});

const presentJobTypes = computed(() => Object.keys(countByType.value).sort());

const filteredExecutions = computed(() => {
    let result = executions.value;
    if (activeFilter.value === 'FAILURES') {
        result = result.filter(e => e.status === 'FAILURE');
    } else if (activeFilter.value !== 'ALL') {
        result = result.filter(e => e.jobType === activeFilter.value);
    }
    if (hideNoops.value) {
        result = result.filter(e => !e.noop);
    }
    return result;
});

const rowKey = (execution: JobExecutionView) => `${execution.jobType}-${execution.startedAt}`;

const isExpandable = (execution: JobExecutionView) => {
    return execution.items.length > 0 || execution.error !== null;
};

const isExpanded = (execution: JobExecutionView) => expandedRows.value.has(rowKey(execution));

const toggleExpanded = (execution: JobExecutionView) => {
    if (!isExpandable(execution)) {
        return;
    }
    const updated = new Set(expandedRows.value);
    const key = rowKey(execution);
    if (updated.has(key)) {
        updated.delete(key);
    } else {
        updated.add(key);
    }
    expandedRows.value = updated;
};

const isErrorItem = (execution: JobExecutionView, item: string) => {
    return execution.status === 'FAILURE' && execution.error !== null && item === execution.error;
};

const load = async () => {
    try {
        executions.value = await schedulerClient.executions();
        error.value = null;
    } catch (e: any) {
        console.error('Failed to load job executions:', e);
        error.value = e?.message || 'Unknown error';
    } finally {
        loading.value = false;
    }
};

const refresh = async () => {
    refreshing.value = true;
    try {
        await load();
    } finally {
        refreshing.value = false;
    }
};

const stopAutoRefresh = () => {
    if (autoRefreshTimer !== null) {
        clearInterval(autoRefreshTimer);
        autoRefreshTimer = null;
    }
};

watch(autoRefresh, enabled => {
    stopAutoRefresh();
    if (enabled) {
        autoRefreshTimer = setInterval(load, AUTO_REFRESH_INTERVAL_MS);
    }
});

onMounted(load);
onUnmounted(stopAutoRefresh);
</script>

<style scoped>
.job-executions-view {
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

.hint i {
    color: var(--color-primary);
}

.loading-state, .empty-state {
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

.filter-bar {
    display: flex;
    gap: 8px;
    margin-bottom: 14px;
    align-items: center;
    flex-wrap: wrap;
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

.filter-chip.chip-failures.active {
    background: var(--color-danger);
    border-color: var(--color-danger);
}

.chip-count {
    opacity: 0.6;
}

.filter-spacer {
    flex: 1;
}

.auto-refresh-toggle {
    font-size: 0.72rem;
    color: var(--color-slate-muted);
    display: inline-flex;
    gap: 5px;
    align-items: center;
    user-select: none;
    margin: 0;
}

.refresh-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    border: 1px solid var(--color-border);
    background: white;
    border-radius: 6px;
    font-size: 0.74rem;
    color: var(--color-slate-text);
    padding: 6px 12px;
    cursor: pointer;
    font-family: inherit;
}

.refresh-btn:disabled {
    opacity: 0.6;
    cursor: default;
}

.section-card {
    background: white;
    border: 1px solid var(--color-border);
    border-radius: 10px;
    overflow: hidden;
}

.executions-table {
    width: 100%;
    border-collapse: collapse;
}

.executions-table thead th {
    text-align: left;
    font-size: 0.7rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--color-text-light);
    padding: 10px 16px;
    background: var(--color-light);
    border-bottom: 1px solid var(--color-grey-bg);
}

.executions-table thead th.th-right {
    text-align: right;
}

.executions-table tbody td {
    padding: 11px 16px;
    border-bottom: 1px solid var(--color-grey-bg);
    font-size: 0.8rem;
    color: var(--color-heading-dark);
    vertical-align: middle;
}

.executions-table tbody tr:last-child td {
    border-bottom: none;
}

.executions-table tbody tr.expandable {
    cursor: pointer;
}

.executions-table tbody tr.expandable:hover {
    background: var(--color-light);
}

.chevron-icon {
    font-size: 0.7rem;
    color: var(--color-text-light);
}

.time-cell {
    white-space: nowrap;
}

.time-of-day {
    font-family: 'SFMono-Regular', Consolas, monospace;
    font-size: 0.76rem;
    color: var(--color-heading-dark);
}

.time-relative {
    display: block;
    font-size: 0.68rem;
    color: var(--color-text-light);
}

.job-name-cell {
    display: flex;
    align-items: center;
    gap: 10px;
}

.job-icon-box {
    width: 28px;
    height: 28px;
    border-radius: 7px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 0.85rem;
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

.job-name {
    font-weight: 600;
    font-size: 0.8rem;
}

.summary-cell {
    color: var(--color-slate-text);
}

.summary-noop {
    color: var(--color-text-light);
}

.summary-error {
    color: var(--color-red-text);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.duration-cell {
    font-family: 'SFMono-Regular', Consolas, monospace;
    font-size: 0.76rem;
    font-weight: 500;
    text-align: right;
    white-space: nowrap;
}

.detail-row td {
    padding-top: 0;
    background: var(--color-light);
}

.detail-box {
    background: white;
    border: 1px solid var(--color-grey-bg);
    border-radius: 8px;
    margin: 8px 0 10px;
    padding: 10px 14px;
}

.detail-box ul {
    margin: 0;
    padding: 0;
    list-style: none;
}

.detail-box li {
    font-family: 'SFMono-Regular', Consolas, monospace;
    font-size: 0.72rem;
    color: var(--color-slate-text);
    padding: 3px 0;
    border-bottom: 1px dashed var(--color-grey-bg);
    word-break: break-all;
}

.detail-box li:last-child {
    border-bottom: none;
}

.detail-box li.detail-error {
    color: var(--color-red-text);
}
</style>
