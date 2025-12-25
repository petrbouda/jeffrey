<!--
  ~ Jeffrey
  ~ Copyright (C) 2025 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<script setup lang="ts">
import { computed, defineProps, onBeforeMount, ref, withDefaults } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import SearchBarComponent from '@/components/SearchBarComponent.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import TracingDisabledFeatureAlert from '@/components/alerts/TracingDisabledFeatureAlert.vue';
import PrimaryFlamegraphClient from '@/services/api/PrimaryFlamegraphClient';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import EventTypes from '@/services/EventTypes';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter';
import type FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FeatureType from '@/services/api/model/FeatureType';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;

// Check if tracing dashboard is disabled
const isTracingDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.TRACING_DASHBOARD);
});

// Flamegraph infrastructure
let graphUpdater: GraphUpdater;
let flamegraphTooltip: FlamegraphTooltip;

// Configuration - reactive for dynamic updates
const useThreadMode = ref(false);
const useWeight = ref(true);

function onModeChange(threadMode: boolean, weight: boolean) {
  useThreadMode.value = threadMode;
  useWeight.value = weight;
}

function scrollToTop() {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }
}

onBeforeMount(() => {
  // Only initialize flamegraph if the feature is not disabled
  if (isTracingDisabled.value) {
    return;
  }

  const flamegraphClient = new PrimaryFlamegraphClient(
    workspaceId.value!,
    projectId.value!,
    profileId,
    EventTypes.METHOD_TRACE,
    useThreadMode.value,
    useWeight.value,
    false,  // excludeNonJavaSamples
    false,  // excludeIdleSamples
    false,  // onlyUnsafeAllocationSamples
    null    // threadInfo
  );

  graphUpdater = new FullGraphUpdater(flamegraphClient, true);
  graphUpdater.setTimeseriesSearchEnabled(true);
  flamegraphTooltip = FlamegraphTooltipFactory.create(
    EventTypes.METHOD_TRACE,
    useWeight.value,
    false  // isDifferential
  );
});
</script>

<template>
  <div>
    <!-- Feature Disabled State -->
    <TracingDisabledFeatureAlert v-if="isTracingDisabled" />

    <div v-else style="padding-left: 5px; padding-right: 5px">
      <!-- Search Bar with Mode Controls -->
      <SearchBarComponent
        :graph-updater="graphUpdater"
        :with-timeseries="true"
        :show-mode-controls="true"
        thread-mode-label="Thread Mode"
        weight-mode-label="Total Time"
        :initial-thread-mode="useThreadMode"
        :initial-use-weight="useWeight"
        @mode-change="onModeChange"
      />

      <!-- Timeseries Chart -->
      <TimeSeriesChart
        :graph-updater="graphUpdater"
        :primary-axis-type="TimeseriesEventAxeFormatter.resolveAxisFormatter(useWeight, EventTypes.METHOD_TRACE)"
        :visible-minutes="60"
        :zoom-enabled="true"
        time-unit="seconds"
      />

      <!-- Flamegraph -->
      <FlamegraphComponent
        :with-timeseries="true"
        :use-weight="useWeight"
        :use-guardian="null"
        :scrollable-wrapper-class="null"
        :flamegraph-tooltip="flamegraphTooltip"
        :graph-updater="graphUpdater"
        @loaded="scrollToTop"
      />
    </div>
  </div>
</template>
