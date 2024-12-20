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
import SubSecondComponent from "@/components/SubSecondComponent.vue";
import {onMounted, ref} from "vue";
import MessageBus from "@/service/MessageBus";
import HeatmapTooltip from "@/service/subsecond/HeatmapTooltip";
import ReplacementResolver from "@/service/replace/ReplacementResolver";
import StaticSubSecondDataProvider from "@/service/subsecond/StaticSubSecondDataProvider";

const cliDialog = ref(false);
const cliValue = ref("")

let primarySubSecondDataProvider
let secondarySubSecondDataProvider

onMounted(() => {
  primarySubSecondDataProvider = new StaticSubSecondDataProvider(JSON.parse(ReplacementResolver.primarySubSecond()))

  if (ReplacementResolver.secondarySubSecond() != null) {
    secondarySubSecondDataProvider = new StaticSubSecondDataProvider(JSON.parse(ReplacementResolver.secondarySubSecond()))
  }
})

function createOnSelectedCallback(graphType) {
  return function (startTime, endTime) {
    cliValue.value = ReplacementResolver.resolveSubSecondCommand()
        .replaceAll("<start-time>", heatmapValueToMillis(startTime))
        .replaceAll("<end-time>", heatmapValueToMillis(endTime))
    cliDialog.value = true;
  };
}

function heatmapValueToMillis(time) {
  return (time[0] * 1000) + time[1]
}

const closeModal = () => {
  MessageBus.emit(MessageBus.SUBSECOND_SELECTION_CLEAR, {});
  cliValue.value = ""
}

const copyToClipboard = () => {
  navigator.clipboard.writeText(cliValue.value);
}
</script>

<template>
  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <SubSecondComponent
        :primary-data-provider="primarySubSecondDataProvider"
        :primary-selected-callback="createOnSelectedCallback('primary')"
        :secondary-data-provider="secondarySubSecondDataProvider"
        :secondary-selected-callback="createOnSelectedCallback('secondary')"
        :tooltip="new HeatmapTooltip(ReplacementResolver.eventType(), ReplacementResolver.useWeight())"
    />
  </div>

  <Dialog v-model:visible="cliDialog" modal header="Command to generate a Flamegraph"
          :closable="false" :style="{ width: '50rem', border: '0px' }">
    <div class="grid p-fluid">
      <div class="col-12">
        <Textarea v-model="cliValue" autoResize rows="5" cols="30"/>
      </div>
      <div class="col-6">
        <Button type="button" label="Copy and Close" severity="primary"
                @click="cliDialog = false; copyToClipboard(); closeModal();"/>
      </div>
      <div class="col-6">
        <Button type="button" label="Close" severity="secondary"
                @click="cliDialog = false; closeModal()"/>
      </div>
    </div>
  </Dialog>
</template>

<style scoped></style>
