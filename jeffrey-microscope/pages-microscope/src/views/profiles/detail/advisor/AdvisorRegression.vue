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
  <LoadingState v-if="loading" message="Loading regression comparison..." />

  <ErrorState v-else-if="error" :message="error" />

  <PageHeader
    v-else
    title="Regression"
    description="Which frames moved between this profile and a baseline — measured, not modelled"
    icon="bi-graph-up-arrow"
  >
    <div v-if="!secondaryProfile" class="notice">
      <i class="bi bi-layers"></i>
      <span>
        Pick a baseline with the <strong>Secondary Profile</strong> selector above. The comparison
        is pure arithmetic over the two cached call trees, so it costs nothing and no model is
        involved.
      </span>
    </div>

    <template v-else>
      <MainCard>
        <template #header>
          <MainCardHeader
            icon="bi-graph-up-arrow"
            :title="`Compared against ${secondaryProfile.name}`"
          >
            <template #actions>
              <select v-model="selectedEventType" class="event-select" @change="compare">
                <option v-for="type in eventTypes" :key="type.eventType" :value="type.eventType">
                  {{ type.label }}
                </option>
              </select>
            </template>
          </MainCardHeader>
        </template>

        <EmptyState
          v-if="!comparison"
          icon="bi-graph-up-arrow"
          title="No comparison yet"
          description="Pick a profile group to compare."
        />

        <template v-else>
          <div class="threshold-note">
            Showing frames that moved at least {{ comparison.thresholdPp.toFixed(1) }} percentage
            points. Smaller moves are sampling noise.
          </div>

          <EmptyState
            v-if="!comparison.regressed.length && !comparison.improved.length"
            icon="bi-check2-circle"
            title="Nothing moved"
            description="No frame changed by more than the configured threshold."
          />

          <div v-else class="delta-tables">
            <DeltaTable title="Regressed" :deltas="comparison.regressed" tone="bad" />
            <DeltaTable title="Improved" :deltas="comparison.improved" tone="good" />
          </div>
        </template>
      </MainCard>
    </template>
  </PageHeader>
</template>

<script setup lang="ts">
import '@shared/styles/shared-components.css';
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import AdvisorClient from '@/services/api/AdvisorClient';
import DeltaTable from '@/views/profiles/detail/advisor/DeltaTable.vue';
import type { AdvisorEventType, RegressionComparison } from '@/services/api/model/Advisor';

const props = defineProps<{ secondaryProfile?: { id: string; name: string } | null }>();

const route = useRoute();
const client = new AdvisorClient(route.params.profileId as string);

const loading = ref(true);
const error = ref<string | null>(null);
const eventTypes = ref<AdvisorEventType[]>([]);
const selectedEventType = ref<string | null>(null);
const comparison = ref<RegressionComparison | null>(null);

const compare = async (): Promise<void> => {
  if (!props.secondaryProfile || !selectedEventType.value) {
    comparison.value = null;
    return;
  }
  try {
    comparison.value = await client.regression(props.secondaryProfile.id, selectedEventType.value);
  } catch (e: any) {
    comparison.value = null;
    error.value = e?.response?.data?.message ?? 'Could not compare the two profiles';
  }
};

const load = async (): Promise<void> => {
  loading.value = true;
  error.value = null;
  try {
    eventTypes.value = await client.eventTypes();
    selectedEventType.value = eventTypes.value[0]?.eventType ?? null;
    await compare();
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load the comparison';
  } finally {
    loading.value = false;
  }
};

// Changing the baseline in the profile-wide selector should re-run the comparison, not silently leave
// the old one on screen next to the new baseline's name.
watch(() => props.secondaryProfile?.id, compare);

onMounted(load);
</script>

<style scoped>
.notice {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  background: var(--color-light);
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.event-select {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.25rem 0.5rem;
  font-size: 0.8rem;
  background: var(--color-white);
}

.threshold-note {
  padding: 0.6rem 1rem;
  border-bottom: 1px solid var(--color-border-light);
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.delta-tables {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 1rem;
}

.delta-tables > * {
  flex: 1 1 320px;
  min-width: 0;
}
</style>
