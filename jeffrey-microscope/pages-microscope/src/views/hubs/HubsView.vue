<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 -->

<template>
  <HubsBrowser
    :app-description="HERO"
    :initial-hub-id="initialHubId"
    :initial-workspace-id="initialWorkspaceId"
  >
    <template #project="{ project, hubId, workspaceId, restore }">
      <ProjectCard
        :project="project"
        :hub-id="hubId"
        :workspace-id="workspaceId"
        @restore="restore"
        @open="t => navigateToProject(t.hubId, t.projectId, t.workspaceId)"
      />
    </template>
  </HubsBrowser>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import HubsBrowser from '@hubs/components/projects/HubsBrowser.vue';
import ProjectCard from '@shared/components/projects/ProjectCard.vue';
import { useNavigation } from '@/composables/useNavigation';

const { navigateToProject } = useNavigation();

const HERO =
  'Microscope analyzes profiles served by Jeffrey Hubs. Add a hub to browse its workspaces and projects.';

// Optional deep-link from a breadcrumb (`/hubs?hubId=…&workspaceId=…`) so the
// browser opens with that server + workspace preselected instead of the first one.
const route = useRoute();
const queryParam = (key: string): string | null => {
  const value = route.query[key];
  return typeof value === 'string' ? value : null;
};
const initialHubId = computed(() => queryParam('hubId'));
const initialWorkspaceId = computed(() => queryParam('workspaceId'));
</script>

<style scoped>
@import '@shared/styles/shared-components.css';
</style>
