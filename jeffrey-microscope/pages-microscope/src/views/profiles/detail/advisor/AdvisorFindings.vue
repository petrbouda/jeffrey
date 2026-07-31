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
  <LoadingState v-if="loading" message="Loading advisor..." />

  <ErrorState v-else-if="error" :message="error" />

  <PageHeader
    v-else
    title="Advisor Findings"
    description="AI recommendations mapped to your source, with every cited frame checked against the measured profile"
    icon="bi-lightbulb"
  >
    <AiDisabledFeatureAlert v-if="aiDisabled" />

    <template v-else>
      <div v-if="!sourceConfigured" class="notice">
        <i class="bi bi-folder2-open"></i>
        <span>
          No source folder is configured for this project. The Advisor reads your working copy
          directly — point it at one under
          <router-link :to="settingsPath">Source &amp; Settings</router-link>.
        </span>
      </div>

      <AdvisorRunBar
        v-if="progress?.status"
        :progress="progress"
        :stages="stages"
        :is-running="isRunning"
        @cancel="cancel"
      />

      <MainCard>
        <template #header>
          <MainCardHeader icon="bi-lightbulb" title="Recommendations">
            <template #actions>
              <div class="actions">
                <select v-model="selectedEventType" class="event-select" :disabled="isRunning">
                  <option v-for="type in eventTypes" :key="type.eventType" :value="type.eventType">
                    {{ type.label }}
                  </option>
                </select>
                <button
                  type="button"
                  class="generate-btn"
                  :disabled="!canGenerate"
                  :title="generateTitle"
                  @click="onGenerate"
                >
                  <i class="bi bi-stars"></i>
                  {{ selectedRecommendation ? 'Regenerate' : 'Generate' }}
                </button>
              </div>
            </template>
          </MainCardHeader>
        </template>

        <EmptyState
          v-if="!selectedRecommendation"
          icon="bi-lightbulb"
          title="No recommendations yet"
          description="Pick a profile and run the Advisor. It reads your source folder to map the hottest frames to real code."
        />

        <template v-else>
          <div class="result-meta">
            <Badge
              :value="selectedRecommendation.severity"
              :variant="severityVariant(selectedRecommendation.severity)"
              size="xs"
            />
            <span class="meta-item">
              {{ FormattingService.formatTimestamp(selectedRecommendation.generatedAt) }}
            </span>
            <span class="meta-item">{{ usageLabel }}</span>
            <span class="meta-item source-ref">{{ sourceRefLabel }}</span>
          </div>

          <ClaimList :claims="selectedRecommendation.claims" />

          <!-- Sanitized in MarkdownRenderer: this markdown quotes source read off the user's disk. -->
          <div class="report" v-html="renderedReport"></div>
        </template>
      </MainCard>
    </template>
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
import Badge from '@shared/components/Badge.vue';
import FormattingService from '@shared/services/FormattingService';
import MarkdownRenderer from '@shared/services/MarkdownRenderer';
import { severityVariant } from '@shared/services/severityDisplay';
import AiDisabledFeatureAlert from '@/components/alerts/AiDisabledFeatureAlert.vue';
import ClaimList from '@/components/advisor/ClaimList.vue';
import AdvisorRunBar from '@/views/profiles/detail/advisor/AdvisorRunBar.vue';
import { useAdvisor } from '@/composables/useAdvisor';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{ disabledFeatures?: FeatureType[] }>();

const route = useRoute();
const profileId = route.params.profileId as string;

const {
  loading,
  error,
  eventTypes,
  progress,
  stages,
  selectedEventType,
  selectedRecommendation,
  sourceConfigured,
  isRunning,
  load,
  generate,
  cancel
} = useAdvisor(profileId);

const aiDisabled = computed(
  () => props.disabledFeatures?.includes(FeatureType.AI_ANALYSIS) === true
);

const settingsPath = computed(() => `/profiles/${profileId}/advisor/settings`);

const canGenerate = computed(
  () => !isRunning.value && sourceConfigured.value && selectedEventType.value != null
);

const generateTitle = computed(() => {
  if (isRunning.value) {
    return 'A run is already in progress';
  }
  if (!sourceConfigured.value) {
    return 'Configure a source folder first';
  }
  return 'Analyze this profile against your source folder';
});

/**
 * Providers report cost inconsistently — an API returns token counts, the Claude Code CLI a settled
 * price — so both are shown when known and neither is invented when not.
 */
const usageLabel = computed(() => {
  const result = selectedRecommendation.value;
  if (!result) {
    return '';
  }
  const tokens = `${FormattingService.formatNumber(result.inputTokens + result.outputTokens)} tokens`;
  return result.costUsd == null ? tokens : `${tokens} · $${result.costUsd.toFixed(2)}`;
});

const sourceRefLabel = computed(() => {
  const ref = selectedRecommendation.value?.sourceRef;
  return ref ? `source @ ${ref.slice(0, 8)}` : 'source commit unknown';
});

const renderedReport = computed(() =>
  MarkdownRenderer.render(selectedRecommendation.value?.recommendations)
);

const onGenerate = (): void => {
  if (selectedEventType.value) {
    generate(selectedEventType.value);
  }
};

onMounted(load);
</script>

<style scoped>
.notice {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
  border: 1px solid var(--color-amber-border);
  border-radius: var(--radius-md);
  background: var(--color-amber-bg);
  color: var(--color-amber-text);
  font-size: 0.85rem;
}

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

.generate-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  border: none;
  border-radius: var(--radius-sm);
  padding: 0.3rem 0.75rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-white);
  background: var(--color-primary);
  cursor: pointer;
}

.generate-btn:disabled {
  background: var(--color-border);
  color: var(--color-text-muted);
  cursor: not-allowed;
}

.result-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--color-border-light);
}

.meta-item {
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.source-ref {
  font-family: var(--font-family-monospace);
}

.report {
  padding: 1rem;
  font-size: 0.9rem;
  line-height: 1.6;
}

.report :deep(h2),
.report :deep(h3) {
  font-size: 1rem;
  font-weight: 600;
  margin-top: 1.25rem;
}

.report :deep(code) {
  font-family: var(--font-family-monospace);
  font-size: 0.85em;
  background: var(--color-light);
  padding: 0.1rem 0.3rem;
  border-radius: var(--radius-sm);
}
</style>
