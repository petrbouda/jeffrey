<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
import {useRoute, useRouter} from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import {onBeforeMount, ref} from "vue";
import FlamegraphRepositoryClient from "@/services/flamegraphs/client/FlamegraphRepositoryClient";
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import FlamegraphClient from "@/services/flamegraphs/client/FlamegraphClient";
import GraphType from "@/services/flamegraphs/GraphType";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import FullGraphUpdater from "@/services/flamegraphs/updater/FullGraphUpdater";
import FlamegraphTooltipFactory from "@/services/flamegraphs/tooltips/FlamegraphTooltipFactory";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import SavedGraphMetadata from "@/services/flamegraphs/model/save/SavedGraphMetadata";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import OnlyFlamegraphGraphUpdater from "@/services/flamegraphs/updater/OnlyFlamegraphGraphUpdater";
import StaticFlamegraphClient from "@/services/flamegraphs/client/StaticFlamegraphClient.ts";

const route = useRoute();
const router = useRouter();
const { workspaceId, projectId } = useNavigation();

let flamegraphTooltip: FlamegraphTooltip;
let flamegraphClient: FlamegraphClient;
const graphUpdater = ref<GraphUpdater>();

let graphMetadata = ref<SavedGraphMetadata>()
const ready = ref<boolean>(false)

const profileId = route.params.profileId as string;

onBeforeMount(() => {
  new FlamegraphRepositoryClient(workspaceId.value!, projectId.value!, profileId)
      .getById(route.params.graphId as string)
      .then((data) => {
        graphMetadata.value = data.metadata

        flamegraphTooltip = FlamegraphTooltipFactory.create(
            data.metadata.eventType, data.metadata.useWeight, !data.metadata?.isPrimary)

        flamegraphClient = new StaticFlamegraphClient(data.content)

        if (data.metadata.withTimeseries) {
          graphUpdater.value = new FullGraphUpdater(flamegraphClient, true)
        } else {
          graphUpdater.value = new OnlyFlamegraphGraphUpdater(flamegraphClient, true)
        }

        // Now we are ready to display the flamegraph and timeseries graph
        ready.value = true
      });
});

const goBack = () => {
  router.push({
    name: 'profile-flamegraphs-saved'
  });
};
</script>

<template>
  <div class="container-fluid p-0">
    <!-- Header Section -->
    <div class="d-flex align-items-center mb-4">
      <button @click="goBack" class="btn btn-sm btn-outline-secondary me-3">
        <i class="bi bi-arrow-left"></i> Back to Saved Flamegraphs
      </button>
      <h2 class="flamegraph-title mb-0" v-if="graphMetadata">
        {{ graphMetadata.name }}
      </h2>
    </div>
    
    <div v-if="graphMetadata" class="card mb-3">
      <div class="card-body py-2">
        <div class="d-flex align-items-center flex-wrap gap-2">
          <span class="badge bg-light text-dark">{{ graphMetadata.eventType }}</span>
          <span v-if="graphMetadata.isPrimary" class="badge bg-success">Primary</span>
          <span v-else class="badge bg-info">Differential</span>
          <span v-if="graphMetadata.withTimeseries" class="badge bg-secondary">With Timeseries</span>
          <span v-if="graphMetadata.useWeight" class="badge bg-warning">Weight-based</span>
        </div>
      </div>
    </div>
    
    <!-- Loading state -->
    <div v-if="!ready" class="text-center py-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <!-- Flamegraph display -->
    <div style="padding: 0 5px 5px;">
      <TimeseriesComponent v-if="ready && graphMetadata?.withTimeseries"
                           :graph-type="graphMetadata.isPrimary ? GraphType.PRIMARY : GraphType.DIFFERENTIAL"
                           :event-type="graphMetadata.eventType"
                           :use-weight="graphMetadata.useWeight"
                           :with-search="null"
                           :search-enabled="false"
                           :zoom-enabled="false"
                           :graph-updater="graphUpdater!!"/>
      <FlamegraphComponent v-if="ready"
                           :with-timeseries="graphMetadata?.withTimeseries ?? false"
                           :with-search="null"
                           :use-weight="graphMetadata?.useWeight ?? false"
                           :use-guardian="null"
                           :save-enabled="false"
                           :scrollable-wrapper-class="null"
                           :flamegraph-tooltip="flamegraphTooltip"
                           :graph-updater="graphUpdater!!"/>
    </div>
  </div>
</template>

<style scoped>
.flamegraph-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: #343a40;
  display: flex;
  align-items: center;
}
</style>
