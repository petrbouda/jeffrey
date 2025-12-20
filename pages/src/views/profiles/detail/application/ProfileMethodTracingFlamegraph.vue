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
import { onBeforeMount } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import SearchBarComponent from '@/components/SearchBarComponent.vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import PrimaryFlamegraphClient from '@/services/flamegraphs/client/PrimaryFlamegraphClient';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import EventTypes from '@/services/EventTypes';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter';
import type FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;

// Flamegraph infrastructure
let graphUpdater: GraphUpdater;
let flamegraphTooltip: FlamegraphTooltip;

// Configuration
const useThreadMode = false;
const useWeight = true;

onBeforeMount(() => {
  const flamegraphClient = new PrimaryFlamegraphClient(
    workspaceId.value!,
    projectId.value!,
    profileId,
    EventTypes.METHOD_TRACE,
    useThreadMode,
    useWeight,
    false,  // excludeNonJavaSamples
    false,  // excludeIdleSamples
    false,  // onlyUnsafeAllocationSamples
    null    // threadInfo
  );

  graphUpdater = new FullGraphUpdater(flamegraphClient, true);
  graphUpdater.setTimeseriesSearchEnabled(true);
  flamegraphTooltip = FlamegraphTooltipFactory.create(
    EventTypes.METHOD_TRACE,
    useWeight,
    false  // isDifferential
  );
});
</script>

<template>
  <div style="padding-left: 5px; padding-right: 5px">
    <!-- Search Bar -->
    <SearchBarComponent :graph-updater="graphUpdater" :with-timeseries="true" />

    <!-- Timeseries Chart -->
    <ApexTimeSeriesChart
      :graph-updater="graphUpdater"
      :primary-axis-type="TimeseriesEventAxeFormatter.resolveAxisFormatter(EventTypes.METHOD_TRACE)"
      :visible-minutes="60"
      :zoom-enabled="true"
      time-unit="milliseconds"
    />

    <!-- Flamegraph -->
    <FlamegraphComponent
      :with-timeseries="true"
      :use-weight="useWeight"
      :use-guardian="null"
      :scrollable-wrapper-class="null"
      :flamegraph-tooltip="flamegraphTooltip"
      :graph-updater="graphUpdater"
    />
  </div>
</template>
