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

import EventViewerService from "@/service/EventViewerService";
import {onMounted, ref} from "vue";
import FilterUtils from "@/service/FilterUtils";
import TimeseriesGraph from "../../service/timeseries/TimeseriesGraph";
import TimeseriesComponent from "../../components/TimeseriesComponent.vue";
import FlamegraphComponent from "../../components/FlamegraphComponent.vue";
import FormattingService from "@/service/FormattingService";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import GraphType from "@/service/flamegraphs/GraphType";
import GlobalVars from "@/service/GlobalVars";
import {useRoute} from "vue-router";
import PrimaryFlamegraphClient from "@/service/flamegraphs/client/PrimaryFlamegraphClient";
import FlamegraphClient from "@/service/flamegraphs/client/FlamegraphClient";
import FlamegraphTooltip from "@/service/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/service/flamegraphs/tooltips/FlamegraphTooltipFactory";
import GraphUpdater from "@/service/flamegraphs/updater/GraphUpdater";
import FullGraphUpdater from "@/service/flamegraphs/updater/FullGraphUpdater";

const route = useRoute()

const allEventTypes = ref(null);
const filters = ref({});
const timeseriesToggle = ref(false)

const filtersDialog = ref({});
const filterMode = ref({label: 'Lenient', value: 'lenient'});
const showDialog = ref(false);
const showFlamegraphDialog = ref(false);
const selectedEventCode = ref<string | null>(null)

let timeseries: TimeseriesGraph | null
let expandedKeys = ref({})
const events = ref(null)

const graphTypeValue = ref('Area');
const graphTypeOptions = ref(['Area', 'Bar']);

let eventViewerService: EventViewerService;
let originalEvents, columns
let currentEventCode: string

let graphUpdater: GraphUpdater
let flamegraphClient: FlamegraphClient
let flamegraphTooltip: FlamegraphTooltip

onMounted(() => {
  eventViewerService = new EventViewerService(route.params.projectId, route.params.profileId)
  eventViewerService.allEventTypes()
      .then((data: any) => {
        allEventTypes.value = data
        expandAll()
      })
});

const expandAll = () => {
  function markExpanded(eventTypes: any) {
    eventTypes.forEach((it: any) => {
      if (it.children.length !== 0) {
        markExpanded(it.children)
      }
      expandedKeys.value[it.key] = true;
    })
  }

  markExpanded(allEventTypes.value)
}

const collapseAll = () => {
  expandedKeys.value = {}
}

const showEvents = (eventCode: string) => {
  graphTypeValue.value = graphTypeOptions.value[0]
  currentEventCode = eventCode

  let eventsRequest = eventViewerService.events(eventCode);
  let columnsRequest = eventViewerService.eventColumns(eventCode);

  eventsRequest.then((eventsData) => {
    columnsRequest.then((columnsData) => {
      const filters = FilterUtils.createFilters(columnsData)
      events.value = eventsData
      originalEvents = eventsData
      columns = columnsData
      filtersDialog.value = filters
      showDialog.value = true
      timeseries = null
      timeseriesToggle.value = false
    })
  })
}

const showFlamegraph = (eventCode: string) => {
  flamegraphClient = PrimaryFlamegraphClient.onlyEventType(
      route.params.projectId as string,
      route.params.profileId as string,
      eventCode)

  graphUpdater = new FullGraphUpdater(flamegraphClient)

  flamegraphTooltip = FlamegraphTooltipFactory.create(eventCode, false, false)

  selectedEventCode.value = eventCode
  showFlamegraphDialog.value = true
}

const resetTimeseriesZoom = () => {
  timeseries?.resetZoom();
  events.value = originalEvents
};

const selectedInTimeseries = (min: number, max: number) => {
  const start = Math.floor(min);
  const end = Math.ceil(max);

  const newEvents = []
  events.value.forEach((json) => {
    const startTime = json.startTime
    if (startTime >= start && startTime <= end) {
      newEvents.push(json)
    }
  })

  events.value = newEvents
};

const toggleTimeseries = () => {
  let timeseriesDiv: HTMLElement = document.getElementById("timeseries")!

  if (timeseriesToggle.value) {
    flamegraphClient = PrimaryFlamegraphClient.onlyEventType(
        route.params.projectId as string,
        route.params.profileId as string,
        currentEventCode)

    flamegraphClient.provideTimeseries(null)
        .then((data) => {
          timeseriesDiv.style.display = '';
          timeseries = new TimeseriesGraph(currentEventCode, 'timeseries', selectedInTimeseries, false, false, true);
          timeseries.render(data);
        });
  } else {
    timeseries = null
    timeseriesDiv.innerHTML = "";
    timeseriesDiv.style.display = 'none';
    events.value = originalEvents
  }
}

