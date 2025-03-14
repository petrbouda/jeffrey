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

import {onMounted, ref} from 'vue';
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import GuardianService from "@/service/guardian/GuardianService";
import Utils from "@/service/Utils";
import GraphType from "@/service/flamegraphs/GraphType";
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import {useRoute} from "vue-router";
import GuardianFlamegraphClient from "@/service/flamegraphs/client/GuardianFlamegraphClient";
import FlamegraphTooltip from "@/service/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/service/flamegraphs/tooltips/FlamegraphTooltipFactory";
import GraphUpdater from "@/service/flamegraphs/updater/GraphUpdater";
import PrimaryGraphUpdater from "@/service/flamegraphs/updater/PrimaryGraphUpdater";
import GuardAnalysisResult from "@/service/flamegraphs/model/GuardAnalysisResult";
import GuardResponse from "@/service/flamegraphs/model/GuardResponse";
import GuardVisualization from "@/service/flamegraphs/model/GuardVisualization";

const route = useRoute()

let guards = ref<GuardResponse[]>([]);

let tooltip: HTMLElement
let tooltipTimeoutId: any
let autoAnalysisCard: HTMLElement

const showFlamegraphDialog = ref(false);
let activeGuardVisualization: GuardVisualization;

let flamegraphTooltip: FlamegraphTooltip
let graphUpdater: GraphUpdater

onMounted(() => {
  GuardianService.list(route.params.projectId as string, route.params.profileId as string)
      .then((data) => guards.value = data);

  tooltip = document.getElementById('analysisTooltip')!;
  autoAnalysisCard = document.getElementById('autoAnalysisCard')!;
});

const items = [
  {label: 'Guardian', route: 'guardian'}
]

function mapSeverity(severity: string) {
  if (severity === "INFO") {
    return "Information"
  } else if (severity === "WARNING") {
    return "Warning"
  } else if (severity === "NA") {
    return "Not Applicable"
  } else if (severity === "IGNORE") {
    return "Ignored"
  } else if (severity === "OK") {
    return "OK"
  } else {
    return severity
  }
}

const click_flamegraph = (guard: GuardAnalysisResult) => {
  if (Utils.isNotNull(guard.visualization)) {
    showFlamegraphDialog.value = true
    activeGuardVisualization = guard.visualization
    let flamegraphClient = new GuardianFlamegraphClient(
        route.params.projectId as string,
        guard.visualization.primaryProfileId,
        guard.visualization.eventType,
        guard.visualization.useWeight,
        guard.visualization.markers
    )

    graphUpdater = new PrimaryGraphUpdater(flamegraphClient)
    flamegraphTooltip = FlamegraphTooltipFactory.create(guard.visualization.eventType, guard.visualization.useWeight, false)
  }
}

const mouse_over = (event: MouseEvent, guard: GuardAnalysisResult) => {
  let currentTarget = event.currentTarget

  tooltip.style.visibility = 'hidden';
  clearTimeout(tooltipTimeoutId)
  tooltipTimeoutId = setTimeout(() => {
    generate_and_place_tooltip(event, currentTarget!!, guard)
  }, 500);
}

function generate_and_place_tooltip(event: MouseEvent, currentTarget: any, guard: GuardAnalysisResult) {
  tooltip.innerHTML = generateTooltip(guard)
  tooltip.style.top = currentTarget.offsetTop + currentTarget.offsetHeight + 'px';

  if (event.clientX > ((autoAnalysisCard.offsetWidth + autoAnalysisCard.offsetLeft) / 2)) {
    tooltip.style.left = (currentTarget.offsetLeft + (currentTarget.offsetWidth - tooltip.offsetWidth) - 20) + 'px';
  } else {
    tooltip.style.left = (currentTarget.offsetLeft + 20) + 'px';
  }

  tooltip.style.visibility = 'visible';
}

function divider(text: string) {
  return `<div class="m-2 ml-4 italic text-gray-500 text-sm">${text}</div>`
}

