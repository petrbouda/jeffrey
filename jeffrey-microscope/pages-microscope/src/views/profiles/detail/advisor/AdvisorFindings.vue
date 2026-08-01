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
      <!-- The docket: the reports ranked worst-first, so the page opens by saying where to start. -->
      <div class="docket">
        <p class="docket-lead">
          <i class="bi bi-folder2-open"></i>
          <span class="docket-count">{{ reportCountLabel }}</span> from
          <span class="docket-path">{{ settings?.sourcePath || 'No source folder' }}</span>
          <span class="docket-ref">{{ sourceRefLabel }}</span>
          <span v-if="generatedAgo" :title="generatedExact">· generated {{ generatedAgo }}</span>
        </p>
        <div class="docket-list">
          <button
            v-for="rec in rankedRecommendations"
            :key="rec.eventType"
            type="button"
            class="docket-item"
            :class="{ selected: selectedEventType === rec.eventType }"
            :style="eventTypeVars(rec.eventType)"
            @click="selectedEventType = rec.eventType"
          >
            <span class="docket-name">
              <i class="bi" :class="eventTypeStyle(rec.eventType).icon"></i>
              {{ labelFor(rec.eventType) }}
            </span>
            <span class="docket-verdict">
              {{ severityLabel(rec.severity) }} · {{ findingCountLabel(rec) }}
            </span>
          </button>
        </div>
      </div>

      <MainCard v-if="selectedRecommendation">
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
        <ClaimList :claims="selectedRecommendation.claims" />

        <!-- The proposed change, with the measured cost of what it removes. -->
        <div v-if="selectedRecommendation.patch" class="patch-block">
          <div class="patch-cap">
            <i class="bi bi-file-earmark-diff"></i>
            Proposed change
          </div>
          <HeatDiffViewer
            :patch="selectedRecommendation.patch"
            :marks="heatMarks"
            :cost-label="costLabel"
            :file-name="patchFileName"
          />
        </div>

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
import { severityLabel, severityRank, severityVariant } from '@shared/services/severityDisplay';
import type { AdvisorRecommendation } from '@/services/api/model/Advisor';
import EmptyState from '@shared/components/EmptyState.vue';
import AiDisabledFeatureAlert from '@/components/alerts/AiDisabledFeatureAlert.vue';
import ClaimList from '@/components/advisor/ClaimList.vue';
import HeatDiffViewer from '@shared/components/HeatDiffViewer.vue';
import { splitCitedPath, type HeatMark } from '@shared/services/unifiedDiff';
import {
  eventTypeCostLabel,
  eventTypeStyle,
  eventTypeVars
} from '@/views/profiles/detail/advisor/eventTypeStyle';
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

const sourceRefLabel = computed(() => {
  const ref = selectedRecommendation.value?.sourceRef;
  return ref ? `@ ${ref.slice(0, 8)}` : '(commit unknown)';
});

/**
 * Worst first, then most findings, then alphabetically — so the order is stable across reloads and
 * the report that earns your attention is the one you read first.
 */
const rankedRecommendations = computed(() =>
  [...recommendations.value].sort((left, right) => {
    const bySeverity = severityRank(left.severity) - severityRank(right.severity);
    if (bySeverity !== 0) {
      return bySeverity;
    }
    const byFindings = right.claims.length - left.claims.length;
    if (byFindings !== 0) {
      return byFindings;
    }
    return labelFor(left.eventType).localeCompare(labelFor(right.eventType));
  })
);

const reportCountLabel = computed(() => {
  const count = recommendations.value.length;
  return `${count} ${count === 1 ? 'report' : 'reports'}`;
});

const findingCountLabel = (recommendation: AdvisorRecommendation): string => {
  const count = recommendation.claims.length;
  return `${count} ${count === 1 ? 'finding' : 'findings'}`;
};

/** The newest report's timestamp — every type in a batch is generated by the same run. */
const latestGeneratedAt = computed(() =>
  recommendations.value.reduce((newest, rec) => Math.max(newest, rec.generatedAt), 0)
);

const generatedAgo = computed(() =>
  latestGeneratedAt.value > 0
    ? FormattingService.formatRelativeTime(latestGeneratedAt.value).toLowerCase()
    : ''
);

/** The exact instant, kept as hover text so the relative line stays readable without losing it. */
const generatedExact = computed(() =>
  latestGeneratedAt.value > 0 ? FormattingService.formatTimestamp(latestGeneratedAt.value) : ''
);

/**
 * The measured hotspots the patch can be joined to. Only grounded claims qualify: a claim whose frame
 * was never sampled has no share of the profile to report, so annotating a diff line with it would be
 * asserting a measurement Jeffrey never made.
 */
const heatMarks = computed<HeatMark[]>(() => {
  const claims = selectedRecommendation.value?.claims ?? [];
  return claims
    .filter(claim => claim.grounded && claim.sourcePath !== null)
    .map(claim => {
      const cited = splitCitedPath(claim.sourcePath as string);
      return {
        path: cited.path,
        line: cited.line,
        pct: claim.selfPct,
        frame: claim.citedFrame
      };
    });
});

const costLabel = computed(() =>
  selectedRecommendation.value ? eventTypeCostLabel(selectedRecommendation.value.eventType) : ''
);

const patchFileName = computed(() => {
  const label = selectedRecommendation.value
    ? labelFor(selectedRecommendation.value.eventType)
    : 'advisor';
  return `advisor-${label.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
});

const renderedReport = computed(() =>
  MarkdownRenderer.render(selectedRecommendation.value?.recommendations)
);

onMounted(load);
</script>

<style scoped>
/* The docket: what was analyzed, then the reports ranked worst-first. */
.docket {
  margin-bottom: 1.1rem;
}

.docket-lead {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin: 0 0 0.6rem;
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.docket-count {
  font-weight: 600;
  color: var(--color-text);
}

.docket-path,
.docket-ref {
  font-family: var(--font-family-monospace);
}

.docket-path {
  color: var(--color-heading-dark);
  font-weight: 600;
}

.docket-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 0.5rem;
}

/* The left spine is the type's own accent — the same device the Overview's manifest uses, so the two
   Advisor pages read as one family. Selection tints the card rather than flooding it, which keeps a
   red type (Blocking) from reading as an error the moment you pick it. */
.docket-item {
  appearance: none;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--et);
  border-radius: var(--radius-base);
  background: var(--color-bg-card);
  padding: 0.5rem 0.7rem;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  transition: background 0.16s, border-color 0.16s;
}

.docket-item:hover {
  background: var(--color-light);
}

.docket-item.selected {
  background: var(--et-light);
  border-color: var(--et);
}

.docket-name {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-heading-dark);
}

.docket-name i {
  color: var(--et);
}

.docket-verdict {
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.docket-item.selected .docket-verdict {
  color: var(--et);
}

/* The patch sits between the claims and the prose: you see what was measured, then what to do about
   it, then why. */
.patch-block {
  padding: 0 1rem 0.25rem;
}

.patch-cap {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-heading-dark);
  margin-bottom: 0.5rem;
}

.patch-cap i {
  color: var(--color-primary);
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
