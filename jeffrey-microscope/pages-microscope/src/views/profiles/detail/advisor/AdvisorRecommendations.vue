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
  <LoadingState v-if="loading" message="Loading recommendations..." />

  <ErrorState v-else-if="error" :message="error" />

  <PageHeader
    v-else
    title="Recommendations"
    description="AI recommendations mapped to your source, ranked by the profile's measured hotspots"
    icon="bi-lightbulb"
  >
    <AiDisabledFeatureAlert v-if="aiDisabled" />

    <template v-else-if="hasResults">
      <!-- The docket: every type in the Advisor's canonical order, so a type sits in the same place
           here as on Prompts and Patches, carrying the figures behind its grade. -->
      <AdvisorDocket v-model:selected="selectedEventType" :items="items">
        <template #rows="{ item }">
          <template v-if="recommendationFor(item.eventType)">
            <AdvisorDocketRow
              label="Top method"
              mono
              :title="recommendationFor(item.eventType)!.dominantMethod"
            >
              {{ topMethod(recommendationFor(item.eventType)!.dominantMethod) }}
            </AdvisorDocketRow>
            <AdvisorDocketRow label="Its share">
              {{
                FormattingService.formatPercentValue(
                  recommendationFor(item.eventType)!.dominantSelfPct
                )
              }}
            </AdvisorDocketRow>
            <AdvisorDocketRow label="Analysed in">
              {{ analysedIn(item.eventType) }}
            </AdvisorDocketRow>
          </template>
          <AdvisorDocketRow v-else label="Report">not analysed yet</AdvisorDocketRow>
        </template>
      </AdvisorDocket>

      <!-- A type this recording has no samples for: what is missing, and how to record it. -->
      <AdvisorMissingType
        v-if="selectedMissing"
        :event-type="selectedMissing.eventType"
        :label="selectedMissing.label"
        missing-artifact="report to read"
      />

      <MainCard v-else-if="selectedRecommendation">
        <template #header>
          <MainCardHeader
            icon="bi-lightbulb"
            :title="`${labelFor(selectedRecommendation.eventType)} recommendations`"
          >
            <template #actions>
              <Badge
                :value="selectedRecommendation.severity"
                :variant="severityVariant(selectedRecommendation.severity)"
                size="xs"
              />
            </template>
          </MainCardHeader>
        </template>
        <!-- The change itself lives on Patches; this page keeps the reasoning. -->
        <router-link v-if="selectedRecommendation.patch" :to="patchesPath" class="patch-link">
          <i class="bi bi-file-earmark-diff"></i>
          <span class="patch-link-text">
            <b>A code change is proposed for {{ labelFor(selectedRecommendation.eventType) }}</b>
            <small>See the diff the Advisor produced for this profile</small>
          </span>
          <i class="bi bi-arrow-right"></i>
        </router-link>

        <!-- Sanitized in MarkdownRenderer: this markdown quotes source read off the user's disk. -->
        <div class="report" v-html="renderedReport"></div>
      </MainCard>

      <!-- Recorded, but this run never analysed it. -->
      <EmptyState
        v-else
        icon="bi-lightbulb"
        :title="`No ${selectedLabel} report yet`"
        description="The Advisor analyses this type on its next run."
      />
    </template>

    <!-- Nothing recommended yet: send the user to the Overview to run the Advisor. -->
    <EmptyState
      v-else
      icon="bi-lightbulb"
      :title="isRunning ? 'A run is in progress' : 'No recommendations yet'"
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
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import Badge from '@shared/components/Badge.vue';
import MarkdownRenderer from '@shared/services/MarkdownRenderer';
import FormattingService from '@shared/services/FormattingService';
import { severityLabel, severityVariant } from '@shared/services/severityDisplay';
import type { AdvisorRecommendation } from '@/services/api/model/Advisor';
import EmptyState from '@shared/components/EmptyState.vue';
import AiDisabledFeatureAlert from '@/components/alerts/AiDisabledFeatureAlert.vue';
import AdvisorDocket from '@/views/profiles/detail/advisor/AdvisorDocket.vue';
import AdvisorDocketRow from '@/views/profiles/detail/advisor/AdvisorDocketRow.vue';
import AdvisorMissingType from '@/views/profiles/detail/advisor/AdvisorMissingType.vue';
import { analysisDurationMs, docketItems } from '@/views/profiles/detail/advisor/advisorDocket';
import { shortMethodName } from '@/views/profiles/detail/advisor/eventTypeStyle';
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
  runResult,
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
const patchesPath = computed(() => `/profiles/${profileId}/advisor/patches`);

const labelFor = (code: string): string =>
  eventTypes.value.find(type => type.eventType === code)?.label ?? code;

const recommendationFor = (eventType: string): AdvisorRecommendation | undefined =>
  recommendations.value.find(item => item.eventType === eventType);

/**
 * CPU, Wall-Clock, Allocation, Blocking — the Advisor's canonical order rather than a per-page ranking,
 * so the docket reads the same on Prompts, Recommendations and Patches. The grade rides on the card,
 * which is where the "where to start" signal lives.
 */
const items = computed(() =>
  docketItems(eventTypes.value, eventType => {
    const recommendation = recommendationFor(eventType);
    if (recommendation === undefined) {
      return undefined;
    }
    return {
      value: severityLabel(recommendation.severity),
      variant: severityVariant(recommendation.severity)
    };
  })
);

const topMethod = (method: string): string => (method === '' ? 'unknown' : shortMethodName(method));

/** How long the last run spent on this type — the same stored timings the Overview's timeline uses. */
const analysedIn = (eventType: string): string => {
  const durationMs = analysisDurationMs(runResult.value, eventType);
  return durationMs === null ? '—' : FormattingService.formatDurationMillisCoarse(durationMs);
};

const selectedItem = computed(() =>
  items.value.find(item => item.eventType === selectedEventType.value)
);

const selectedLabel = computed(() => selectedItem.value?.label ?? '');

/** Set only when the selected type is one this recording carries no samples for. */
const selectedMissing = computed(() =>
  selectedItem.value?.available === false ? selectedItem.value : undefined
);

const renderedReport = computed(() =>
  MarkdownRenderer.render(selectedRecommendation.value?.report)
);

onMounted(load);
</script>

<style scoped>
/* The hand-off sits above the prose: you see that a change exists, then read why it is proposed. */
.patch-link {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  margin: 0 1rem;
  padding: 0.7rem 0.9rem;
  border: 1px solid var(--color-primary-border-light);
  border-radius: var(--radius-base);
  background: var(--color-primary-lighter);
  text-decoration: none;
  transition:
    background 0.16s,
    border-color 0.16s;
}

.patch-link:hover {
  background: var(--color-primary-light);
  border-color: var(--color-primary-border);
}

.patch-link > i {
  color: var(--color-primary);
  font-size: 1.05rem;
}

.patch-link-text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.patch-link-text b {
  font-size: 0.83rem;
  font-weight: 600;
  color: var(--color-heading-dark);
}

.patch-link-text small {
  font-size: 0.73rem;
  color: var(--color-text-muted);
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

/* action button inside the EmptyState (nothing recommended yet) */
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

/* the global `.empty-state i` rule would otherwise blow this icon up to 3rem and grey it out */
.guard-btn i {
  font-size: 1em;
  color: inherit;
  margin-bottom: 0;
}

.guard-btn:hover {
  background: var(--color-primary-hover);
}
</style>
