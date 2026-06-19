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
  <WorkspacesBrowser :app-description="HERO">
    <template #project="{ project, hubId, workspaceId, restore }">
      <ProjectCard
        :project="project"
        :hub-id="hubId"
        :workspace-id="workspaceId"
        @restore="restore"
      />
    </template>
  </WorkspacesBrowser>
</template>

<script setup lang="ts">
import WorkspacesBrowser from '@workspaces/components/projects/WorkspacesBrowser.vue';
import ProjectCard from '@/components/ProjectCard.vue';

const HERO =
  'The Performance Analyst analyzes profiles served by Jeffrey servers. Add a hub to browse its workspaces and projects.';
</script>
