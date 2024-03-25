<script setup>

import PrimaryProfileService from "@/service/PrimaryProfileService";
import EventViewerService from "@/service/EventViewerService";
import {onBeforeMount, ref} from "vue";

const allEventTypes = ref(null);
const filters = ref({});
const filtersDialog = ref({});
const filterMode = ref({label: 'Lenient', value: 'lenient'});
const showDialog = ref(false);
let expandedKeys = ref({})

let events, columns

onBeforeMount(() => {
  EventViewerService.allEventTypes(PrimaryProfileService.id())
      .then((data) => {
        allEventTypes.value = data
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
  let eventsRequest = EventViewerService.events(PrimaryProfileService.id(), eventCode);
  let columnsRequest = EventViewerService.eventColumns(PrimaryProfileService.id(), eventCode);

  eventsRequest.then((eventsData) => {
    columnsRequest.then((columnsData) => {
      events = eventsData
      columns = columnsData
      showDialog.value = true
    })
  })
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
            <Button type="button" icon="pi pi-search text-sm" @click="showEvents(slotProps.node.data.code)"/>
            <!--            <Button type="button" icon="pi pi-pencil text-sm" severity="success" />-->
          </div>
        </template>
      </Column>
    </TreeTable>
  </div>

  <Dialog header=" " maximizable v-model:visible="showDialog" modal :style="{ width: '95%' }" style="overflow-y: auto"
          :modal="true">
    <DataTable v-model:filters="filtersDialog" :filterMode="filterMode.value" :value="events" paginator :rows="50"
               tableStyle="min-width: 50rem" :globalFilterFields="['startTime']">
      <template #header>
        <div class="flex justify-content-end">
            <InputText v-model="filtersDialog['global']" placeholder="Keyword Search" />
        </div>
      </template>

      <Column sortable v-for="col of columns" :key="col.field" :field="col.field" :header="col.header">
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
