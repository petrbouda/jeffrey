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
  <LoadingState v-if="loading" message="Loading fleet patterns..." />

  <ErrorState v-else-if="error" :message="error" />

  <PageHeader
    v-else
    title="Fleet Patterns"
    description="Hotspots that recur across projects — the finding no single profile can produce"
    icon="bi-diagram-3"
  >
    <FleetPatternsCard
      :patterns="patterns.patterns"
      :analyzed-project-count="patterns.analyzedProjectCount"
      @open="openOccurrence"
    />
  </PageHeader>
</template>

<script setup lang="ts">
import '@shared/styles/shared-components.css';
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import FleetPatternsCard from '@/components/advisor/FleetPatternsCard.vue';
import FleetPatternsClient from '@/services/api/FleetPatternsClient';
import type { FleetOccurrence, FleetPatterns } from '@/services/api/model/Advisor';

const router = useRouter();
const client = new FleetPatternsClient();

const loading = ref(true);
const error = ref<string | null>(null);
const patterns = ref<FleetPatterns>({ patterns: [], analyzedProjectCount: 0 });

const load = async (): Promise<void> => {
  loading.value = true;
  error.value = null;
  try {
    patterns.value = await client.load();
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load fleet patterns';
  } finally {
    loading.value = false;
  }
};

/**
 * A pattern is only actionable next to the profile that measured it, so an occurrence navigates to
 * that profile's findings rather than opening a detail panel here.
 */
const openOccurrence = (occurrence: FleetOccurrence): void => {
  router.push(`/profiles/${occurrence.profileId}/advisor`);
};

onMounted(load);
</script>
