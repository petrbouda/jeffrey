<script setup>

import PrimaryProfileService from "@/service/PrimaryProfileService";
import EventViewerService from "@/service/EventViewerService";
import {onBeforeMount, ref} from "vue";
import FilterUtils from "@/service/FilterUtils";
import Utils from "../../service/Utils";
import TimeseriesGraph from "../../service/TimeseriesGraph";
import Flamegraph from "../../service/Flamegraph";
import TimeseriesComponent from "../../components/TimeseriesComponent.vue";
import FlamegraphComponent from "../../components/FlamegraphComponent.vue";
import FormattingService from "@/service/FormattingService";

const allEventTypes = ref(null);
const filters = ref({});
const timeseriesToggle = ref(false)

const filtersDialog = ref({});
const filterMode = ref({label: 'Lenient', value: 'lenient'});
const showDialog = ref(false);
const showFlamegraphDialog = ref(false);
const selectedEventCode = ref(null)

let timeseries = null
let expandedKeys = ref({})
const events = ref(null)


let originalEvents, columns, currentEventCode

onBeforeMount(() => {
  EventViewerService.allEventTypes(PrimaryProfileService.id())
      .then((data) => {
        allEventTypes.value = data
        expandAll()
      })
});

const expandAll = () => {
  function markExpanded(eventTypes) {
    eventTypes.forEach((it) => {
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

const showEvents = (eventCode) => {
  currentEventCode = eventCode

  let eventsRequest = EventViewerService.events(PrimaryProfileService.id(), eventCode);
  let columnsRequest = EventViewerService.eventColumns(PrimaryProfileService.id(), eventCode);

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

const showFlamegraph = (eventCode) => {
  selectedEventCode.value = eventCode
  showFlamegraphDialog.value = true
}

const resetTimeseriesZoom = () => {
  timeseries.resetZoom();
  events.value = originalEvents
};

const selectedInTimeseries = (chartContext, {xaxis, yaxis}) => {
  const start = Math.floor(xaxis.min);
  const end = Math.ceil(xaxis.max);

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
  if (timeseriesToggle.value) {
    EventViewerService.timeseries(PrimaryProfileService.id(), currentEventCode)
        .then((data) => {
          // if (timeseries == null) {
          document.getElementById("timeseries").style.display = '';
          timeseries = new TimeseriesGraph('timeseries', data, selectedInTimeseries, false);
          timeseries.render();
          // } else {
          //   timeseries.update(data, true);
          // }
          // searchPreloader.style.display = 'none';
        });
  } else {
    timeseries = null
    document.getElementById("timeseries").innerHTML = "";
    document.getElementById("timeseries").style.display = 'none';
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
  if (jfrType === "jdk.jfr.MemoryAddress") {
    return "0x" + parseInt(value).toString(16).toUpperCase()
  } else if (jfrType === "jdk.jfr.DataAmount") {
    return FormattingService.formatBytes(parseInt(value), 2)
  } else if (jfrType === "jdk.jfr.Percentage") {
    return FormattingService.formatPercentage(parseFloat(value));
  } else if (jfrType === "jdk.jfr.Timestamp") {
    return new Date(value).toISOString()
  } else {
    return value
  }
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
</script>

<template>

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
      <Column headerStyle="width: 10rem" style="padding: 10px">
        <template #body="slotProps">
          <div class="flex flex-wrap gap-2 flex-row-reverse" v-if="slotProps.node.data.code != null">
            <Button type="button" @click="showEvents(slotProps.node.data.code)" :disabled="slotProps.node.data.count < 1">
              <div class="material-symbols-outlined text-xl">search</div>
            </Button>
            <Button type="button" @click="showFlamegraph(slotProps.node.data.code)"
                    v-if="slotProps.node.data.withStackTrace" :disabled="slotProps.node.data.count < 1">
              <div class="material-symbols-outlined text-xl">local_fire_department</div>
            </Button>
          </div>
        </template>
      </Column>
    </TreeTable>
  </div>

  <!-- Dialog for events that contain StackTrace field -->

  <Dialog class="scrollable" header=" " maximizable v-model:visible="showFlamegraphDialog" modal :style="{ width: '95%' }" style="overflow-y: auto"
          :modal="true">
    <TimeseriesComponent :primary-profile-id="PrimaryProfileService.id()"
                         :graph-mode="Flamegraph.PRIMARY"
                         :eventType="selectedEventCode"/>
    <FlamegraphComponent :primary-profile-id="PrimaryProfileService.id()"
                         :graph-mode="Flamegraph.PRIMARY"
                         :eventType="selectedEventCode"
                         scrollableWrapperClass="p-dialog-content"/>
  </Dialog>

  <!-- Dialog for events to list all records in a table -->

  <Dialog header=" " maximizable v-model:visible="showDialog" modal :style="{ width: '95%' }" style="overflow-y: auto"
          :modal="true">

    <div class="col-6">
      <ToggleButton v-model="timeseriesToggle" @click="toggleTimeseries()" onLabel="Unload Timeseries"
                    offLabel="Load Timeseries" class="m-2"/>

      <Button v-if="timeseriesToggle" icon="pi pi-home" class="p-button-filled p-button-info m-2" title="Reset Zoom"
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