function generateTooltip(guard: GuardAnalysisResult) {
  let severity = ""
  if (guard.severity != null) {
    severity =
        `${divider("Severity")}
    <table class="pl-1 pr-1 text-sm">
      <tr>
        <td>${mapSeverity(guard.severity)}<td>
      </tr>
    </table>`
  }

  let summary = ""
  if (guard.summary != null) {
    summary =
        `${divider("Summary")}
    <table class="pl-1 pr-1 text-sm">
      <tr>
        <td>${guard.summary}<td>
      </tr>
    </table>`
  }

  let explanation = ""
  if (guard.explanation != null) {
    explanation =
        `${divider("Explanation")}
    <table class="pl-1 pr-1 text-sm">
      <tr>
        <td>${guard.explanation}<td>
      </tr>
    </table>`
  }

  let solution = ""
  if (guard.solution != null) {
    solution =
        `${divider("Solution")}
    <table class="pl-1 pr-1 text-sm">
      <tr>
        <td>${guard.solution}<td>
      </tr>
    </table>`
  }

  return `${severity}${summary}${explanation}${solution}`
}

function select_icon(guard: GuardAnalysisResult) {
  if (guard.severity === "OK") {
    return "check"
  } else if (guard.severity === "WARNING") {
    return "warning"
  } else if (guard.severity === "INFO") {
    return "info_i"
  } else if (guard.severity === "NA") {
    return "do_not_disturb_on"
  } else if (guard.severity === "IGNORE") {
    return "search_off"
  }
}

function select_color(guard: GuardAnalysisResult, type: string, shade: number) {
  if (guard.severity === "OK") {
    return type + "-green-" + shade
  } else if (guard.severity === "WARNING") {
    return type + "-red-" + shade
  } else if (guard.severity === "INFO") {
    return type + "-blue-" + shade
  } else if (guard.severity === "NA" || guard.severity === "IGNORE") {
    return type + "-gray-" + shade
  }
}

function removeTooltip() {
  tooltip.style.visibility = 'hidden';
  clearTimeout(tooltipTimeoutId)
}
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card card-w-title" id="autoAnalysisCard">
    <div class="col-12" v-for="guardWithCategory in guards">
      <Divider align="left" type="solid" style="z-index: 0">
        <span style="font-weight: bold">{{ guardWithCategory.category }}</span>
      </Divider>
      <div class="grid">
        <div class="col-12 md:col-6 lg:col-3" v-for="(guard, index) in guardWithCategory.results" :key="index"
             @mouseout="removeTooltip"
             @mouseover="mouse_over($event, guard)"
             @click="click_flamegraph(guard)">
          <div class="surface-card shadow-2 p-3 border-round hover:bg-gray-50"
               :class="{'cursor-pointer': Utils.isNotNull(guard.visualization)}">
            <div class="flex justify-center justify-content-between">
              <div>
                <span class="block text-900">{{ guard.rule }}</span>
                <span class="block text-400 mt-2" v-if="guard.score != null">Score: {{ guard.score }}</span>
                <span class="block text-400 mt-2" v-else>&nbsp;</span>
              </div>
              <div class="flex align-items-center">
                <div v-if="Utils.isNotNull(guard.visualization)">
              <span class="material-symbols-outlined text-3xl mr-2"
                    :class="select_color(guard, 'text', 700)">local_fire_department</span>
                </div>
                <div class="flex align-items-center justify-content-center border-round"
                     :class="select_color(guard, 'bg', 100)"
                     style="width: 2.5rem; height: 2.5rem;">
              <span class="material-symbols-outlined text-3xl"
                    :class="select_color(guard, 'text', 700)">{{ select_icon(guard) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div class="card p-2 border-1 bg-gray-50 w-4" style="visibility:hidden; position:absolute" id="analysisTooltip"></div>

  <Dialog class="scrollable" header=" " :pt="{root: 'overflow-hidden'}" v-model:visible="showFlamegraphDialog" modal
          :style="{ width: '95%' }" style="overflow-y: auto">
    <TimeseriesComponent
        :graph-type="GraphType.PRIMARY"
        :event-type="activeGuardVisualization.eventType"
        :use-weight="activeGuardVisualization.useWeight"
        :with-search="null"
        :search-enabled="false"
        :zoom-enabled="true"
        :graph-updater="graphUpdater"/>
    <FlamegraphComponent
        :with-timeseries="false"
        :with-search="null"
        :use-weight="activeGuardVisualization.useWeight"
        :use-guardian="activeGuardVisualization"
        :time-range="null"
        :export-enabled="false"
        scrollableWrapperClass="p-dialog-content"
        :flamegraph-tooltip="flamegraphTooltip"
        :graph-updater="graphUpdater"/>
  </Dialog>
</template>
