<script setup>

import PrimaryProfileService from "@/service/PrimaryProfileService";
import EventViewerService from "@/service/EventViewerService";
import {onBeforeMount, ref} from "vue";

const allEventTypes = ref(null);
const filters = ref({});
const filterMode = ref({label: 'Lenient', value: 'lenient'});
let expandedKeys = ref({})

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

</script>

<template>

  <div class="card">
    <Button @click="expandAll" label="Expand All" class="m-2" />
    <Button @click="collapseAll" label="Collapse All" class="m-2" />
    <TreeTable :value="allEventTypes" :filters="filters" :filterMode="filterMode.value" v-model:expandedKeys="expandedKeys">
      <Column field="name" header="Name" :expander="true" filter-match-mode="contains">
        <template #filter>
          <InputText v-model="filters['name']" type="text" class="p-column-filter" placeholder="Filter by Name"/>
        </template>

        <template class="bg-blue-300" #body="slotProps">
            <span class="p-column-title font-bold"  v-if="slotProps.node.data.code == null">{{ slotProps.node.data.name }}</span>
            <span class="p-column-title text-primary" v-else>{{ slotProps.node.data.name }} - <span class="p-column-title text-red-400">{{ slotProps.node.data.count }}</span></span>
        </template>
      </Column>
      <Column field="code" header="Code" filter-match-mode="contains">
        <template #filter>
          <InputText v-model="filters['code']" type="text" class="p-column-filter" placeholder="Filter by Code"/>
        </template>

        <template class="bg-blue-300" #body="slotProps">
          <span class="p-column-title text-primary" v-if="slotProps.node.data.code != null">{{ slotProps.node.data.code }}</span>
        </template>
      </Column>
      <Column headerStyle="width: 10rem">
        <template  #body="slotProps">
          <div class="flex flex-wrap gap-2" v-if="slotProps.node.data.code != null">
            <Button type="button" icon="pi pi-search" rounded />
            <Button type="button" icon="pi pi-pencil" rounded severity="success" />
          </div>
        </template>
      </Column>
    </TreeTable>
  </div>
</template>
