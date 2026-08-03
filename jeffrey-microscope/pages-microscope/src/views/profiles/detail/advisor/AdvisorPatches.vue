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
      <!-- Every type is listed, including those the model proposed nothing for: a type that produced
           no diff is a result, and hiding it would read as a type that was never analyzed. -->
      <AdvisorDocket v-model:selected="selectedType" :items="items">
        <template #rows="{ item }">
          <template v-if="recommendationFor(item.eventType)">
            <AdvisorDocketRow label="Diff">
              <template v-if="hasPatch(recommendationFor(item.eventType)!.patch)">
                <span class="added">+{{ statsFor(item.eventType).added }}</span>
                <span class="removed">−{{ statsFor(item.eventType).removed }}</span>
              </template>
              <template v-else>none</template>
            </AdvisorDocketRow>
            <AdvisorDocketRow label="Files">{{ filesLabel(item.eventType) }}</AdvisorDocketRow>
            <AdvisorDocketRow
              label="Top method"
              mono
              :title="recommendationFor(item.eventType)!.dominantMethod"
            >
              {{ topMethod(recommendationFor(item.eventType)!.dominantMethod) }}
            </AdvisorDocketRow>
          </template>
          <AdvisorDocketRow v-else label="Patch">not analysed yet</AdvisorDocketRow>
        </template>
      </AdvisorDocket>

      <!-- A type this recording has no samples for: what is missing, and how to record it. -->
      <AdvisorMissingType
        v-if="selectedMissing"
        :event-type="selectedMissing.eventType"
        :label="selectedMissing.label"
        missing-artifact="diff to apply"
      />

      <!-- The diff carries its own frame — file, cost, counts and actions are all on its header bar —
           so it stands on its own rather than inside a card that would repeat the type's name. -->
      <template v-else-if="selected">
        <DiffViewer
          v-if="hasPatch(selected.patch)"
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
            <router-link :to="recommendationsPath" class="guard-btn">
              <i class="bi bi-arrow-right"></i>
              Read the {{ labelFor(selected.eventType) }} report
            </router-link>
          </template>
        </EmptyState>
      </template>

      <!-- Recorded, but this run never analysed it. -->
      <EmptyState
        v-else
        icon="bi-file-earmark-diff"
        :title="`No ${selectedLabel} patch yet`"
        description="The Advisor analyses this type on its next run."
      />
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
import { severityLabel, severityVariant } from '@shared/services/severityDisplay';
import type { AdvisorRecommendation } from '@/services/api/model/Advisor';
import AiDisabledFeatureAlert from '@/components/alerts/AiDisabledFeatureAlert.vue';
import AdvisorDocket from '@/views/profiles/detail/advisor/AdvisorDocket.vue';
import AdvisorDocketRow from '@/views/profiles/detail/advisor/AdvisorDocketRow.vue';
import AdvisorMissingType from '@/views/profiles/detail/advisor/AdvisorMissingType.vue';
import {
  docketItems,
  hasPatch,
  patchStats,
  type PatchStats
} from '@/views/profiles/detail/advisor/advisorDocket';
import { shortMethodName } from '@/views/profiles/detail/advisor/eventTypeStyle';
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
const recommendationsPath = computed(() => `/profiles/${profileId}/advisor/recommendations`);

const labelFor = (code: string): string =>
  eventTypes.value.find(type => type.eventType === code)?.label ?? code;

const recommendationFor = (eventType: string): AdvisorRecommendation | undefined =>
  recommendations.value.find(item => item.eventType === eventType);

const statsFor = (eventType: string): PatchStats =>
  patchStats(recommendationFor(eventType)?.patch ?? null);

const filesLabel = (eventType: string): string => {
  const files = statsFor(eventType).files;
  return files === 0 ? '\u2014' : String(files);
};

const topMethod = (method: string): string => (method === '' ? 'unknown' : shortMethodName(method));

/**
 * CPU, Wall-Clock, Allocation, Blocking — the Advisor's canonical order, the same one Prompts and
 * Recommendations use, so a type sits in the same place on all three pages. Whether a type produced a
 * patch is said on its card rather than by moving it.
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

/**
 * Opens on the first type that actually has a patch, so the page lands on something to read rather
 * than on an explanation of an absence — the docket order itself does not guarantee that.
 */
const firstWithPatch = computed(
  () =>
    items.value.find(item => hasPatch(recommendationFor(item.eventType)?.patch ?? null))
      ?.eventType ?? null
);

const selectedItem = computed(
  () =>
    items.value.find(item => item.eventType === selectedType.value) ??
    items.value.find(item => item.eventType === firstWithPatch.value)
);

const selectedLabel = computed(() => selectedItem.value?.label ?? '');

/** Set only when the selected type is one this recording carries no samples for. */
const selectedMissing = computed(() =>
  selectedItem.value?.available === false ? selectedItem.value : undefined
);

const selected = computed(() =>
  selectedItem.value ? recommendationFor(selectedItem.value.eventType) : undefined
);

const patchFileName = computed(() => {
  const label = selected.value ? labelFor(selected.value.eventType) : 'advisor';
  return `advisor-${label.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
});

onMounted(load);
</script>

<style scoped>
/* The docket's own styling lives in AdvisorDocket; only the diff counts are this page's. */
.added {
  color: var(--color-success-dark);
}

.removed {
  color: var(--color-danger);
  margin-left: 0.3rem;
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
