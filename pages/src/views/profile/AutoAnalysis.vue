<script setup>

import {onMounted, ref} from 'vue';
import RulesService from "../../service/RulesService";
import PrimaryProfileService from "../../service/PrimaryProfileService";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";

let rules = ref(null);

let tooltip, tooltipTimeoutId, autoAnalysisCard

onMounted(() => {
  RulesService.rules(PrimaryProfileService.id())
      .then((data) => {
        rules.value = data;
      });

  tooltip = document.getElementById('analysisTooltip');
  autoAnalysisCard = document.getElementById('autoAnalysisCard');
});

const items = [
  {label: 'Profile'},
  {label: 'Auto Analysis', route: '/profile/autoAnalysis'}
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

const mouse_over = (event, rule) => {
  let currentTarget = event.currentTarget

  tooltip.style.visibility = 'hidden';
  clearTimeout(tooltipTimeoutId)
  tooltipTimeoutId = setTimeout(() => {
    tooltip.innerHTML = generateTooltip(rule)
    tooltip.style.top = currentTarget.offsetTop + currentTarget.offsetHeight + 'px';

    if (event.clientX > ((autoAnalysisCard.offsetWidth + autoAnalysisCard.offsetLeft) / 2)) {
      tooltip.style.left = (currentTarget.offsetLeft + (currentTarget.offsetWidth - tooltip.offsetWidth)) + 'px';
    } else {
      tooltip.style.left = currentTarget.offsetLeft + 'px';
    }

    tooltip.style.visibility = 'visible';
  }, 500);
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
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card card-w-title" id="autoAnalysisCard">
    <div class="grid">
      <div class="col-12 md:col-6 lg:col-3" v-for="(rule, index) in rules" :key="index" @mouseover="mouse_over($event, rule)">
        <div class="surface-card shadow-2 p-3 border-round">
          <div class="flex justify-content-between">
            <div>
              <span class="block text-900 text-lg mt-2">{{ rule.rule }}</span>
            </div>
            <div class="flex align-items-center justify-content-center border-round"
                 :class="select_color(rule, 'bg', 100)"
                 style="width: 2.5rem; height: 2.5rem;">
              <span class="material-symbols-outlined text-3xl"
                    :class="select_color(rule, 'text', 700)">{{ select_icon(rule) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div class="card p-2 border-1 bg-gray-50 w-4" style="visibility:hidden; position:absolute" id="analysisTooltip"></div>
</template>

<style scoped lang="scss"></style>