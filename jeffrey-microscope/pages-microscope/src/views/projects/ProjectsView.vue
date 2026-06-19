<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 -->

<template>
  <WorkspacesBrowser
    :app-description="HERO"
    :extra-tabs="EXTRA_TABS"
    @refresh-tab="onRefreshTab"
    @tab-change="onTabChange"
  >
    <template #project="{ project, hubId, workspaceId, restore }">
      <ProjectCard
        :project="project"
        :hub-id="hubId"
        :workspace-id="workspaceId"
        @restore="restore"
      />
    </template>

    <template #header-controls="{ activeTabId }">
      <template v-if="activeTabId === 'events'">
        <Badge
          key-label="Events"
          :value="eventsCount"
          variant="secondary"
          size="s"
          :uppercase="false"
          :borderless="true"
        />
        <div class="search">
          <i class="bi bi-search"></i>
          <input v-model="eventSearchQuery" type="text" placeholder="Search events…" />
        </div>
      </template>
    </template>

    <template #tab-events="{ hubId, workspaceId }">
      <WorkspaceEventLog
        ref="eventLogRef"
        :hub-id="hubId"
        :workspace-id="workspaceId"
        :search-query="eventSearchQuery"
        @update:count="eventsCount = $event"
      />
    </template>

    <template #tab-settings="{ hubId, workspaceId, workspaceName }">
      <WorkspaceProfilerSettings
        :hub-id="hubId"
        :workspace-id="workspaceId"
        :workspace-name="workspaceName"
      />
    </template>
  </WorkspacesBrowser>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import WorkspacesBrowser from '@workspaces/components/projects/WorkspacesBrowser.vue';
import ProjectCard from '@/components/ProjectCard.vue';
import Badge from '@shared/components/Badge.vue';
import WorkspaceEventLog from '@/views/projects/WorkspaceEventLog.vue';
import WorkspaceProfilerSettings from '@/views/projects/WorkspaceProfilerSettings.vue';

const HERO =
  'Microscope analyzes profiles served by Jeffrey servers. Add a hub to browse its workspaces and projects.';

const EXTRA_TABS = [
  { id: 'events', label: 'Event Log', icon: 'bi-list-ul', refreshable: true },
  { id: 'settings', label: 'Profiler Settings', icon: 'bi-gear', refreshable: false }
];

const eventSearchQuery = ref('');
const eventsCount = ref(0);
const eventLogRef = ref<InstanceType<typeof WorkspaceEventLog> | null>(null);

const onRefreshTab = (tabId: string) => {
  if (tabId === 'events') {
    eventLogRef.value?.refresh();
  }
};

const onTabChange = (tabId: string) => {
  if (tabId === 'projects') {
    eventSearchQuery.value = '';
  }
};
</script>

<style scoped>
@import '@/styles/shared-components.css';
</style>
