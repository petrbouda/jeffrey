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
  <LoadingState v-if="loading" message="Loading patch..." />

  <ErrorState v-else-if="error" :message="error" />

  <PageHeader
    v-else
    title="Patch & Verification"
    description="The proposed diff and how far Jeffrey could verify it against your working copy"
    icon="bi-file-earmark-diff"
  >
    <AiDisabledFeatureAlert v-if="aiDisabled" />

    <MainCard v-else>
      <template #header>
        <MainCardHeader icon="bi-file-earmark-diff" title="Proposed patch">
          <template #actions>
            <div class="actions">
              <select v-model="selectedEventType" class="event-select">
                <option v-for="type in eventTypes" :key="type.eventType" :value="type.eventType">
                  {{ type.label }}
                </option>
              </select>
              <button
                v-if="patch"
                type="button"
                class="copy-btn"
                title="Copy the diff so you can apply it yourself"
                @click="copyPatch"
              >
                <i class="bi bi-clipboard"></i>
                Copy diff
              </button>
            </div>
          </template>
        </MainCardHeader>
      </template>

      <EmptyState
        v-if="!selectedRecommendation"
        icon="bi-file-earmark-diff"
        title="Nothing to show yet"
        description="Run the Advisor on this profile first."
      />

      <template v-else>
        <PatchVerificationBar v-if="verification" :verification="verification" />

        <EmptyState
          v-if="!patch"
          icon="bi-check2-circle"
          title="No code change proposed"
          description="The model described what to change but produced no diff. The written recommendations are under Findings."
        />
        <DiffViewer v-else :patch="patch" />
      </template>
    </MainCard>
  </PageHeader>
</template>

<script setup lang="ts">
import '@shared/styles/shared-components.css';
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import DiffViewer from '@shared/components/DiffViewer.vue';
import ToastService from '@shared/services/ToastService';
import AiDisabledFeatureAlert from '@/components/alerts/AiDisabledFeatureAlert.vue';
import PatchVerificationBar from '@/components/advisor/PatchVerificationBar.vue';
import { useAdvisor } from '@/composables/useAdvisor';
import type { PatchVerification } from '@/services/api/model/Advisor';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{ disabledFeatures?: FeatureType[] }>();

const route = useRoute();
const profileId = route.params.profileId as string;

const { loading, error, eventTypes, selectedEventType, selectedRecommendation, load } =
  useAdvisor(profileId);

const aiDisabled = computed(
  () => props.disabledFeatures?.includes(FeatureType.AI_ANALYSIS) === true
);

const patch = computed(() => selectedRecommendation.value?.patch ?? null);

/**
 * The ladder is stored as raw JSON because it is produced and consumed as a whole. A row that cannot
 * be parsed is treated as absent rather than rendered half-formed — a verification bar showing partial
 * results would read as "we checked" when we did not.
 */
const verification = computed<PatchVerification | null>(() => {
  const raw = selectedRecommendation.value?.verification;
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as PatchVerification;
  } catch {
    return null;
  }
});

const copyPatch = async (): Promise<void> => {
  if (!patch.value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(patch.value);
    ToastService.success('Advisor', 'Diff copied to the clipboard');
  } catch {
    ToastService.error('Advisor', 'Could not copy the diff');
  }
};

onMounted(load);
</script>

<style scoped>
.actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.event-select {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.25rem 0.5rem;
  font-size: 0.8rem;
  background: var(--color-white);
}

.copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-white);
  padding: 0.25rem 0.7rem;
  font-size: 0.8rem;
  cursor: pointer;
}
</style>
