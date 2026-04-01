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
import {onBeforeMount, ref} from 'vue';
import SecondaryProfileService from '@/services/SecondaryProfileService';

interface Props {
  profile: any;
  secondaryProfile: any;
  disabledFeatures: string[];
}

defineProps<Props>();
import MessageBus from '@/services/MessageBus';
import Utils from '@/services/Utils';
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import SearchBarComponent from "@/components/SearchBarComponent.vue";
import TimeSeriesChart from "@/components/TimeSeriesChart.vue";
import router from "@/router";
import GraphType from "@/services/flamegraphs/GraphType";
import SubSecondComponent from "@/components/SubSecondComponent.vue";
import {useRoute} from "vue-router";

import FlamegraphClient from "@/services/api/FlamegraphClient";
import PrimaryFlamegraphClient from "@/services/api/PrimaryFlamegraphClient";
import DifferentialFlamegraphClient from "@/services/api/DifferentialFlamegraphClient";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/services/flamegraphs/tooltips/FlamegraphTooltipFactory";
import SubSecondDataProvider from "@/services/subsecond/SubSecondDataProvider";
import SubSecondDataProviderImpl from "@/services/subsecond/SubSecondDataProviderImpl";
import HeatmapTooltip from "@/services/subsecond/HeatmapTooltip";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import OnlyFlamegraphGraphUpdater from "@/services/flamegraphs/updater/OnlyFlamegraphGraphUpdater";
import TimeRange from "@/services/api/model/TimeRange";
import TimeseriesEventAxeFormatter from "@/services/timeseries/TimeseriesEventAxeFormatter";
import GenericModal from '@/components/GenericModal.vue';

const route = useRoute()

const showDialog = ref<boolean>(false);
const subSecondRef = ref<InstanceType<typeof SubSecondComponent> | null>(null);
const timeseriesData = ref<number[][] | null>(null);
const timeseriesSecondaryData = ref<number[][] | undefined>(undefined);

// Reactive refs for template-bound values - initialized in onBeforeMount when route is resolved
const eventType = ref<string>('');
const useWeight = ref(false);
const isDifferential = ref(false);

let graphUpdater: GraphUpdater
let flamegraphTooltip: FlamegraphTooltip
let timeseriesClient: PrimaryFlamegraphClient | DifferentialFlamegraphClient

function scrollToTop() {
  const wrapper = document.querySelector('.flamegraphModal');
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}


let primarySubSecondDataProvider: SubSecondDataProvider
let secondarySubSecondDataProvider: SubSecondDataProvider | null = null

onBeforeMount(() => {
  // Read query params here where the route is guaranteed to be resolved
  const queryParams = route.query;

  const eventTypeValue = queryParams.eventType as string | undefined;
  const useWeightValue = queryParams.useWeight === 'true';
  const isPrimaryValue = queryParams.graphMode === GraphType.PRIMARY;
  const isDifferentialValue = queryParams.graphMode === GraphType.DIFFERENTIAL;

  // Validate required query parameter
  if (!eventTypeValue) {
    console.error('SubSecondView: eventType query parameter is required')
    router.back()
    return
  }

  // Set reactive refs for template
  eventType.value = eventTypeValue;
  useWeight.value = useWeightValue;
  isDifferential.value = isDifferentialValue;

  // Scroll the workspace-content container to top
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }

  primarySubSecondDataProvider = new SubSecondDataProviderImpl(
      route.params.profileId as string,
      eventTypeValue,
      useWeightValue,
  )

  if (isDifferentialValue) {
    secondarySubSecondDataProvider = new SubSecondDataProviderImpl(
        SecondaryProfileService.id() as string,
        eventTypeValue,
        useWeightValue,
    )
  }

  let flamegraphClient: FlamegraphClient
  if (isPrimaryValue) {
    flamegraphClient = new PrimaryFlamegraphClient(
        route.params.profileId as string,
        eventTypeValue,
        false,
        useWeightValue,
        false,
        false,
        false,
        null
    )
    timeseriesClient = flamegraphClient as PrimaryFlamegraphClient
  } else {
    flamegraphClient = new DifferentialFlamegraphClient(
        route.params.profileId as string,
        SecondaryProfileService.id() as string,
        eventTypeValue,
        useWeightValue,
        false,
        false,
        false,
    )
    timeseriesClient = flamegraphClient as DifferentialFlamegraphClient
  }

  graphUpdater = new OnlyFlamegraphGraphUpdater(flamegraphClient, false)
  flamegraphTooltip = FlamegraphTooltipFactory.create(eventTypeValue, useWeightValue, !isPrimaryValue)

  // Fetch timeseries data for the brush chart
  timeseriesClient.provideTimeseries(null)
      .then(data => {
        if (data.series && data.series.length > 0) {
          timeseriesData.value = data.series[0].data
          // Extract secondary series if in differential mode
          if (data.series.length > 1) {
            timeseriesSecondaryData.value = data.series[1].data
          }
        }
      })
      .catch(error => console.error('Error loading timeseries data:', error))
})

