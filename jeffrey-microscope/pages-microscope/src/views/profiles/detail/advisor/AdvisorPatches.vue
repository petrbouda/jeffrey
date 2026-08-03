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
  <LoadingState v-if="loading" message="Loading patches..." />

  <ErrorState v-else-if="error" :message="error" />

  <PageHeader
    v-else
    title="Patches"
    description="The code changes the Advisor proposes, ranked by the measured cost of what they touch"
    icon="bi-file-earmark-diff"
  >
    <AiDisabledFeatureAlert v-if="aiDisabled" />

    <template v-else-if="hasResults">
      <!-- Every analyzed type is listed, including those the model proposed nothing for: a type that
           produced no diff is a result, and hiding it would read as a type that was never analyzed. -->
      <div class="docket">
        <div class="docket-list">
          <button
            v-for="rec in rankedRecommendations"
            :key="rec.eventType"
            type="button"
            class="docket-item"
            :class="{ selected: selectedType === rec.eventType, empty: !hasPatch(rec) }"
            :style="eventTypeVars(rec.eventType)"
            @click="selectedType = rec.eventType"
          >
            <span class="docket-name">
              <i class="bi" :class="eventTypeStyle(rec.eventType).icon"></i>
              {{ labelFor(rec.eventType) }}
            </span>
            <span class="docket-verdict">
              {{ severityLabel(rec.severity) }} · {{ costOf(rec) }}
            </span>
          </button>
        </div>
      </div>

      <!-- The diff carries its own frame — file, cost, counts and actions are all on its header bar —
           so it stands on its own rather than inside a card that would repeat the type's name. -->
      <template v-if="selected">
        <DiffViewer
          v-if="hasPatch(selected)"
          :patch="selected.patch as string"
          :file-name="patchFileName"
        />

        <!-- Analyzed, but nothing to apply. Said plainly rather than left as an absence. -->
        <EmptyState
          v-else
          icon="bi-file-earmark-diff"
          :title="`No code change proposed for ${labelFor(selected.eventType)}`"
          description="The Advisor analyzed this type and did not find an edit worth making. Its report explains what it measured."
        >
          <template #action>
            <router-link :to="findingsPath" class="guard-btn">
              <i class="bi bi-arrow-right"></i>
              Read the {{ labelFor(selected.eventType) }} report
            </router-link>
          </template>
        </EmptyState>
      </template>
    </template>

    <!-- The Advisor has not produced anything yet for this profile. -->
    <EmptyState
      v-else
      icon="bi-file-earmark-diff"
      :title="isRunning ? 'A run is in progress' : 'No patches yet'"
      :description="
        isRunning
          ? 'The Advisor is analyzing your profiles now — watch it on the Overview.'
          : 'Run the Advisor to turn the hottest frames in each profile into a diff against your source.'
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
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import DiffViewer from '@shared/components/DiffViewer.vue';
import FormattingService from '@shared/services/FormattingService';
import { severityLabel, severityRank } from '@shared/services/severityDisplay';
import type { AdvisorRecommendation } from '@/services/api/model/Advisor';
import AiDisabledFeatureAlert from '@/components/alerts/AiDisabledFeatureAlert.vue';
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

const { loading, error, eventTypes, recommendations, hasResults, isRunning, load } =
  useAdvisor(profileId);

/**
 * Selection is local rather than the composable's `selectedEventType`, so the two Advisor pages can
 * be read side by side without one page's choice moving the other's.
 */
const selectedType = ref<string | null>(null);

const aiDisabled = computed(
  () => props.disabledFeatures?.includes(FeatureType.AI_ANALYSIS) === true
);

const overviewPath = computed(() => `/profiles/${profileId}/advisor`);
const findingsPath = computed(() => `/profiles/${profileId}/advisor/findings`);

const labelFor = (code: string): string =>
  eventTypes.value.find(type => type.eventType === code)?.label ?? code;

const hasPatch = (recommendation: AdvisorRecommendation): boolean =>
  recommendation.patch !== null && recommendation.patch.trim() !== '';

/** What a type's card says under its name: the price of its patch, or that there isn't one. */
const costOf = (recommendation: AdvisorRecommendation): string => {
  if (!hasPatch(recommendation)) {
    return 'no patch';
  }
  return `${FormattingService.formatPercentValue(recommendation.dominantSelfPct)} ${eventTypeCostLabel(recommendation.eventType)}`;
};

/**
 * Types with a patch first — this page exists to hand you a change, so the ones that have none belong
 * after the ones that do. Within each group the ranking is the Findings docket's: worst severity
 * first, then the most expensive frame, then alphabetically, so the order is stable across reloads.
 */
const rankedRecommendations = computed(() =>
  [...recommendations.value].sort((left, right) => {
    if (hasPatch(left) !== hasPatch(right)) {
      return hasPatch(left) ? -1 : 1;
    }
    const bySeverity = severityRank(left.severity) - severityRank(right.severity);
    if (bySeverity !== 0) {
      return bySeverity;
    }
    const byCost = right.dominantSelfPct - left.dominantSelfPct;
    if (byCost !== 0) {
      return byCost;
    }
    return labelFor(left.eventType).localeCompare(labelFor(right.eventType));
  })
);

/**
 * Opens on the first type that actually has a patch — the ranking puts it first — so the page lands
 * on something to read rather than on an explanation of an absence.
 */
const selected = computed(
  () =>
    rankedRecommendations.value.find(rec => rec.eventType === selectedType.value) ??
    rankedRecommendations.value[0]
);

const patchFileName = computed(() => {
  const label = selected.value ? labelFor(selected.value.eventType) : 'advisor';
  return `advisor-${label.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
});

onMounted(load);
</script>

<style scoped>
/* The docket: the patches ranked by what each one is worth. */
.docket {
  margin-bottom: 1.1rem;
}

.docket-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 0.5rem;
}

/* The left spine is the type's own accent — the same device the Findings docket uses, so the Advisor's
   pages read as one family. Selection tints the card rather than flooding it, which keeps a red type
   (Blocking) from reading as an error the moment you pick it. */
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

/* Analyzed but with nothing to apply: still listed, still selectable, just visibly not the reason you
   came to this page. The accent stays so the type keeps its identity across the Advisor's pages. */
.docket-item.empty .docket-name {
  color: var(--color-text-muted);
}

.docket-item.empty .docket-name i {
  opacity: 0.55;
}

.docket-item.empty .docket-verdict {
  font-style: italic;
  letter-spacing: 0.03em;
  text-transform: none;
}

/* action button inside the EmptyState (nothing to apply) */
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
