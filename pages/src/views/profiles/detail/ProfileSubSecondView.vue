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
import {onBeforeMount, ref, watch} from 'vue';
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
import router from "@/router";
import GraphType from "@/services/flamegraphs/GraphType";
import SubSecondComponent from "@/components/SubSecondComponent.vue";
import {useRoute} from "vue-router";
import { useNavigation } from '@/composables/useNavigation';
import FlamegraphClient from "@/services/flamegraphs/client/FlamegraphClient";
import PrimaryFlamegraphClient from "@/services/flamegraphs/client/PrimaryFlamegraphClient";
import DifferentialFlamegraphClient from "@/services/flamegraphs/client/DifferentialFlamegraphClient";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/services/flamegraphs/tooltips/FlamegraphTooltipFactory";
import SubSecondDataProvider from "@/services/subsecond/SubSecondDataProvider";
import SubSecondDataProviderImpl from "@/services/subsecond/SubSecondDataProviderImpl";
import HeatmapTooltip from "@/services/subsecond/HeatmapTooltip";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import OnlyFlamegraphGraphUpdater from "@/services/flamegraphs/updater/OnlyFlamegraphGraphUpdater";
import TimeRange from "@/services/flamegraphs/model/TimeRange";
// Import Bootstrap modal functionality
import * as bootstrap from 'bootstrap';

const route = useRoute()
const { workspaceId, projectId } = useNavigation();

const queryParams = router.currentRoute.value.query

const showDialog = ref<boolean>(false);
let graphUpdater: GraphUpdater
let flamegraphTooltip: FlamegraphTooltip

let modalInstance: bootstrap.Modal | null = null;

// Watch for changes to showDialog and control the Bootstrap modal
watch(showDialog, (isVisible) => {
  // Initialize modal if not already done
  if (!modalInstance) {
    const modalEl = document.getElementById('flamegraphModal');
    if (modalEl) {
      modalInstance = new bootstrap.Modal(modalEl, {
        backdrop: 'static',
        keyboard: false
      });
    }
  }

  // Show or hide modal based on showDialog value
  if (isVisible && modalInstance) {
    modalInstance.show();
  } else if (!isVisible && modalInstance) {
    modalInstance.hide();
  }
});

let primarySubSecondDataProvider: SubSecondDataProvider
let secondarySubSecondDataProvider: SubSecondDataProvider | null = null

let useWeight = queryParams.useWeight === 'true'

onBeforeMount(() => {
  // Scroll the workspace-content container to top
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }

  // Add event listener for Bootstrap modal hidden event
  document.addEventListener('hidden.bs.modal', (event) => {
    if (event.target && event.target.id === 'flamegraphModal') {
      showDialog.value = false;
    }
  });
  primarySubSecondDataProvider = new SubSecondDataProviderImpl(
      workspaceId.value!,
      projectId.value!,
      route.params.profileId as string,
      queryParams.eventType as string,
      useWeight,
  )

  if (queryParams.graphMode === GraphType.DIFFERENTIAL) {
    secondarySubSecondDataProvider = new SubSecondDataProviderImpl(
        workspaceId.value!,
        SecondaryProfileService.projectId() as string,
        SecondaryProfileService.id() as string,
        queryParams.eventType as string,
        useWeight,
    )
  }

  let isPrimary = queryParams.graphMode === GraphType.PRIMARY
  let flamegraphClient: FlamegraphClient
  if (isPrimary) {
    flamegraphClient = new PrimaryFlamegraphClient(
        workspaceId.value!,
        projectId.value!,
        route.params.profileId as string,
        queryParams.eventType as string,
        false,
        useWeight,
        false,
        false,
        false,
        null
    )
  } else {
    flamegraphClient = new DifferentialFlamegraphClient(
        workspaceId.value!,
        projectId.value!,
        route.params.profileId as string,
        SecondaryProfileService.id() as string,
        queryParams.eventType as string,
        useWeight,
        false,
        false,
        false,
    )
  }

  graphUpdater = new OnlyFlamegraphGraphUpdater(flamegraphClient, false)
  flamegraphTooltip = FlamegraphTooltipFactory.create(queryParams.eventType as string, useWeight, !isPrimary)
})

function createOnSelectedCallback(profileId: string) {
  return function (startTime: number[], endTime: number[]) {
    let selectedTimeRange = Utils.toTimeRange(startTime, endTime, false);
    showFlamegraph(profileId, selectedTimeRange);
  };
}

function showFlamegraph(profileId: string, timeRange: TimeRange) {
  // Show the flamegraph dialog
  showDialog.value = true

  MessageBus.emit(MessageBus.SUBSECOND_SELECTION_CLEAR, {});

  setTimeout(() => {
    graphUpdater.updateWithZoom(timeRange)
  }, 200);
}
</script>

<template>
  <SubSecondComponent
      :primary-data-provider="primarySubSecondDataProvider"
      :primary-selected-callback="createOnSelectedCallback(route.params.profileId as string)"
      :secondary-data-provider="secondarySubSecondDataProvider"
      :secondary-selected-callback="createOnSelectedCallback(SecondaryProfileService.id() as string)"
      :tooltip="new HeatmapTooltip(queryParams.eventType, useWeight)"
  />

  <!-- Bootstrap Modal with v-model:visible binding (95% size) -->

  <div class="modal fade" id="flamegraphModal" tabindex="-1" aria-labelledby="flamegraphModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg" style="width: 95vw; max-width: 95%;">
      <div class="modal-content">
        <div class="modal-header">
          <button type="button" class="btn-close" @click="showDialog = false" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <FlamegraphComponent
              v-if="showDialog"
              :with-timeseries="false"
              :with-search="null"
              :use-weight="useWeight"
              :use-guardian="null"
              scrollable-wrapper-class="flamegraphModal"
              :flamegraph-tooltip="flamegraphTooltip"
              :graph-updater="graphUpdater"/>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.modal-body {
  padding-left: 5px;
  padding-right: 5px;
  overflow: hidden;
  overflow-y: auto;
}

.modal-body-content {
  overflow: auto;
}

/* Add a subtle animation to the modal */
.modal.fade .modal-dialog {
  transition: transform 0.3s ease-out;
  transform: translate(0, -50px);
}

.modal.show .modal-dialog {
  transform: none;
}

/* Custom header styling */
.modal-header {
  background-color: #f8f9fa;
  border-bottom: 1px solid #dee2e6;
}

.modal-title {
  font-weight: 600;
}

</style>