function createOnSelectedCallback() {
  return function (startTime: number[], endTime: number[]) {
    let selectedTimeRange = Utils.toTimeRange(startTime, endTime, false);
    showFlamegraph(selectedTimeRange);
  };
}

function showFlamegraph(timeRange: TimeRange) {
  // Show the flamegraph dialog
  showDialog.value = true

  MessageBus.emit(MessageBus.SUBSECOND_SELECTION_CLEAR, {});

  setTimeout(() => {
    graphUpdater.updateWithZoom(timeRange)
  }, 200);
}

function onTimeRangeChange(payload: { start: number; end: number; isZoomed: boolean }) {
  // TimeSeriesChart already handles duplicate suppression via lastProcessedSelection check,
  // so we can process all events that reach here
  if (payload.isZoomed) {
    // Convert from seconds to milliseconds for backend API
    const newTimeRange = new TimeRange(
        Math.floor(payload.start * 1000),
        Math.ceil(payload.end * 1000),
        false
    );

    // Reload heatmap with new time range
    subSecondRef.value?.reloadWithTimeRange(newTimeRange);
  } else {
    // User reset to full range - reload heatmap without time range (no offset)
    subSecondRef.value?.reloadWithTimeRange();
  }
}
</script>

<template>
  <div>
  <div style="padding-left: 5px; padding-right: 5px">
    <TimeSeriesChart
        v-if="timeseriesData"
        :primary-data="timeseriesData"
        :secondary-data="timeseriesSecondaryData"
        :primary-title="isDifferential ? 'Primary' : undefined"
        :secondary-title="isDifferential ? 'Secondary' : undefined"
        :primary-axis-type="TimeseriesEventAxeFormatter.resolveAxisFormatter(useWeight, eventType!)"
        :visible-minutes="5"
        :zoom-enabled="true"
        :fixed-window-minutes="5"
        time-unit="seconds"
        @update:timeRange="onTimeRangeChange"
    />
  </div>

  <SubSecondComponent
      ref="subSecondRef"
      :primary-data-provider="primarySubSecondDataProvider"
      :primary-selected-callback="createOnSelectedCallback()"
      :secondary-data-provider="secondarySubSecondDataProvider"
      :secondary-selected-callback="createOnSelectedCallback()"
      :tooltip="new HeatmapTooltip(eventType!, useWeight)"
      :event-type="eventType!"
      :use-weight="useWeight"
  />

  <GenericModal
      modal-id="flamegraphModal"
      :show="showDialog"
      size="fullscreen"
      :show-footer="false"
      @update:show="showDialog = $event">
    <template #header>
      <button type="button" class="btn-close" @click="showDialog = false" aria-label="Close"></button>
    </template>
    <SearchBarComponent
        v-if="showDialog"
        :graph-updater="graphUpdater"
        :with-timeseries="false"/>
    <FlamegraphComponent
        v-if="showDialog"
        :with-timeseries="false"
        :use-weight="useWeight"
        :use-guardian="null"
        scrollable-wrapper-class="flamegraphModal"
        :flamegraph-tooltip="flamegraphTooltip"
        :graph-updater="graphUpdater"
        @loaded="scrollToTop"/>
  </GenericModal>
  </div>
</template>