const dataTypeMapping = (jfrType) => {
  // jdk.jfr.Percentage
  // jdk.jfr.Timespan
  // jdk.jfr.Timestamp
  // jdk.jfr.Frequency
  // jdk.jfr.BooleanFlag
  // jdk.jfr.MemoryAddress
  // jdk.jfr.DataAmount
  // jdk.jfr.Unsigned -> "byte", "short", "int", "long"
  // jdk.jfr.snippets.Temperature
  // => text, numeric, date

  if (
      jfrType === "jdk.jfr.Unsigned"
      || jfrType === "jdk.jfr.Timestamp"
      || jfrType === "jdk.jfr.DataAmount"
      || jfrType === "jdk.jfr.MemoryAddress"
      || jfrType === "jdk.jfr.Frequency"
      || jfrType === "jdk.jfr.Timespan"
      || jfrType === "jdk.jfr.Percentage") {

    return "numeric"
  } else {
    return "text"
  }
}

const formatFieldValue = (value, jfrType) => {
  return FormattingService.format(value, jfrType)
}

const modifyISODateToTimestamp = (filterModel, callback) => {
  // Value can be passed as a ISO DateTime or directly as a timestamp
  function resolveValue(value) {
    if (!isNaN(value)) {
      return value
    } else {
      return new Date(value.trim()).getTime()
    }
  }

  const newConstraints = []
  filterModel["constraints"].forEach((row) => {
    const newConstraint = {
      value: resolveValue(row.value),
      matchMode: row.matchMode
    }
    newConstraints.push(newConstraint)
  })

  filterModel["constraints"] = newConstraints
  callback()
}

const items = [
  {label: 'Event Viewer', route: 'eventViewer'}
]

const linkToJfrSAP = (eventCode: string) => {
  let code = eventCode.replace("jdk.", "").toLowerCase();
  window.open(GlobalVars.SAP_EVENT_LINK + "#" + code, '_blank')
}

const isJDKEvent = (eventCode: string) => {
  return eventCode.startsWith("jdk.")
}

