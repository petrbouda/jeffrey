<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <MainCard>
    <template #header>
      <MainCardHeader icon="bi bi-activity" title="Activity" :badge="activityCount">
        <template #actions>
          <SearchInput
            v-model="searchQuery"
            class="activity-search"
            placeholder="Search activity…"
            aria-label="Search activity"
          />
        </template>
      </MainCardHeader>
    </template>

    <WorkspaceEventLog
      v-if="hubId && workspaceId && projectId"
      :hub-id="hubId"
      :workspace-id="workspaceId"
      :project-id="projectId"
      :search-query="searchQuery"
      @update:count="activityCount = $event"
    />
  </MainCard>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import SearchInput from '@shared/components/form/SearchInput.vue';
import WorkspaceEventLog from '@/components/workspace/WorkspaceEventLog.vue';
import { useNavigation } from '@/composables/useNavigation';

const { hubId, workspaceId, projectId } = useNavigation();

const searchQuery = ref('');
const activityCount = ref(0);
</script>

<style scoped>
.activity-search {
  width: 260px;
}
</style>
