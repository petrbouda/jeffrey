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
import {useRoute} from 'vue-router';
import {onBeforeMount, ref} from "vue";
import FlamegraphRepositoryClient from "@/service/flamegraphs/client/FlamegraphRepositoryClient";
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import FlamegraphClient from "@/service/flamegraphs/client/FlamegraphClient";
import StaticFlamegraphClient from "@/service/flamegraphs/client/StaticFlamegraphClient";
import GraphType from "@/service/flamegraphs/GraphType";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import FullGraphUpdater from "@/service/flamegraphs/updater/FullGraphUpdater";
import FlamegraphTooltipFactory from "@/service/flamegraphs/tooltips/FlamegraphTooltipFactory";
import GraphUpdater from "@/service/flamegraphs/updater/GraphUpdater";
import SavedGraphMetadata from "@/service/flamegraphs/model/save/SavedGraphMetadata";
import FlamegraphTooltip from "@/service/flamegraphs/tooltips/FlamegraphTooltip";
import OnlyFlamegraphGraphUpdater from "@/service/flamegraphs/updater/OnlyFlamegraphGraphUpdater";

const route = useRoute();

let flamegraphTooltip: FlamegraphTooltip;
let flamegraphClient: FlamegraphClient;
const graphUpdater = ref<GraphUpdater>();

let graphMetadata = ref<SavedGraphMetadata>()
const ready = ref<boolean>(false)

onBeforeMount(() => {
  new FlamegraphRepositoryClient(route.params.projectId as string, route.params.profileId as string)
      .getById(route.query.flamegraphId as string)
      .then((data) => {
        graphMetadata.value = data.metadata

        flamegraphTooltip = FlamegraphTooltipFactory.create(
            data.metadata.eventType, data.metadata.useWeight, !data.metadata?.isPrimary)

        flamegraphClient = new StaticFlamegraphClient(data.content)

        if (data.metadata.withTimeseries) {
          graphUpdater.value = new FullGraphUpdater(flamegraphClient)
        } else {
          graphUpdater.value = new OnlyFlamegraphGraphUpdater(flamegraphClient, null)
        }

        // Now we are ready to display the flamegraph and timeseries graph
        ready.value = true
      });
});
</script>

<template>
  <div class="card card-w-title" style="padding: 20px 25px 25px;">
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
</template>