const changeGraphType = () => {
  resetTimeseriesZoom()
  timeseries.changeGraphType(graphTypeValue.value);
}
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card">
    <Button @click="expandAll" label="Expand All" class="m-2"/>
    <Button @click="collapseAll" label="Collapse All" class="m-2"/>
    <TreeTable :value="allEventTypes" :filters="filters" :filterMode="filterMode.value"
               v-model:expandedKeys="expandedKeys">
      <Column field="name" header="Name" :expander="true" filter-match-mode="contains" style="padding: 10px">
        <template #filter>
          <InputText v-model="filters['name']" type="text" class="p-column-filter" placeholder="Filter by Name"/>
        </template>

        <template #body="slotProps" style="padding: 10px">
          <span class="font-bold" v-if="slotProps.node.data.code == null">{{ slotProps.node.data.name }}</span>
          <span class="text-primary" v-else>{{ slotProps.node.data.name }} - <span class="p-column-title text-red-400">{{
              slotProps.node.data.count
            }}</span></span>
        </template>
      </Column>
      <Column field="code" header="Code" filter-match-mode="contains" style="padding: 10px">
        <template #filter>
          <InputText v-model="filters['code']" type="text" class="p-column-filter" placeholder="Filter by Code"/>
        </template>

        <template class="bg-blue-300" #body="slotProps">
          <span class="text-primary" v-if="slotProps.node.data.code != null">{{ slotProps.node.data.code }}</span>
        </template>
      </Column>
      <Column headerStyle="width: 20rem" style="padding: 10px">
        <template #body="slotProps">
          <div class="flex flex-wrap gap-2 flex-row-reverse" v-if="slotProps.node.data.code != null">
            <Button type="button" severity="success" @click="showEvents(slotProps.node.data.code)"
                    :disabled="slotProps.node.data.count < 1">
              <div class="material-symbols-outlined text-xl">search</div>
            </Button>
            <Button type="button" v-if="isJDKEvent(slotProps.node.data.code)"
                    @click="linkToJfrSAP(slotProps.node.data.code)">
              <div class="material-symbols-outlined text-xl">link</div>
            </Button>
            <Button type="button" @click="showFlamegraph(slotProps.node.data.code)" severity="danger"
                    v-if="slotProps.node.data.withStackTrace" :disabled="slotProps.node.data.count < 1">
              <div class="material-symbols-outlined text-xl">local_fire_department</div>
            </Button>
          </div>
        </template>
      </Column>
    </TreeTable>
  </div>

  <!-- Dialog for events that contain StackTrace field -->
  <Dialog class="scrollable" header=" " :pt="{root: 'overflow-hidden'}" v-model:visible="showFlamegraphDialog" modal
          :style="{ width: '95%' }" style="overflow-y: auto">
    <TimeseriesComponent
        :graph-type="GraphType.PRIMARY"
        :event-type="selectedEventCode!"
        :use-weight="false"
        :with-search="null"
        :search-enabled="true"
        :zoom-enabled="true"
        :graph-updater="graphUpdater"/>
    <FlamegraphComponent
        :with-timeseries="true"
        :with-search="null"
        :use-weight="false"
        :use-guardian="null"
        :time-range="null"
        :save-enabled="false"
        scrollableWrapperClass="p-dialog-content"
        :flamegraph-tooltip="flamegraphTooltip"
        :graph-updater="graphUpdater"/>
  </Dialog>

  <!-- Dialog for events to list all records in a table -->

  <Dialog header=" " maximizable v-model:visible="showDialog" modal :style="{ width: '95%' }" style="overflow-y: auto">

    <div class="col-8 flex flex-row">
      <ToggleButton v-model="timeseriesToggle" @click="toggleTimeseries()" onLabel="Unload Timeseries"
                    offLabel="Load Timeseries" class="ml-2"/>

      <SelectButton v-if="timeseriesToggle" v-model="graphTypeValue" :options="graphTypeOptions"
                    @click="changeGraphType"
                    aria-labelledby="basic" class="ml-2 mr-2" :allowEmpty="false"/>

      <Button v-if="timeseriesToggle" icon="pi pi-home" class="p-button-filled p-button-info mt-1" title="Reset Zoom"
              @click="resetTimeseriesZoom()"/>
    </div>

    <div id="timeseries"></div>

    <DataTable v-model:filters="filtersDialog" :value="events" paginator :rows="50" tableStyle="min-width: 50rem"
               filterDisplay="menu">

      <Column sortable v-for="col of columns" :key="col.field" :field="col.field"
              :header="col.header" :dataType="dataTypeMapping(col.type)">

        <template #body="slotProps">
          {{ formatFieldValue(slotProps.data[col.field], col.type) }}
        </template>

        <template #filter="{ filterModel, filterCallback }" v-if="col.type !== 'jdk.jfr.Timestamp'">
          <InputText v-model="filterModel.value" @input="filterCallback()" type="text" class="p-column-filter"/>
        </template>

        <!-- Timestamp needs to be converted to ISO (for the sake of convenience) from Timestamp millis and back -->

        <template #filter="{ filterModel }" v-if="col.type === 'jdk.jfr.Timestamp'">
          <InputText v-model="filterModel.value" type="text" class="p-column-filter"/>
        </template>

        <!-- Timestamp has different buttons to do the conversion between ISO time to timestamp for filtering -->

        <template #filterclear="{ filterCallback }" v-if="col.type === 'jdk.jfr.Timestamp'">
          <Button type="button" class="p-button-sm" @click="filterCallback()" severity="primary" label="Clear"
                  outlined></Button>
        </template>

        <template #filterapply="{ filterModel, filterCallback }" v-if="col.type === 'jdk.jfr.Timestamp'">
          <Button type="button" class="p-button-sm" @click="modifyISODateToTimestamp(filterModel, filterCallback)"
                  label="Apply" severity="primary"></Button>
        </template>

        <template #filterfooter v-if="col.type === 'jdk.jfr.Timestamp'">
          <div class="px-2 pt-0 pb-2 text-center text-sm font-bold">Use ISO DateTime or Timestamp (ms)</div>
        </template>

        <template #filterfooter v-if="col.type === 'jdk.jfr.DataAmount'">
          <div class="px-2 pt-0 pb-2 text-center text-sm font-bold">Use a number in bytes</div>
        </template>

        <template #filterfooter v-if="col.type === 'jdk.jfr.Percentage'">
          <div class="px-2 pt-0 pb-2 text-center text-sm font-bold">Use 0-1 format</div>
        </template>
      </Column>
    </DataTable>
  </Dialog>

</template>

<style>
.p-treetable tr:hover {
  background: #f4fafe;
}

.p-button.p-button-icon-only {
  width: 2.5rem;
  height: 2.5rem;
  padding: 0.75rem 0;
}
</style>
