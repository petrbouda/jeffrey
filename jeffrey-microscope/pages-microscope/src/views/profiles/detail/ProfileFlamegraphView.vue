<!--
  - Jeffrey
  - Copyright (C) 2024 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<script setup lang="ts">
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import SearchBarComponent from '@/components/SearchBarComponent.vue';
import CpuTimeSampleLossAlert from '@/components/alerts/CpuTimeSampleLossAlert.vue';
import { computed, onBeforeMount, ref } from 'vue';
import SecondaryProfileService from '@/services/SecondaryProfileService';
import { useRoute } from 'vue-router';
import FeatureType from '@/services/api/model/FeatureType';

import PrimaryFlamegraphClient from '@/services/api/PrimaryFlamegraphClient';
import DifferentialFlamegraphClient from '@/services/api/DifferentialFlamegraphClient';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter.ts';
import { linkedGraphState } from '@/services/flamegraphs/FlamegraphLinkQuery';
import { flamegraphViewOpening } from '@/services/flamegraphs/FlamegraphViewOpening';
import { profileStore } from '@/stores/profileStore';
import EmptyState from '@shared/components/EmptyState.vue';

const route = useRoute();

// `disabledFeatures` is provided by ProfileDetail's router-view (already resolved by the time this
// view is opened). The timeseries strip is hidden when the backend disables TIMESERIES, e.g. for
// pprof profiles, which are aggregated and carry no per-sample timestamps (all samples share the
// profile's collection time, so the timeseries collapses into a single meaningless spike).
const props = defineProps<{
  disabledFeatures?: FeatureType[];
}>();

const showTimeseries = computed(
  () => !(props.disabledFeatures ?? []).includes(FeatureType.TIMESERIES)
);

let flamegraphTooltip: FlamegraphTooltip;
let graphUpdater: GraphUpdater;

// Reactive refs for template-bound values - initialized in onBeforeMount when route is resolved
const profileId = ref<string>('');
const eventType = ref<string>('');
const useWeight = ref(false);
const isDifferential = ref(false);
const isPrimary = ref(false);
// A differential graph with no baseline to subtract - a link whose baseline could not be loaded, or
// one opened without any. Drawn as its own state rather than as a request for profile "null".
const missingBaseline = ref(false);

function scrollToTop() {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }
}

onBeforeMount(() => {
  // Read query params here where the route is guaranteed to be resolved
  const queryParams = route.query;

  const eventTypeValue = queryParams.eventType as string;
  const useThreadMode = queryParams.useThreadMode === 'true';
  const useWeightValue = queryParams.useWeight === 'true';
  const excludeNonJavaSamples = queryParams.excludeNonJavaSamples === 'true';
  const excludeIdleSamples = queryParams.excludeIdleSamples === 'true';
  const onlyUnsafeAllocationSamples = queryParams.onlyUnsafeAllocationSamples === 'true';
  // Only a differential graph needs a baseline; a link that names no graphMode opens the primary.
  const opening = flamegraphViewOpening(queryParams.graphMode, SecondaryProfileService.id());
  const isPrimaryValue = opening.kind === 'PRIMARY';
  const isDifferentialValue = !isPrimaryValue;

  // Set reactive refs for template
  profileId.value = route.params.profileId as string;
  eventType.value = eventTypeValue;
  useWeight.value = useWeightValue;
  isPrimary.value = isPrimaryValue;
  isDifferential.value = isDifferentialValue;

  if (opening.kind === 'MISSING_BASELINE') {
    missingBaseline.value = true;
    return;
  }

  let flamegraphClient;
  if (opening.kind === 'PRIMARY') {
    flamegraphClient = new PrimaryFlamegraphClient(
      route.params.profileId as string,
      eventTypeValue,
      useThreadMode,
      useWeightValue,
      excludeNonJavaSamples,
      excludeIdleSamples,
      onlyUnsafeAllocationSamples,
      null
    );
  } else {
    flamegraphClient = new DifferentialFlamegraphClient(
      route.params.profileId as string,
      opening.baselineId,
      eventTypeValue,
      useWeightValue,
      excludeNonJavaSamples,
      excludeIdleSamples,
      onlyUnsafeAllocationSamples
    );
  }

  graphUpdater = new FullGraphUpdater(flamegraphClient, true);
  // A link may open the graph on a window of the recording and a search - the graph an MCP answer
  // described. Without them the view opens as it always has.
  graphUpdater.openAt(linkedGraphState(queryParams, profileStore.recordingWindow.value));
  graphUpdater.setTimeseriesEnabled(showTimeseries.value);
  graphUpdater.setTimeseriesSearchEnabled(isPrimaryValue && showTimeseries.value);
  flamegraphTooltip = FlamegraphTooltipFactory.create(
    eventTypeValue,
    useWeightValue,
    isDifferentialValue
  );
});
</script>

<template>
  <EmptyState
    v-if="missingBaseline"
    icon="bi-file-diff"
    title="No Baseline Profile"
    description="This differential graph needs a baseline. Select a secondary profile to compare against."
  />
  <div v-else style="padding-left: 5px; padding-right: 5px">
    <CpuTimeSampleLossAlert v-if="profileId" :profile-id="profileId" :event-type="eventType" />
    <SearchBarComponent :graph-updater="graphUpdater" :with-timeseries="showTimeseries" />
    <TimeSeriesChart
      v-if="showTimeseries"
      :graph-updater="graphUpdater"
      :primary-title="isDifferential ? 'Primary' : undefined"
      :secondary-title="isDifferential ? 'Secondary' : undefined"
      :primary-axis-type="TimeseriesEventAxeFormatter.resolveAxisFormatter(useWeight, eventType)"
      :visible-minutes="60"
      :zoom-enabled="true"
      time-unit="seconds"
    />
    <FlamegraphComponent
      :with-timeseries="isPrimary && showTimeseries"
      :use-weight="useWeight"
      :scrollable-wrapper-class="null"
      :flamegraph-tooltip="flamegraphTooltip"
      :graph-updater="graphUpdater"
      @loaded="scrollToTop"
    />
  </div>
</template>
