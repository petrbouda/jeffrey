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

<script setup>

import {onMounted, ref} from 'vue';
import PrimaryProfileService from "../../service/PrimaryProfileService";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import GuardianService from "@/service/guardian/GuardianService";
import Utils from "@/service/Utils";
import GraphType from "@/service/flamegraphs/GraphType";
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";

let guards = ref(null);

let tooltip, tooltipTimeoutId, autoAnalysisCard

const showFlamegraphDialog = ref(false);
let activeGuardVisualization = null;

onMounted(() => {
  GuardianService.list(PrimaryProfileService.id())
      .then((data) => {
        guards.value = data;
      });

  tooltip = document.getElementById('analysisTooltip');
  autoAnalysisCard = document.getElementById('autoAnalysisCard');
});

const items = [
  {label: 'Profile'},
  {label: 'Guardian', route: '/profile/guardian'}
]

function mapSeverity(severity) {
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

const click_flamegraph = (guard) => {
  if (Utils.isNotNull(guard.visualization)) {
    showFlamegraphDialog.value = true
    activeGuardVisualization = guard.visualization
  }
}

const mouse_over = (event, rule) => {
  let currentTarget = event.currentTarget

  tooltip.style.visibility = 'hidden';
  clearTimeout(tooltipTimeoutId)
  tooltipTimeoutId = setTimeout(() => {
    generate_and_place_tooltip(event, currentTarget, rule)
  }, 500);
}

function generate_and_place_tooltip(event, currentTarget, rule) {
  tooltip.innerHTML = generateTooltip(rule)
  tooltip.style.top = currentTarget.offsetTop + currentTarget.offsetHeight + 'px';

  if (event.clientX > ((autoAnalysisCard.offsetWidth + autoAnalysisCard.offsetLeft) / 2)) {
    tooltip.style.left = (currentTarget.offsetLeft + (currentTarget.offsetWidth - tooltip.offsetWidth) - 20) + 'px';
  } else {
    tooltip.style.left = (currentTarget.offsetLeft + 20) + 'px';
  }

  tooltip.style.visibility = 'visible';
}

function divider(text) {
  return `<div class="m-2 ml-4 italic text-gray-500 text-sm">${text}</div>`
}

function generateTooltip(rule) {
  let severity = ""
  if (rule.severity != null) {
    severity =
        `${divider("Severity")}
    <table class="pl-1 pr-1 text-sm">
      <tr>
        <td>${mapSeverity(rule.severity)}<td>
      </tr>
    </table>`
  }

  let summary = ""
  if (rule.summary != null) {
    summary =
        `${divider("Summary")}
    <table class="pl-1 pr-1 text-sm">
      <tr>
        <td>${rule.summary}<td>
      </tr>
    </table>`
  }

  let explanation = ""
  if (rule.explanation != null) {
    explanation =
        `${divider("Explanation")}
    <table class="pl-1 pr-1 text-sm">
      <tr>
        <td>${rule.explanation}<td>
      </tr>
    </table>`
  }

  let solution = ""
  if (rule.solution != null) {
    solution =
        `${divider("Solution")}
    <table class="pl-1 pr-1 text-sm">
      <tr>
        <td>${rule.solution}<td>
      </tr>
    </table>`
  }

  return `${severity}${summary}${explanation}${solution}`
}

function select_icon(rule) {
  if (rule.severity === "OK") {
    return "check"
  } else if (rule.severity === "WARNING") {
    return "warning"
  } else if (rule.severity === "INFO") {
    return "info_i"
  } else if (rule.severity === "NA") {
    return "do_not_disturb_on"
  } else if (rule.severity === "IGNORE") {
    return "search_off"
  }
}

function select_color(rule, type, shade) {
  if (rule.severity === "OK") {
    return type + "-green-" + shade
  } else if (rule.severity === "WARNING") {
    return type + "-red-" + shade
  } else if (rule.severity === "INFO") {
    return type + "-blue-" + shade
  } else if (rule.severity === "NA" || rule.severity === "IGNORE") {
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

  <div class="card card-w-title">
    <div class="col-12" v-for="(guardsInCategory, category) in guards" :key="category">
      <Divider align="left" type="solid">
        <b>{{ category }}</b>
      </Divider>
      <div class="grid">
        <div class="col-12 md:col-6 lg:col-3" v-for="(guard, index) in guardsInCategory" :key="index"
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
    <TimeseriesComponent :primary-profile-id="activeGuardVisualization.primaryProfileId"
                         :graph-type="GraphType.PRIMARY"
                         :eventType="activeGuardVisualization.eventType"
                         :use-guardian="activeGuardVisualization"
                         :search-enabled="false"
                         :use-weight="false"/>
    <FlamegraphComponent :primary-profile-id="activeGuardVisualization.primaryProfileId"
                         :with-timeseries="false"
                         :eventType="activeGuardVisualization.eventType"
                         :use-guardian="activeGuardVisualization"
                         :use-weight="false"
                         :use-thread-mode="false"
                         scrollableWrapperClass="p-dialog-content"
                         :export-enabled="false"
                         :graph-type="GraphType.PRIMARY"
                         :generated="false"/>
  </Dialog>
</template>
