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
  <RemoteBrowser :initial-workspace-id="initialWorkspaceId">
    <template #project="{ project, workspaceId }">
      <ProjectCard
        :project="project"
        :workspace-id="workspaceId"
        @open="t => navigateToProject(t.projectId, t.workspaceId)"
      />
    </template>
  </RemoteBrowser>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import RemoteBrowser from '@remote/components/projects/RemoteBrowser.vue';
import ProjectCard from '@shared/components/projects/ProjectCard.vue';
import { useNavigation } from '@/composables/useNavigation';

const { navigateToProject } = useNavigation();

// Optional deep-link from a breadcrumb (`/remote?workspaceId=…`) so the browser opens with that
// workspace preselected instead of the first one.
const route = useRoute();
const initialWorkspaceId = computed(() => {
  const value = route.query.workspaceId;
  return typeof value === 'string' ? value : null;
});
</script>
