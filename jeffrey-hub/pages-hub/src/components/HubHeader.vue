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
    <div class="page-header">
        <div class="header-left">
            <img src="/jeffrey-icon.svg" alt="Jeffrey" class="header-logo">
            <h4>Jeffrey Hub</h4>
            <span v-if="version" class="version-badge">{{ version }}</span>
        </div>
        <nav class="header-nav">
            <router-link to="/" class="nav-tab">Workspaces</router-link>
            <router-link to="/scheduler" class="nav-tab">Scheduler</router-link>
            <router-link to="/job-executions" class="nav-tab">Job Executions</router-link>
            <router-link to="/api-docs" class="nav-tab">API Documentation</router-link>
        </nav>
    </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import VersionClient from '@/services/api/VersionClient';

const versionClient = new VersionClient();
const version = ref<string>('');

onMounted(() => {
    versionClient.getVersion()
        .then(v => { version.value = v; })
        .catch(err => console.error('Failed to load version:', err));
});
</script>

<style scoped>
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

.nav-tab:hover {
    color: var(--color-slate-text);
}

.nav-tab.router-link-active {
    background: white;
    color: var(--color-primary);
    box-shadow: var(--shadow-sm);
}
</style>
