<!--
  - Jeffrey
  - Copyright (C) 2024 Petr Bouda
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

<script setup lang="ts">
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import SearchBarComponent from '@/components/SearchBarComponent.vue';
import { onBeforeMount, ref } from 'vue';
import SecondaryProfileService from '@/services/SecondaryProfileService';
import GraphType from '@/services/flamegraphs/GraphType';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import PrimaryFlamegraphClient from '@/services/api/PrimaryFlamegraphClient';
import DifferentialFlamegraphClient from '@/services/api/DifferentialFlamegraphClient';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter.ts';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();

let flamegraphTooltip: FlamegraphTooltip;
let graphUpdater: GraphUpdater;

// Reactive refs for template-bound values - initialized in onBeforeMount when route is resolved
const eventType = ref<string>('');
const useWeight = ref(false);
const isDifferential = ref(false);
const isPrimary = ref(false);

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
  const isPrimaryValue = queryParams.graphMode === GraphType.PRIMARY;
  const isDifferentialValue = queryParams.graphMode === GraphType.DIFFERENTIAL;

  // Set reactive refs for template
  eventType.value = eventTypeValue;
  useWeight.value = useWeightValue;
  isPrimary.value = isPrimaryValue;
  isDifferential.value = isDifferentialValue;

  let flamegraphClient;
  if (isPrimaryValue) {
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
      SecondaryProfileService.id() as string,
      eventTypeValue,
      useWeightValue,
      excludeNonJavaSamples,
      excludeIdleSamples,
      onlyUnsafeAllocationSamples
    );
  }

  graphUpdater = new FullGraphUpdater(flamegraphClient, true);
  graphUpdater.setTimeseriesSearchEnabled(isPrimaryValue);
  flamegraphTooltip = FlamegraphTooltipFactory.create(eventTypeValue, useWeightValue, isDifferentialValue);
});
</script>

<template>
  <div style="padding-left: 5px; padding-right: 5px">
    <SearchBarComponent :graph-updater="graphUpdater" :with-timeseries="true" />
    <TimeSeriesChart
      :graph-updater="graphUpdater"
      :primary-title="isDifferential ? 'Primary' : undefined"
      :secondary-title="isDifferential ? 'Secondary' : undefined"
      :primary-axis-type="TimeseriesEventAxeFormatter.resolveAxisFormatter(useWeight, eventType)"
      :visible-minutes="60"
      :zoom-enabled="true"
      time-unit="seconds"
    />
    <FlamegraphComponent
      :with-timeseries="isPrimary"
      :use-weight="useWeight"
      :use-guardian="null"
      :scrollable-wrapper-class="null"
      :flamegraph-tooltip="flamegraphTooltip"
      :graph-updater="graphUpdater"
      @loaded="scrollToTop"
    />
  </div>
</template>
