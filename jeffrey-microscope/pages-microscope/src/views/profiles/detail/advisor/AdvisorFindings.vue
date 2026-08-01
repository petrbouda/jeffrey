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
  <LoadingState v-if="loading" message="Loading findings..." />

  <ErrorState v-else-if="error" :message="error" />

  <PageHeader
    v-else
    title="Findings"
    description="AI recommendations mapped to your source, with every cited frame checked against the measured profile"
    icon="bi-lightbulb"
  >
    <AiDisabledFeatureAlert v-if="aiDisabled" />

    <template v-else-if="hasResults">
      <div class="results-bar">
        <span class="advisor-src">
          <i class="bi bi-folder2-open"></i>
          <span class="advisor-src-path">{{ settings?.sourcePath || 'No source folder' }}</span>
        </span>
        <div class="result-chips">
          <button
            v-for="rec in recommendations"
            :key="rec.eventType"
            type="button"
            class="rchip"
            :class="{ selected: selectedEventType === rec.eventType }"
            :style="eventTypeVars(rec.eventType)"
            @click="selectedEventType = rec.eventType"
          >
            <i class="bi" :class="eventTypeStyle(rec.eventType).icon"></i>
            {{ labelFor(rec.eventType) }}
          </button>
        </div>
        <router-link :to="overviewPath" class="overview-link">
          <i class="bi bi-arrow-repeat"></i>
          Run again
        </router-link>
      </div>

      <MainCard v-if="selectedRecommendation">
        <template #header>
          <MainCardHeader
            icon="bi-lightbulb"
            :title="`${labelFor(selectedRecommendation.eventType)} recommendations`"
          />
        </template>
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
      </MainCard>
    </template>

    <!-- No findings yet: send the user to the Overview to run the Advisor. -->
    <EmptyState
      v-else
      icon="bi-lightbulb"
      :title="isRunning ? 'A run is in progress' : 'No findings yet'"
      :description="
        isRunning
          ? 'The Advisor is analyzing your profiles now — watch it on the Overview.'
          : 'Run the Advisor to map the hottest frames in each profile to real code.'
      "
    >
      <template #action>
        <router-link :to="overviewPath" class="guard-btn">
          <i class="bi bi-arrow-right"></i>
          {{ isRunning ? 'Watch the run' : 'Go to the Advisor Overview' }}
        </router-link>
      </template>
    </EmptyState>
  </PageHeader>
</template>

<script setup lang="ts">
import '@shared/styles/shared-components.css';
import '@/views/profiles/detail/advisor/advisor-shared.css';
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import Badge from '@shared/components/Badge.vue';
import FormattingService from '@shared/services/FormattingService';
import MarkdownRenderer from '@shared/services/MarkdownRenderer';
import { severityVariant } from '@shared/services/severityDisplay';
import EmptyState from '@shared/components/EmptyState.vue';
import AiDisabledFeatureAlert from '@/components/alerts/AiDisabledFeatureAlert.vue';
import ClaimList from '@/components/advisor/ClaimList.vue';
import { eventTypeStyle, eventTypeVars } from '@/views/profiles/detail/advisor/eventTypeStyle';
import { useAdvisor } from '@/composables/useAdvisor';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{ disabledFeatures?: FeatureType[] }>();

const route = useRoute();
const profileId = route.params.profileId as string;

const {
  loading,
  error,
  eventTypes,
  recommendations,
  settings,
  selectedEventType,
  selectedRecommendation,
  hasResults,
  isRunning,
  load
} = useAdvisor(profileId);

const aiDisabled = computed(
  () => props.disabledFeatures?.includes(FeatureType.AI_ANALYSIS) === true
);

const overviewPath = computed(() => `/profiles/${profileId}/advisor`);

const labelFor = (code: string): string =>
  eventTypes.value.find(type => type.eventType === code)?.label ?? code;

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

onMounted(load);
</script>

<style scoped>
.results-bar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-bottom: 1.1rem;
}

.result-chips {
  display: inline-flex;
  gap: 0.35rem;
  flex-wrap: wrap;
}

.rchip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  padding: 0.26rem 0.7rem;
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-text-muted);
  background: var(--color-bg-card);
  cursor: pointer;
  transition: color 0.16s, background 0.16s, border-color 0.16s;
}

.rchip i {
  color: var(--et);
}

.rchip:hover {
  color: var(--color-text);
  border-color: var(--et);
}

.rchip.selected {
  color: var(--color-white);
  font-weight: 600;
  background: var(--et);
  border-color: var(--et);
}

.rchip.selected i {
  color: var(--color-white);
}

.overview-link {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-primary);
  text-decoration: none;
}

.overview-link:hover {
  text-decoration: underline;
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

/* action button inside the EmptyState (no findings yet) */
.guard-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  border-radius: var(--radius-base);
  background: var(--color-primary);
  color: var(--color-white);
  font-weight: 600;
  font-size: 0.85rem;
  padding: 0.5rem 1rem;
  text-decoration: none;
}

.guard-btn:hover {
  background: var(--color-primary-hover);
}
</style>
